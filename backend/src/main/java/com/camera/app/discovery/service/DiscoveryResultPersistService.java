package com.camera.app.discovery.service;

import com.camera.app.asset.repository.AssetRepository;
import com.camera.app.discovery.engine.DeviceTypeClassifier;
import com.camera.app.discovery.engine.ScanHostResult;
import com.camera.app.discovery.engine.SniffHostResult;
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
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiscoveryResultPersistService {

    private final DiscoveryResultRepository resultRepository;
    private final AssetRepository assetRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public int persistScanResults(Long taskId, List<ScanHostResult> scanResults) {
        int count = 0;
        LocalDateTime now = LocalDateTime.now();
        for (ScanHostResult raw : scanResults) {
            try {
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

                String evidence = buildScanEvidence(raw, openPorts, protocols);
                dr.setRawEvidence(evidence);

                if (dr.getFirstSeenAt() == null) dr.setFirstSeenAt(now);
                dr.setLastSeenAt(now);

                // Link to existing asset if IP matches
                linkToAsset(dr);

                resultRepository.save(dr);
                count++;
            } catch (Exception e) {
                log.warn("Failed to persist scan result for ip={}: {}", raw.ip(), e.getMessage());
            }
        }
        return count;
    }

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
                dr.setMac(raw.mac());
                dr.setHostname(raw.hostname());
                dr.setVendorHint(raw.vendorHint());
                dr.setProtocols(raw.protocols() != null ? String.join(",", raw.protocols()) : null);
                dr.setRawEvidence(raw.rawEvidence());

                // Classify based on protocols seen
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
                "vendorHint", raw.vendorHint() != null ? raw.vendorHint() : ""
        );
        return toJson(evidence);
    }

    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); }
        catch (Exception e) { return obj.toString(); }
    }
}
