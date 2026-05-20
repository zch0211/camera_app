package com.camera.app.collection.plugin;

import com.camera.app.asset.entity.*;
import com.camera.app.asset.repository.*;
import com.camera.app.asset.util.TechnicalProfileConverter;
import com.camera.app.collection.entity.AssetCollectionResult;
import com.camera.app.collection.entity.ProbeType;
import com.camera.app.collection.repository.AssetCollectionResultRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ProbeWritebackService {

    private final AssetCollectionResultRepository resultRepository;
    private final AssetTechnicalProfileRepository technicalProfileRepository;
    private final AssetServiceFingerprintRepository fingerprintRepository;
    private final AssetEvidenceRepository evidenceRepository;
    private final ObjectMapper objectMapper;

    /**
     * Persist all probe results to:
     * 1. asset_collection_results (raw log)
     * 2. asset_service_fingerprints (per-port upsert)
     * 3. asset_technical_profiles (device-level: openPorts, protocols, ONVIF device info)
     * 4. asset_evidences (per meaningful result)
     */
    public void processResults(Long assetId, Long taskId, List<ProbeResult> results) {
        if (results.isEmpty()) return;

        saveRawResults(results);
        upsertServiceFingerprints(assetId, taskId, results);
        updateDeviceProfile(assetId, results);
        createEvidences(assetId, taskId, results);
    }

    // ── 1. Raw results ──────────────────────────────────────────────────────

    private void saveRawResults(List<ProbeResult> results) {
        List<AssetCollectionResult> batch = results.stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
        resultRepository.saveAll(batch);
    }

    private AssetCollectionResult toEntity(ProbeResult r) {
        var e = new AssetCollectionResult();
        e.setTaskId(r.getTaskId());
        e.setAssetId(r.getAssetId());
        e.setProbeType(r.getProbeType());
        e.setPluginName(r.getPluginName());
        e.setConfidence(r.getConfidence() != null ? r.getConfidence() : BigDecimal.ZERO);
        e.setSuccess(r.isSuccess());
        e.setTargetHost(r.getTargetHost());
        e.setTargetPort(r.getTargetPort());
        e.setProtocolHint(r.getApplicationProtocol());
        e.setRawData(truncate(r.getRawData(), 8192));
        e.setParsedData(r.getParsedData());
        e.setErrorMessage(r.getErrorMessage());
        e.setCollectedAt(r.getCollectedAt() != null ? r.getCollectedAt() : LocalDateTime.now());
        return e;
    }

    // ── 2. Service fingerprints ─────────────────────────────────────────────

    private void upsertServiceFingerprints(Long assetId, Long taskId, List<ProbeResult> results) {
        LocalDateTime ts = LocalDateTime.now();

        for (ProbeResult r : results) {
            if (r.getTargetPort() == null) continue;
            // Skip UDP-level probes that don't map to a TCP port fingerprint
            // except SNMP (we store under port 161 / UDP) and UPnP (port 1900/UDP)
            if (!r.isSuccess() && r.getProbeType() == ProbeType.PORT_SCAN) {
                // Still upsert as CLOSED so we have a record
                upsertFingerprint(assetId, r.getTargetPort(), taskId, r, ts, "CLOSED");
                continue;
            }
            if (!r.isSuccess()) continue;

            String status = r.isPortOpen() ? "OPEN" : (r.isSuccess() ? "OPEN" : "UNKNOWN");
            upsertFingerprint(assetId, r.getTargetPort(), taskId, r, ts, status);
        }
    }

    private void upsertFingerprint(Long assetId, int port, Long taskId,
                                    ProbeResult r, LocalDateTime ts, String status) {
        AssetServiceFingerprint fp = fingerprintRepository.findByAssetIdAndPort(assetId, port)
                .orElseGet(() -> {
                    var f = new AssetServiceFingerprint();
                    f.setAssetId(assetId);
                    f.setPort(port);
                    return f;
                });

        fp.setStatus(status);
        fp.setLastCollectedAt(ts);
        fp.setLastTaskId(taskId);

        // Transport protocol
        if ("SNMP".equals(r.getApplicationProtocol()) || "UPnP".equals(r.getApplicationProtocol())) {
            fp.setTransportProtocol("UDP");
        }

        // Application protocol — only upgrade (HTTP → RTSP is fine, UNKNOWN → HTTP is fine)
        if (r.getApplicationProtocol() != null && !r.getApplicationProtocol().equals("TCP")) {
            fp.setApplicationProtocol(r.getApplicationProtocol());
        }

        if (r.getApplicationProtocol() != null) {
            String scheme = r.getApplicationProtocol().toLowerCase();
            if ("http".equals(scheme) || "https".equals(scheme)) fp.setScheme(scheme);
        }

        if (r.getServiceBanner() != null && !r.getServiceBanner().isBlank()) fp.setServiceBanner(r.getServiceBanner());
        if (r.getWebTitle() != null && !r.getWebTitle().isBlank()) fp.setWebTitle(r.getWebTitle());
        if (r.getServerHeader() != null && !r.getServerHeader().isBlank()) fp.setServerHeader(r.getServerHeader());
        if (r.getVendorHint() != null && !r.getVendorHint().isBlank()) fp.setVendorHint(r.getVendorHint());

        // Build raw summary
        StringBuilder sb = new StringBuilder("plugin=").append(r.getPluginName()).append(" status=").append(status);
        if (r.getApplicationProtocol() != null) sb.append(" protocol=").append(r.getApplicationProtocol());
        if (r.getWebTitle() != null) sb.append(" title=").append(r.getWebTitle());
        if (r.getVendorHint() != null) sb.append(" vendor=").append(r.getVendorHint());
        fp.setRawSummary(truncate(sb.toString(), 512));

        fingerprintRepository.save(fp);
    }

    // ── 3. Device-level profile ─────────────────────────────────────────────

    private void updateDeviceProfile(Long assetId, List<ProbeResult> results) {
        AssetTechnicalProfile profile = technicalProfileRepository.findByAssetId(assetId)
                .orElseGet(() -> { var p = new AssetTechnicalProfile(); p.setAssetId(assetId); return p; });

        // openPorts: merge from PORT_SCAN results
        List<Integer> openPorts = new ArrayList<>(TechnicalProfileConverter.parsePorts(profile.getOpenPorts()));
        results.stream()
                .filter(r -> r.getProbeType() == ProbeType.PORT_SCAN && r.isPortOpen() && r.getTargetPort() != null)
                .map(ProbeResult::getTargetPort)
                .filter(p -> !openPorts.contains(p))
                .forEach(openPorts::add);
        Collections.sort(openPorts);
        if (!openPorts.isEmpty()) profile.setOpenPorts(TechnicalProfileConverter.portsToJson(openPorts));

        // protocols: merge from successful probes
        List<String> protocols = new ArrayList<>(TechnicalProfileConverter.parseProtocols(profile.getProtocols()));
        results.stream()
                .filter(r -> r.isSuccess() && r.getApplicationProtocol() != null
                        && !r.getApplicationProtocol().equals("TCP")
                        && !r.getApplicationProtocol().equals("UNKNOWN"))
                .map(ProbeResult::getApplicationProtocol)
                .filter(p -> !protocols.contains(p))
                .forEach(protocols::add);
        if (!protocols.isEmpty()) profile.setProtocols(TechnicalProfileConverter.protocolsToJson(protocols));

        // ONVIF device-level fields — these ARE device-level, not port-level
        results.stream()
                .filter(r -> r.getProbeType() == ProbeType.ONVIF_PROBE && r.isSuccess())
                .findFirst()
                .ifPresent(onvif -> {
                    if (onvif.getFirmwareVersion() != null && profile.getFirmwareVersion() == null)
                        profile.setFirmwareVersion(onvif.getFirmwareVersion());
                    if (onvif.getSerialNumber() != null && profile.getSerialNumber() == null)
                        profile.setSerialNumber(onvif.getSerialNumber());
                    if (onvif.getMacAddress() != null && profile.getMacAddress() == null)
                        profile.setMacAddress(onvif.getMacAddress());
                });

        profile.setLastFingerprintAt(LocalDateTime.now());
        technicalProfileRepository.save(profile);
    }

    // ── 4. Evidences ────────────────────────────────────────────────────────

    private void createEvidences(Long assetId, Long taskId, List<ProbeResult> results) {
        List<AssetEvidence> batch = new ArrayList<>();
        LocalDateTime ts = LocalDateTime.now();

        // Aggregate open ports evidence (device-level)
        List<Integer> openPorts = results.stream()
                .filter(r -> r.getProbeType() == ProbeType.PORT_SCAN && r.isPortOpen())
                .map(ProbeResult::getTargetPort).filter(Objects::nonNull)
                .sorted().collect(Collectors.toList());
        if (!openPorts.isEmpty()) {
            batch.add(evidence(assetId, "openPorts", openPorts.toString(),
                    EvidenceSourceType.SCAN, "端口探测发现开放端口: " + openPorts,
                    new BigDecimal("0.900"), ts, null, taskId));
        }

        // Protocol evidence (device-level)
        List<String> protos = results.stream()
                .filter(r -> r.isSuccess() && r.getApplicationProtocol() != null
                        && !r.getApplicationProtocol().equals("TCP")
                        && !r.getApplicationProtocol().equals("UNKNOWN"))
                .map(ProbeResult::getApplicationProtocol).distinct().collect(Collectors.toList());
        if (!protos.isEmpty()) {
            batch.add(evidence(assetId, "protocols", protos.toString(),
                    EvidenceSourceType.SCAN, "探测到应用层协议: " + protos,
                    new BigDecimal("0.900"), ts, null, taskId));
        }

        // Per-port evidences for successful probes with useful data
        for (ProbeResult r : results) {
            if (!r.isSuccess()) continue;
            Integer port = r.getTargetPort();

            if (r.getWebTitle() != null && !r.getWebTitle().isBlank()) {
                batch.add(evidence(assetId, "webTitle", r.getWebTitle(),
                        EvidenceSourceType.SCAN,
                        String.format("端口 %d [%s] 页面标题: %s", port, r.getPluginName(), r.getWebTitle()),
                        new BigDecimal("0.950"), ts, port, taskId));
            }

            if (r.getVendorHint() != null && !r.getVendorHint().isBlank() && !r.getVendorHint().equals(r.getWebTitle())) {
                batch.add(evidence(assetId, "vendorHint", r.getVendorHint(),
                        EvidenceSourceType.SCAN,
                        String.format("端口 %d [%s] 厂商线索: %s", port, r.getPluginName(), r.getVendorHint()),
                        new BigDecimal("0.800"), ts, port, taskId));
            }

            if (r.getServiceBanner() != null && !r.getServiceBanner().isBlank()) {
                batch.add(evidence(assetId, "serviceBanner", truncate(r.getServiceBanner(), 512),
                        EvidenceSourceType.SCAN,
                        String.format("端口 %d [%s] Banner: %s", port, r.getPluginName(), truncate(r.getServiceBanner(), 128)),
                        new BigDecimal("0.850"), ts, port, taskId));
            }

            // ONVIF device-level fields go as evidence with no port
            if (r.getProbeType() == ProbeType.ONVIF_PROBE) {
                if (r.getManufacturer() != null) {
                    batch.add(evidence(assetId, "brand", r.getManufacturer(),
                            EvidenceSourceType.SCAN, "ONVIF GetDeviceInformation: Manufacturer=" + r.getManufacturer(),
                            new BigDecimal("0.980"), ts, port, taskId));
                }
                if (r.getModel() != null) {
                    batch.add(evidence(assetId, "model", r.getModel(),
                            EvidenceSourceType.SCAN, "ONVIF GetDeviceInformation: Model=" + r.getModel(),
                            new BigDecimal("0.980"), ts, port, taskId));
                }
                if (r.getFirmwareVersion() != null) {
                    batch.add(evidence(assetId, "firmwareVersion", r.getFirmwareVersion(),
                            EvidenceSourceType.SCAN, "ONVIF GetDeviceInformation: FirmwareVersion=" + r.getFirmwareVersion(),
                            new BigDecimal("0.980"), ts, port, taskId));
                }
                if (r.getSerialNumber() != null) {
                    batch.add(evidence(assetId, "serialNumber", r.getSerialNumber(),
                            EvidenceSourceType.SCAN, "ONVIF GetDeviceInformation: SerialNumber=" + r.getSerialNumber(),
                            new BigDecimal("0.980"), ts, port, taskId));
                }
            }

            // SNMP fields
            if (r.getProbeType() == ProbeType.SNMP_PROBE) {
                if (r.getServiceBanner() != null) { // sysDescr stored here
                    batch.add(evidence(assetId, "snmpSysDescr", truncate(r.getServiceBanner(), 512),
                            EvidenceSourceType.SCAN, "SNMP sysDescr: " + truncate(r.getServiceBanner(), 128),
                            new BigDecimal("0.900"), ts, port, taskId));
                }
            }
        }

        evidenceRepository.saveAll(batch);
    }

    private AssetEvidence evidence(Long assetId, String fieldName, String fieldValue,
                                    EvidenceSourceType sourceType, String rawEvidence,
                                    BigDecimal confidence, LocalDateTime collectedAt,
                                    Integer relatedPort, Long relatedTaskId) {
        var e = new AssetEvidence();
        e.setAssetId(assetId);
        e.setFieldName(fieldName);
        e.setFieldValue(truncate(fieldValue, 512));
        e.setSourceType(sourceType);
        e.setRawEvidence(rawEvidence);
        e.setConfidence(confidence);
        e.setCollectedAt(collectedAt);
        e.setRelatedPort(relatedPort);
        e.setRelatedTaskId(relatedTaskId);
        return e;
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
