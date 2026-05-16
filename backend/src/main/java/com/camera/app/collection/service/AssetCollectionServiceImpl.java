package com.camera.app.collection.service;

import com.camera.app.asset.entity.AssetEvidence;
import com.camera.app.asset.entity.AssetServiceFingerprint;
import com.camera.app.asset.entity.AssetTechnicalProfile;
import com.camera.app.asset.entity.EvidenceSourceType;
import com.camera.app.asset.repository.AssetEvidenceRepository;
import com.camera.app.asset.repository.AssetRepository;
import com.camera.app.asset.repository.AssetServiceFingerprintRepository;
import com.camera.app.asset.repository.AssetTechnicalProfileRepository;
import com.camera.app.asset.util.TechnicalProfileConverter;
import com.camera.app.collection.dto.CollectionResultResponse;
import com.camera.app.collection.dto.CollectionTaskCreateRequest;
import com.camera.app.collection.dto.CollectionTaskResponse;
import com.camera.app.collection.entity.*;
import com.camera.app.collection.probe.LightweightProbeService;
import com.camera.app.collection.probe.LightweightProbeService.PortResult;
import com.camera.app.collection.probe.LightweightProbeService.ProbeSession;
import com.camera.app.collection.repository.AssetCollectionResultRepository;
import com.camera.app.collection.repository.AssetCollectionTaskRepository;
import com.camera.app.common.exception.BusinessException;
import com.camera.app.common.response.PageResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AssetCollectionServiceImpl implements AssetCollectionService {

    private final AssetRepository assetRepository;
    private final AssetCollectionTaskRepository taskRepository;
    private final AssetCollectionResultRepository resultRepository;
    private final AssetTechnicalProfileRepository technicalProfileRepository;
    private final AssetEvidenceRepository evidenceRepository;
    private final AssetServiceFingerprintRepository fingerprintRepository;
    private final LightweightProbeService probeService;
    private final ObjectMapper objectMapper;

    private static final List<Integer> DEFAULT_PORTS = List.of(80, 443, 554, 8000, 8080, 8443);
    private static final int DEFAULT_TIMEOUT_MS = 2000;

    @Override
    public CollectionTaskResponse createAndExecuteTask(Long assetId, CollectionTaskCreateRequest req) {
        var asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new BusinessException(404, "资产不存在，id=" + assetId));

        // 创建任务记录
        var task = new AssetCollectionTask();
        task.setAssetId(assetId);
        task.setTaskType(req.getTaskType() != null ? req.getTaskType() : CollectionTaskType.LIGHTWEIGHT_PROBE);
        task.setStatus(CollectionTaskStatus.RUNNING);
        task.setTriggerType(TriggerType.MANUAL);
        task.setStartedAt(LocalDateTime.now());
        task = taskRepository.save(task);

        List<Integer> ports = (req.getPorts() != null && !req.getPorts().isEmpty())
                ? req.getPorts() : DEFAULT_PORTS;
        int timeoutMs = req.getTimeoutMillis() != null ? req.getTimeoutMillis() : DEFAULT_TIMEOUT_MS;

        try {
            ProbeSession session = probeService.probe(
                    asset.getIp(), ports,
                    req.isEnableHttpProbe(), req.isEnableHttpsProbe(),
                    timeoutMs);

            saveRawResults(task.getId(), assetId, session);
            writeBackServiceFingerprints(assetId, task.getId(), session);
            writeBackToProfile(assetId, session);
            createEvidenceRecords(assetId, task.getId(), session);

            int openCount    = session.openPorts().size();
            long httpTried   = session.httpResults().stream().filter(r -> "HTTP".equals(r.protocol())).count();
            long httpOk      = session.httpResults().stream().filter(r -> "HTTP".equals(r.protocol()) && r.success()).count();
            long httpsTried  = session.httpResults().stream().filter(r -> "HTTPS".equals(r.protocol())).count();
            long httpsOk     = session.httpResults().stream().filter(r -> "HTTPS".equals(r.protocol()) && r.success()).count();

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("探测完成。开放端口 %d 个: %s。", openCount, session.openPorts()));
            if (httpTried  > 0) sb.append(String.format("HTTP 尝试 %d 个，成功 %d 个。",  httpTried,  httpOk));
            if (httpsTried > 0) sb.append(String.format("HTTPS 尝试 %d 个，成功 %d 个。", httpsTried, httpsOk));
            if (session.bestWebTitle() != null) sb.append("页面标题: ").append(session.bestWebTitle()).append("。");
            String summary = sb.toString().trim();

            task.setStatus(CollectionTaskStatus.SUCCESS);
            task.setFinishedAt(LocalDateTime.now());
            task.setSuccess(true);
            task.setSummary(summary);
            task.setWritebackApplied(true);

        } catch (Exception e) {
            log.error("采集任务执行失败 assetId={}", assetId, e);
            task.setStatus(CollectionTaskStatus.FAILED);
            task.setFinishedAt(LocalDateTime.now());
            task.setSuccess(false);
            task.setErrorMessage(e.getMessage());
        }

        task = taskRepository.save(task);
        return new CollectionTaskResponse(task, resultRepository.countByTaskId(task.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<CollectionTaskResponse> listTasks(Long assetId, int page, int size) {
        ensureAssetExists(assetId);
        var pageResult = taskRepository.findByAssetIdOrderByCreatedAtDesc(
                assetId, PageRequest.of(page, size));
        return new PageResult<>(pageResult.map(t ->
                new CollectionTaskResponse(t, resultRepository.countByTaskId(t.getId()))));
    }

    @Override
    @Transactional(readOnly = true)
    public CollectionTaskResponse getTask(Long assetId, Long taskId) {
        var task = taskRepository.findByIdAndAssetId(taskId, assetId)
                .orElseThrow(() -> new BusinessException(404, "采集任务不存在，id=" + taskId));
        return new CollectionTaskResponse(task, resultRepository.countByTaskId(taskId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CollectionResultResponse> getTaskResults(Long assetId, Long taskId) {
        taskRepository.findByIdAndAssetId(taskId, assetId)
                .orElseThrow(() -> new BusinessException(404, "采集任务不存在，id=" + taskId));
        return resultRepository.findByTaskIdOrderByCollectedAtAsc(taskId)
                .stream().map(CollectionResultResponse::new).toList();
    }

    // ---- raw result persistence ----

    private void saveRawResults(Long taskId, Long assetId, ProbeSession session) {
        LocalDateTime ts = session.probedAt();
        List<AssetCollectionResult> batch = new ArrayList<>();

        for (PortResult pr : session.portResults()) {
            var r = new AssetCollectionResult();
            r.setTaskId(taskId);
            r.setAssetId(assetId);
            r.setProbeType(ProbeType.PORT_SCAN);
            r.setSuccess(pr.open());
            r.setTargetHost(session.host());
            r.setTargetPort(pr.port());
            r.setProtocolHint(guessProtocol(pr.port()));
            r.setRawData(pr.banner() != null ? "Banner: " + pr.banner()
                    : (pr.open() ? "Port open" : "Port closed/unreachable"));
            r.setParsedData(toJson(buildPortParsed(pr)));
            r.setCollectedAt(ts);
            batch.add(r);
        }

        for (LightweightProbeService.HttpResult hr : session.httpResults()) {
            var r = new AssetCollectionResult();
            r.setTaskId(taskId);
            r.setAssetId(assetId);
            r.setProbeType(ProbeType.HTTP_TITLE);
            r.setSuccess(hr.success());
            r.setTargetHost(session.host());
            r.setTargetPort(hr.port());
            r.setProtocolHint(hr.protocol());
            if (hr.success()) {
                r.setRawData("Title: " + hr.title() + "  Server: " + hr.serverHeader());
                r.setParsedData(toJson(buildHttpParsed(hr)));
            } else {
                r.setErrorMessage(hr.error());
            }
            r.setCollectedAt(ts);
            batch.add(r);
        }

        resultRepository.saveAll(batch);
    }

    // ---- service fingerprint upsert ----

    private void writeBackServiceFingerprints(Long assetId, Long taskId, ProbeSession session) {
        LocalDateTime ts = session.probedAt();

        for (PortResult pr : session.portResults()) {
            AssetServiceFingerprint fp = fingerprintRepository.findByAssetIdAndPort(assetId, pr.port())
                    .orElseGet(() -> {
                        var f = new AssetServiceFingerprint();
                        f.setAssetId(assetId);
                        f.setPort(pr.port());
                        return f;
                    });

            fp.setStatus(pr.open() ? "OPEN" : "CLOSED");
            fp.setLastCollectedAt(ts);
            fp.setLastTaskId(taskId);

            if (pr.banner() != null && !pr.banner().isBlank()) {
                fp.setServiceBanner(pr.banner());
            }

            // 找该端口成功的 HTTP 或 HTTPS 探测结果（HTTP 优先，HTTPS 次之）
            LightweightProbeService.HttpResult httpResult = session.httpResults().stream()
                    .filter(r -> r.port() == pr.port() && r.success() && "HTTP".equals(r.protocol()))
                    .findFirst()
                    .orElse(null);
            if (httpResult == null) {
                httpResult = session.httpResults().stream()
                        .filter(r -> r.port() == pr.port() && r.success() && "HTTPS".equals(r.protocol()))
                        .findFirst()
                        .orElse(null);
            }

            if (httpResult != null) {
                fp.setApplicationProtocol(httpResult.protocol());
                fp.setScheme(httpResult.protocol().toLowerCase());
                if (httpResult.title() != null && !httpResult.title().isBlank()) {
                    fp.setWebTitle(httpResult.title());
                }
                if (httpResult.serverHeader() != null && !httpResult.serverHeader().isBlank()) {
                    fp.setServerHeader(httpResult.serverHeader());
                    fp.setVendorHint(httpResult.serverHeader());
                }
                fp.setRawSummary(buildFingerprintSummary(pr.port(), httpResult));
            } else if (pr.open()) {
                // 端口开放但无 HTTP 响应
                fp.setApplicationProtocol("UNKNOWN");
                fp.setRawSummary("port=" + pr.port() + " open, no HTTP/HTTPS response");
            }

            fingerprintRepository.save(fp);
        }
    }

    private String buildFingerprintSummary(int port, LightweightProbeService.HttpResult r) {
        StringBuilder sb = new StringBuilder("port=").append(port)
                .append(" protocol=").append(r.protocol());
        if (r.title() != null) sb.append(" title=").append(r.title());
        if (r.serverHeader() != null) sb.append(" server=").append(r.serverHeader());
        return sb.toString();
    }

    // ---- write-back to device-level profile ----
    // 仅写回真正属于设备级的聚合字段：openPorts / protocols / lastFingerprintAt
    // webTitle / vendorHint / serviceBanner 属于端口/服务级信息，由 service fingerprints 表负责，
    // 不再由采集任务自动覆盖设备级摘要，避免把某一端口的服务特征误标为设备厂商线索。

    private void writeBackToProfile(Long assetId, ProbeSession session) {
        AssetTechnicalProfile profile = technicalProfileRepository.findByAssetId(assetId)
                .orElseGet(() -> { var p = new AssetTechnicalProfile(); p.setAssetId(assetId); return p; });

        // openPorts：合并去重排序
        List<Integer> merged = new ArrayList<>(TechnicalProfileConverter.parsePorts(profile.getOpenPorts()));
        for (int port : session.openPorts()) {
            if (!merged.contains(port)) merged.add(port);
        }
        Collections.sort(merged);
        profile.setOpenPorts(TechnicalProfileConverter.portsToJson(merged));

        // protocols：合并去重
        List<String> mergedProto = new ArrayList<>(
                TechnicalProfileConverter.parseProtocols(profile.getProtocols()));
        for (String p : session.detectedProtocols()) {
            if (!mergedProto.contains(p)) mergedProto.add(p);
        }
        profile.setProtocols(TechnicalProfileConverter.protocolsToJson(mergedProto));

        profile.setLastFingerprintAt(session.probedAt());
        technicalProfileRepository.save(profile);
    }

    // ---- evidence generation ----

    private void createEvidenceRecords(Long assetId, Long taskId, ProbeSession session) {
        LocalDateTime ts = session.probedAt();
        List<AssetEvidence> batch = new ArrayList<>();

        // 汇总所有开放端口的证据（设备级，不带端口）
        if (!session.openPorts().isEmpty()) {
            batch.add(buildEvidence(assetId, "openPorts",
                    session.openPorts().toString(),
                    EvidenceSourceType.SCAN,
                    "端口探测: " + session.portResults().stream()
                            .filter(PortResult::open)
                            .map(pr -> pr.port() + " open")
                            .collect(Collectors.joining(", ")),
                    new BigDecimal("0.90"), ts, null, taskId));
        }

        if (!session.detectedProtocols().isEmpty()) {
            batch.add(buildEvidence(assetId, "protocols",
                    session.detectedProtocols().toString(),
                    EvidenceSourceType.SCAN,
                    "HTTP/HTTPS 探测发现协议: " + session.detectedProtocols(),
                    new BigDecimal("0.90"), ts, null, taskId));
        }

        // 每个成功探测的 HTTP/HTTPS 端口各自生成一条证据，携带 relatedPort
        for (LightweightProbeService.HttpResult hr : session.httpResults()) {
            if (!hr.success()) continue;

            if (hr.title() != null && !hr.title().isBlank()) {
                batch.add(buildEvidence(assetId, "webTitle", hr.title(),
                        EvidenceSourceType.SCAN,
                        String.format("端口 %d %s 页面标题: %s", hr.port(), hr.protocol(), hr.title()),
                        new BigDecimal("0.95"), ts, hr.port(), taskId));
            }

            if (hr.serverHeader() != null && !hr.serverHeader().isBlank()) {
                batch.add(buildEvidence(assetId, "vendorHint", hr.serverHeader(),
                        EvidenceSourceType.SCAN,
                        String.format("端口 %d %s Server 头: %s", hr.port(), hr.protocol(), hr.serverHeader()),
                        new BigDecimal("0.80"), ts, hr.port(), taskId));
            }
        }

        evidenceRepository.saveAll(batch);
    }

    // ---- helpers ----

    private AssetEvidence buildEvidence(Long assetId, String fieldName, String fieldValue,
                                        EvidenceSourceType sourceType, String rawEvidence,
                                        BigDecimal confidence, LocalDateTime collectedAt,
                                        Integer relatedPort, Long relatedTaskId) {
        var e = new AssetEvidence();
        e.setAssetId(assetId);
        e.setFieldName(fieldName);
        e.setFieldValue(fieldValue);
        e.setSourceType(sourceType);
        e.setRawEvidence(rawEvidence);
        e.setConfidence(confidence);
        e.setCollectedAt(collectedAt);
        e.setRelatedPort(relatedPort);
        e.setRelatedTaskId(relatedTaskId);
        return e;
    }

    private String guessProtocol(int port) {
        return switch (port) {
            case 80, 8000, 8080 -> "HTTP";
            case 443, 8443 -> "HTTPS";
            case 554 -> "RTSP";
            default -> "TCP";
        };
    }

    private LinkedHashMap<String, Object> buildPortParsed(PortResult pr) {
        var m = new LinkedHashMap<String, Object>();
        m.put("port", pr.port());
        m.put("open", pr.open());
        if (pr.banner() != null) m.put("banner", pr.banner());
        return m;
    }

    private LinkedHashMap<String, Object> buildHttpParsed(LightweightProbeService.HttpResult hr) {
        var m = new LinkedHashMap<String, Object>();
        m.put("port", hr.port());
        m.put("protocol", hr.protocol());
        m.put("title", hr.title());
        m.put("server", hr.serverHeader());
        return m;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    private void ensureAssetExists(Long assetId) {
        if (!assetRepository.existsById(assetId)) {
            throw new BusinessException(404, "资产不存在，id=" + assetId);
        }
    }
}
