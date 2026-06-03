package com.camera.app.alert.service;

import com.camera.app.alert.entity.*;
import com.camera.app.alert.repository.AlertEvidenceRepository;
import com.camera.app.alert.repository.AlertOperationRepository;
import com.camera.app.alert.repository.AlertRepository;
import com.camera.app.asset.entity.Asset;
import com.camera.app.asset.repository.AssetRepository;
import com.camera.app.collection.plugin.ProbeResult;
import com.camera.app.collection.entity.ProbeType;
import com.camera.app.discovery.entity.DiscoveryLevel;
import com.camera.app.discovery.entity.DiscoveryResult;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Unified entry point for automatic alert generation.
 * Handles fingerprint-based deduplication: same fingerprint + active status
 * → aggregate (triggerCount++, lastTriggeredAt); otherwise → create new alert.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertGenerationService {

    private static final List<AlertStatus> ACTIVE = List.of(AlertStatus.NEW, AlertStatus.CONFIRMED);
    private static final Set<String> CAMERA_DEVICE_TYPES =
            Set.of("CAMERA", "IPC", "IPC_CAMERA", "NVR", "DVR");
    private static final List<Integer> MANAGEMENT_PORTS = List.of(80, 8080, 8000, 8443, 443);
    private static final List<Integer> CAMERA_STREAM_PORTS = List.of(554, 8554, 1935);
    private static final BigDecimal MIN_INFERENCE_CONFIDENCE = new BigDecimal("0.65");

    private final AlertRepository            alertRepository;
    private final AlertEvidenceRepository    alertEvidenceRepository;
    private final AlertOperationRepository   alertOperationRepository;
    private final AssetRepository            assetRepository;
    private final AlertRiskScoreService      riskScoreService;

    // ═══════════════════════════════════════════════════════════════
    // Public API — rule methods called by business services
    // ═══════════════════════════════════════════════════════════════

    /**
     * Rule A: POC execution hit.
     * Called by PocExecutionServiceImpl after a successful execution.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void generateFromPocExecution(Long pocId, Long assetId,
                                          String actionKey, boolean success, Long executionId) {
        if (!success || actionKey == null) return;

        AlertSeverity severity;
        String ruleCode;
        String title;

        switch (actionKey) {
            case "CHECK_VULN"      -> { severity = AlertSeverity.HIGH;     ruleCode = "POC_CHECK_HIT";
                                        title = "POC 验证成功：设备存在漏洞风险"; }
            case "FETCH_SNAPSHOT"  -> { severity = AlertSeverity.HIGH;     ruleCode = "POC_FETCH_SNAPSHOT";
                                        title = "POC 成功获取摄像头快照"; }
            case "EXEC_COMMAND"    -> { severity = AlertSeverity.CRITICAL; ruleCode = "POC_EXEC_COMMAND";
                                        title = "POC 成功执行命令"; }
            // DUMP_CREDS — dedicated action for extracting credentials (was previously missing!)
            case "DUMP_CREDS"      -> { severity = AlertSeverity.CRITICAL; ruleCode = "POC_DUMP_CREDS";
                                        title = "POC 成功读取管理员凭证"; }
            // LIST_USERS — enumerating user accounts is also credential-adjacent
            case "LIST_USERS"      -> { severity = AlertSeverity.CRITICAL; ruleCode = "POC_DUMP_CREDS";
                                        title = "POC 成功读取管理员凭证"; }
            case "DOWNLOAD_CONFIG" -> { severity = AlertSeverity.CRITICAL; ruleCode = "POC_DOWNLOAD_CONFIG";
                                        title = "POC 成功下载设备配置"; }
            default -> { return; } // unknown action — not a known high-risk hit, skip
        }

        String summary = String.format("POC (id=%d) 执行动作 %s 成功，执行记录 id=%d", pocId, actionKey, executionId);
        generateOrUpdate(Input.builder()
                .sourceType(AlertSourceType.POC)
                .severity(severity)
                .title(title)
                .ruleCode(ruleCode)
                .ruleName("POC 自动规则")
                .assetId(assetId)
                .summary(summary)
                .evidenceType(AlertEvidenceType.POC_EXECUTION)
                .evidenceRefId(executionId)
                .evidenceDescription(String.format("POC id=%d，动作=%s，执行记录 id=%d", pocId, actionKey, executionId))
                .build());
    }

    /**
     * Rule B: Collection task — camera management interface exposed.
     * Called by CollectionTaskExecutor after task completes.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void generateFromCollectionTask(Long assetId, Long taskId, List<ProbeResult> results) {
        if (results == null || results.isEmpty()) return;

        boolean rtspOk  = results.stream().anyMatch(r -> r.getProbeType() == ProbeType.RTSP_PROBE  && r.isSuccess());
        boolean onvifOk = results.stream().anyMatch(r -> r.getProbeType() == ProbeType.ONVIF_PROBE && r.isSuccess());
        List<Integer> openPorts = results.stream()
                .filter(r -> r.getProbeType() == ProbeType.PORT_SCAN && r.isPortOpen() && r.getTargetPort() != null)
                .map(ProbeResult::getTargetPort)
                .toList();

        boolean hasMgmtPort    = openPorts.stream().anyMatch(MANAGEMENT_PORTS::contains);
        boolean hasStreamPort  = openPorts.stream().anyMatch(CAMERA_STREAM_PORTS::contains);

        // Trigger: (RTSP or ONVIF reachable) AND (management or stream port open)
        if ((rtspOk || onvifOk) && (hasMgmtPort || hasStreamPort)) {
            String detail = String.format(
                    "RTSP可达=%b ONVIF可达=%b 开放端口=%s", rtspOk, onvifOk, openPorts);
            generateOrUpdate(Input.builder()
                    .sourceType(AlertSourceType.COLLECTION)
                    .severity(AlertSeverity.MEDIUM)
                    .title("视频监控设备暴露管理/访问接口")
                    .ruleCode("COLLECTION_CAMERA_MANAGEMENT_EXPOSED")
                    .ruleName("采集：视频设备管理面暴露")
                    .assetId(assetId)
                    .summary("采集任务发现摄像头/NVR 暴露 RTSP/ONVIF 及管理端口，存在被未授权访问的风险")
                    .evidenceType(AlertEvidenceType.COLLECTION_RESULT)
                    .evidenceRefId(taskId)
                    .evidenceDescription(detail)
                    .build());
        }
    }

    /**
     * Rule C: Unmanaged device discovered.
     * Called by DiscoveryResultPersistService after persisting a result.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void generateFromDiscoveryResult(Long taskId, DiscoveryResult dr) {
        if (dr.isManaged()) return;                                    // already imported, skip
        if (dr.getDiscoveryLevel() != DiscoveryLevel.CANDIDATE) return; // only CANDIDATE warrants alert

        // Use IP as the unique key since no assetId exists
        String fingerprint = buildDiscoveryFingerprint(dr.getIp(), "DISCOVERY_UNMANAGED_DEVICE_FOUND");

        // Check dedup against alerts that used IP snapshot (assetId = null)
        Optional<Alert> existing = alertRepository.findFirstByFingerprintAndStatusIn(fingerprint, ACTIVE);
        String summary = String.format(
                "IP=%s 发现未纳管设备，设备类型=%s，开放端口=%s，可信度=%.2f",
                dr.getIp(),
                dr.getDeviceTypeCandidate() != null ? dr.getDeviceTypeCandidate() : "未知",
                dr.getOpenPorts() != null ? dr.getOpenPorts() : "[]",
                dr.getConfidence() != null ? dr.getConfidence() : BigDecimal.ZERO);

        if (existing.isPresent()) {
            aggregate(existing.get(), null, AlertEvidenceType.DISCOVERY_RESULT, dr.getId(),
                    "重复发现同 IP 未纳管设备：" + dr.getIp());
        } else {
            Alert alert = buildAlert(null, AlertSourceType.DISCOVERY, AlertSeverity.LOW,
                    "发现未纳管设备：" + dr.getIp(),
                    "DISCOVERY_UNMANAGED_DEVICE_FOUND", "发现：未纳管设备",
                    summary, fingerprint,
                    null, dr.getIp(), null);  // no assetId, use IP snapshot
            alertRepository.save(alert);
            writeOperation(alert.getId(), AlertOperationType.CREATE, "system", null);
            appendEvidence(alert.getId(), AlertEvidenceType.DISCOVERY_RESULT, dr.getId(),
                    "发现未纳管设备，任务 id=" + taskId + "，" + summary);
            // No riskScore recalc since no linked asset
        }
        log.debug("[AlertGen] DISCOVERY alert for ip={} managed={}", dr.getIp(), dr.isManaged());
    }

    /**
     * Rule D: Inference — high-confidence camera device with exposure.
     * Called by AssetProfileServiceImpl when an inference candidate is saved.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void generateFromInference(Long assetId, Long candidateId,
                                       String fieldName, String fieldValue, BigDecimal confidence) {
        if (!"deviceCategory".equals(fieldName)) return;
        if (fieldValue == null || !CAMERA_DEVICE_TYPES.contains(fieldValue.toUpperCase())) return;
        if (confidence == null || confidence.compareTo(MIN_INFERENCE_CONFIDENCE) < 0) return;

        generateOrUpdate(Input.builder()
                .sourceType(AlertSourceType.INFERENCE)
                .severity(AlertSeverity.MEDIUM)
                .title("推断发现视频监控设备高暴露风险")
                .ruleCode("INFERENCE_CAMERA_HIGH_EXPOSURE")
                .ruleName("推断：高置信度摄像头高暴露")
                .assetId(assetId)
                .summary(String.format("高置信度（%.2f）推断设备类型为 %s，可能存在视频流或管理面未授权访问风险",
                        confidence, fieldValue))
                .evidenceType(AlertEvidenceType.INFERENCE_CANDIDATE)
                .evidenceRefId(candidateId)
                .evidenceDescription(String.format("推断候选 id=%d，fieldName=%s，fieldValue=%s，confidence=%.2f",
                        candidateId, fieldName, fieldValue, confidence))
                .build());
    }

    // ═══════════════════════════════════════════════════════════════
    // Internal — generate-or-update with fingerprint dedup
    // ═══════════════════════════════════════════════════════════════

    private void generateOrUpdate(Input in) {
        String fingerprint = buildFingerprint(in.getSourceType(), in.getAssetId(), in.getRuleCode());

        Optional<Alert> existing = alertRepository.findFirstByFingerprintAndStatusIn(fingerprint, ACTIVE);
        if (existing.isPresent()) {
            aggregate(existing.get(), in.getSeverity(),
                    in.getEvidenceType(), in.getEvidenceRefId(), in.getEvidenceDescription());
        } else {
            Asset asset = in.getAssetId() != null
                    ? assetRepository.findById(in.getAssetId()).orElse(null) : null;
            Alert alert = buildAlert(
                    in.getAssetId(), in.getSourceType(), in.getSeverity(),
                    in.getTitle(), in.getRuleCode(), in.getRuleName(),
                    in.getSummary(), fingerprint,
                    asset != null ? asset.getName() : null,
                    asset != null ? asset.getIp()   : null,
                    asset != null && asset.getType() != null ? asset.getType().name() : null);
            alertRepository.save(alert);
            writeOperation(alert.getId(), AlertOperationType.CREATE, "system", null);
            appendEvidence(alert.getId(), in.getEvidenceType(),
                    in.getEvidenceRefId(), in.getEvidenceDescription());
            riskScoreService.recalculate(in.getAssetId());
            log.info("[AlertGen] Created alert id={} ruleCode={} assetId={}",
                    alert.getId(), in.getRuleCode(), in.getAssetId());
        }
    }

    private void aggregate(Alert alert, AlertSeverity newSeverity,
                           AlertEvidenceType evType, Long evRefId, String evDesc) {
        alert.setLastTriggeredAt(LocalDateTime.now());
        alert.setTriggerCount(alert.getTriggerCount() + 1);
        if (newSeverity != null && newSeverity.weight() > alert.getSeverity().weight()) {
            alert.setSeverity(newSeverity);
        }
        alertRepository.save(alert);
        writeOperation(alert.getId(), AlertOperationType.RETRIGGER, "system",
                "自动重复触发，已聚合更新 triggerCount=" + alert.getTriggerCount());
        appendEvidence(alert.getId(), evType, evRefId, evDesc);
        riskScoreService.recalculate(alert.getAssetId());
        log.debug("[AlertGen] Aggregated alert id={} triggerCount={}", alert.getId(), alert.getTriggerCount());
    }

    // ═══════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════

    private Alert buildAlert(Long assetId, AlertSourceType sourceType, AlertSeverity severity,
                              String title, String ruleCode, String ruleName,
                              String summary, String fingerprint,
                              String assetNameSnapshot, String assetIpSnapshot, String assetTypeSnapshot) {
        Alert a = new Alert();
        a.setAssetId(assetId);
        a.setAssetNameSnapshot(assetNameSnapshot);
        a.setAssetIpSnapshot(assetIpSnapshot);
        a.setAssetTypeSnapshot(assetTypeSnapshot);
        a.setTitle(title);
        a.setSourceType(sourceType);
        a.setSeverity(severity);
        a.setStatus(AlertStatus.NEW);
        a.setSummary(summary);
        a.setRuleCode(ruleCode);
        a.setRuleName(ruleName);
        a.setFingerprint(fingerprint);
        LocalDateTime now = LocalDateTime.now();
        a.setFirstTriggeredAt(now);
        a.setLastTriggeredAt(now);
        a.setTriggerCount(1);
        return a;
    }

    private void writeOperation(Long alertId, AlertOperationType type, String operator, String comment) {
        AlertOperation op = new AlertOperation();
        op.setAlertId(alertId);
        op.setOperationType(type);
        op.setOperatorUsername(operator);
        op.setComment(comment);
        alertOperationRepository.save(op);
    }

    private void appendEvidence(Long alertId, AlertEvidenceType type, Long refId, String description) {
        if (type == null) return;
        AlertEvidence e = new AlertEvidence();
        e.setAlertId(alertId);
        e.setEvidenceType(type);
        e.setRefId(refId);
        e.setDescription(description);
        alertEvidenceRepository.save(e);
    }

    /** Fingerprint for asset-linked alerts: sourceType|assetId|ruleCode */
    private static String buildFingerprint(AlertSourceType src, Long assetId, String ruleCode) {
        return src.name() + "|" + (assetId != null ? assetId : "null") + "|" + ruleCode;
    }

    /** Fingerprint for unmanaged discovery alerts: DISCOVERY|ip|ruleCode */
    private static String buildDiscoveryFingerprint(String ip, String ruleCode) {
        return AlertSourceType.DISCOVERY.name() + "|" + ip + "|" + ruleCode;
    }

    // ── Internal builder ─────────────────────────────────────────────────────
    @Getter
    @Builder
    private static class Input {
        AlertSourceType    sourceType;
        AlertSeverity      severity;
        String             title;
        String             ruleCode;
        String             ruleName;
        Long               assetId;
        String             summary;
        AlertEvidenceType  evidenceType;
        Long               evidenceRefId;
        String             evidenceDescription;
    }
}
