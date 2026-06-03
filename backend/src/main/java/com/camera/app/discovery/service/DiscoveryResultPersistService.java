package com.camera.app.discovery.service;

import com.camera.app.alert.service.AlertGenerationService;
import com.camera.app.asset.repository.AssetRepository;
import com.camera.app.discovery.engine.DeviceTypeClassifier;
import com.camera.app.discovery.engine.ScanHostResult;
import com.camera.app.discovery.engine.SniffHostResult;
import com.camera.app.discovery.entity.DiscoveryLevel;
import com.camera.app.discovery.entity.DiscoveryResult;
import com.camera.app.discovery.entity.DiscoverySourceType;
import com.camera.app.discovery.repository.DiscoveryResultRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Persists raw scan/sniff results as DiscoveryResult rows and
 * links them to existing assets if an IP match is found.
 *
 * Each public method runs in its own @Transactional so the executor can
 * persist results one-by-one as they arrive (edge-result pattern).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiscoveryResultPersistService {

    private final DiscoveryResultRepository resultRepository;
    private final AssetRepository assetRepository;
    private final ObjectMapper objectMapper;
    private final AlertGenerationService alertGenerationService;

    /**
     * Persist a single scan result immediately — called per alive host as it
     * is discovered, so the user sees results in real time.
     *
     * Returns the assigned DiscoveryLevel so the executor can track
     * aliveCount vs candidateCount separately.
     */
    @Transactional
    public DiscoveryLevel persistOneScanResult(Long taskId, ScanHostResult raw) {
        LocalDateTime now = LocalDateTime.now();
        DiscoveryResult dr = resultRepository.findByTaskIdAndIp(taskId, raw.ip())
                .orElse(new DiscoveryResult());
        dr.setTaskId(taskId);
        dr.setIp(raw.ip());
        dr.setSourceType(DiscoverySourceType.SCAN);

        List<Integer> openPorts = raw.openPorts();
        dr.setOpenPorts(toJson(openPorts));
        List<String> protocols = DeviceTypeClassifier.inferProtocols(openPorts);
        dr.setProtocols(String.join(",", protocols));
        dr.setVendorHint(raw.vendorHint());

        DeviceTypeClassifier.ClassificationResult cls =
                DeviceTypeClassifier.classify(openPorts, raw.vendorHint(), raw.banner());
        dr.setDeviceTypeCandidate(cls.deviceType());
        dr.setConfidence(cls.confidence());
        dr.setRawEvidence(buildScanEvidence(raw, openPorts, protocols));

        // CANDIDATE: any open port or vendor hint detected — worth investigating further
        // ALIVE: host responded (ICMP) but no recognisable service found
        DiscoveryLevel level = (!openPorts.isEmpty() || isNotBlank(raw.vendorHint()))
                ? DiscoveryLevel.CANDIDATE : DiscoveryLevel.ALIVE;
        dr.setDiscoveryLevel(level);

        if (dr.getFirstSeenAt() == null) dr.setFirstSeenAt(now);
        dr.setLastSeenAt(now);

        linkToAsset(dr);
        resultRepository.save(dr);
        log.debug("发现结果已写入 ip={} ports={} type={} level={}", raw.ip(), openPorts, cls.deviceType(), level);

        // Auto-generate alert for unmanaged candidate devices (non-blocking)
        try {
            alertGenerationService.generateFromDiscoveryResult(taskId, dr);
        } catch (Exception e) {
            log.warn("[AlertGen] Discovery alert generation failed ip={}: {}", raw.ip(), e.getMessage());
        }
        return level;
    }

    /** Bulk persist for passive sniff — all sniff results are CANDIDATE (service advertisements). */
    @Transactional
    public int persistSniffResults(Long taskId, List<SniffHostResult> sniffResults) {
        int count = 0;
        LocalDateTime now = LocalDateTime.now();
        for (SniffHostResult raw : sniffResults) {
            try {
                DiscoveryResult dr = resultRepository.findByTaskIdAndIp(taskId, raw.ip())
                        .orElse(new DiscoveryResult());
                dr.setTaskId(taskId);
                dr.setIp(raw.ip());
                dr.setSourceType(DiscoverySourceType.SNIFF);
                dr.setDiscoveryLevel(DiscoveryLevel.CANDIDATE); // sniff always means a service was found
                dr.setMac(raw.mac());
                dr.setHostname(raw.hostname());
                dr.setVendorHint(raw.vendorHint());
                dr.setProtocols(raw.protocols() != null ? String.join(",", raw.protocols()) : null);
                dr.setRawEvidence(raw.rawEvidence());

                DeviceTypeClassifier.ClassificationResult cls =
                        DeviceTypeClassifier.classify(List.of(), raw.vendorHint(), raw.rawEvidence());
                dr.setDeviceTypeCandidate(cls.deviceType());
                dr.setConfidence(cls.confidence());

                if (dr.getFirstSeenAt() == null) dr.setFirstSeenAt(now);
                dr.setLastSeenAt(now);
                linkToAsset(dr);
                resultRepository.save(dr);
                count++;
            } catch (Exception e) {
                log.warn("Failed to persist sniff result for ip={}: {}", raw.ip(), e.getMessage());
            }
        }
        return count;
    }

    private void linkToAsset(DiscoveryResult dr) {
        if (dr.getLinkedAssetId() != null) return;
        assetRepository.findAll().stream()
                .filter(a -> a.getIp().equals(dr.getIp()))
                .findFirst()
                .ifPresent(asset -> {
                    dr.setLinkedAssetId(asset.getId());
                    dr.setManaged(true);
                });
    }

    private String buildScanEvidence(ScanHostResult raw, List<Integer> openPorts, List<String> protocols) {
        Map<String, Object> evidence = Map.of(
                "openPorts", openPorts,
                "protocols", protocols,
                "banner", raw.banner() != null ? raw.banner() : "",
                "vendorHint", raw.vendorHint() != null ? raw.vendorHint() : "",
                "icmpAlive", raw.icmpAlive()
        );
        return toJson(evidence);
    }

    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); }
        catch (Exception e) { return obj.toString(); }
    }

    private static boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }
}
