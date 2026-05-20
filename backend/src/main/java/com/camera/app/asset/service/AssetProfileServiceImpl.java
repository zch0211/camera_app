package com.camera.app.asset.service;

import com.camera.app.asset.dto.*;
import com.camera.app.asset.entity.AssetEvidence;
import com.camera.app.asset.entity.AssetInferenceCandidate;
import com.camera.app.asset.entity.AssetServiceFingerprint;
import com.camera.app.asset.entity.AssetTechnicalProfile;
import com.camera.app.asset.repository.AssetEvidenceRepository;
import com.camera.app.asset.repository.AssetInferenceCandidateRepository;
import com.camera.app.asset.repository.AssetRepository;
import com.camera.app.asset.repository.AssetServiceFingerprintRepository;
import com.camera.app.asset.repository.AssetTechnicalProfileRepository;
import com.camera.app.asset.util.TechnicalProfileConverter;
import com.camera.app.collection.dto.DeviceTypeDetectionResponse;
import com.camera.app.collection.entity.AssetCollectionResult;
import com.camera.app.collection.entity.ProbeType;
import com.camera.app.collection.repository.AssetCollectionResultRepository;
import com.camera.app.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class AssetProfileServiceImpl implements AssetProfileService {

    private static final List<Map.Entry<String, ProbeType>> PROTOCOL_PROBE_ENTRIES = List.of(
            Map.entry("ONVIF", ProbeType.ONVIF_PROBE),
            Map.entry("RTSP", ProbeType.RTSP_PROBE),
            Map.entry("SNMP", ProbeType.SNMP_PROBE),
            Map.entry("SSH", ProbeType.SSH_BANNER),
            Map.entry("TELNET", ProbeType.TELNET_BANNER),
            Map.entry("UPNP", ProbeType.UPNP_PROBE)
    );

    private final AssetRepository assetRepository;
    private final AssetTechnicalProfileRepository technicalProfileRepository;
    private final AssetInferenceCandidateRepository candidateRepository;
    private final AssetEvidenceRepository evidenceRepository;
    private final AssetServiceFingerprintRepository fingerprintRepository;
    private final AssetCollectionResultRepository collectionResultRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public AssetProfileResponse getProfile(Long assetId) {
        var asset = findAssetById(assetId);
        var basicInfo = new AssetResponse(asset);

        var technicalProfile = technicalProfileRepository.findByAssetId(assetId).orElse(null);
        var techResponse = technicalProfile != null ? new TechnicalProfileResponse(technicalProfile) : null;

        var candidates = candidateRepository.findByAssetIdOrderByConfidenceDesc(assetId)
                .stream().map(InferenceCandidateResponse::new).toList();

        var deviceTypeDetections = candidateRepository
                .findByAssetIdAndFieldNameOrderByConfidenceDesc(assetId, "deviceCategory")
                .stream().map(DeviceTypeDetectionResponse::new).toList();

        var evidences = evidenceRepository.findByAssetIdOrderByCollectedAtDesc(assetId)
                .stream().map(EvidenceResponse::new).toList();

        var fingerprints = fingerprintRepository.findByAssetIdOrderByPortAsc(assetId)
                .stream().map(ServiceFingerprintResponse::new).toList();

        var missingFields = computeMissingFields(basicInfo, techResponse);

        var protocolCapabilities = buildProtocolCapabilities(assetId);

        var kgPlaceholder = new AssetProfileResponse.KnowledgeEnhancementPlaceholder(
                false, "知识图谱增强将在后续版本中自动填充，当前可通过 /api/v1/kg/assets/{id}/enrich 手动查询");

        return new AssetProfileResponse(basicInfo, techResponse, missingFields, candidates, deviceTypeDetections, evidences, fingerprints, protocolCapabilities, kgPlaceholder);
    }

    @Override
    @Transactional(readOnly = true)
    public TechnicalProfileResponse getTechnicalFeatures(Long assetId) {
        ensureAssetExists(assetId);
        var profile = technicalProfileRepository.findByAssetId(assetId)
                .orElseGet(() -> emptyProfile(assetId));
        return new TechnicalProfileResponse(profile);
    }

    @Override
    public TechnicalProfileResponse updateTechnicalFeatures(Long assetId, TechnicalProfileUpdateRequest request) {
        ensureAssetExists(assetId);
        var profile = technicalProfileRepository.findByAssetId(assetId)
                .orElseGet(() -> {
                    var p = new AssetTechnicalProfile();
                    p.setAssetId(assetId);
                    return p;
                });

        if (request.getOpenPorts() != null) {
            profile.setOpenPorts(TechnicalProfileConverter.portsToJson(request.getOpenPorts()));
        }
        if (request.getProtocols() != null) {
            profile.setProtocols(TechnicalProfileConverter.protocolsToJson(request.getProtocols()));
        }
        if (request.getServiceBanner() != null) {
            profile.setServiceBanner(request.getServiceBanner());
        }
        if (request.getWebTitle() != null) {
            profile.setWebTitle(request.getWebTitle());
        }
        if (request.getFirmwareVersion() != null) {
            profile.setFirmwareVersion(request.getFirmwareVersion());
        }
        if (request.getSerialNumber() != null) {
            profile.setSerialNumber(request.getSerialNumber());
        }
        if (request.getMacAddress() != null) {
            profile.setMacAddress(request.getMacAddress());
        }
        if (request.getVendorHint() != null) {
            profile.setVendorHint(request.getVendorHint());
        }
        if (request.getLastFingerprintAt() != null) {
            profile.setLastFingerprintAt(request.getLastFingerprintAt());
        }

        return new TechnicalProfileResponse(technicalProfileRepository.save(profile));
    }

    @Override
    @Transactional(readOnly = true)
    public List<InferenceCandidateResponse> listInferenceCandidates(Long assetId) {
        ensureAssetExists(assetId);
        return candidateRepository.findByAssetIdOrderByConfidenceDesc(assetId)
                .stream().map(InferenceCandidateResponse::new).toList();
    }

    @Override
    public InferenceCandidateResponse createInferenceCandidate(Long assetId, InferenceCandidateRequest request) {
        ensureAssetExists(assetId);
        var candidate = new AssetInferenceCandidate();
        candidate.setAssetId(assetId);
        applyCandidate(candidate, request);
        return new InferenceCandidateResponse(candidateRepository.save(candidate));
    }

    @Override
    public InferenceCandidateResponse updateInferenceCandidate(Long assetId, Long candidateId, InferenceCandidateRequest request) {
        var candidate = candidateRepository.findByIdAndAssetId(candidateId, assetId)
                .orElseThrow(() -> new BusinessException(404, "候选推断不存在，id=" + candidateId));
        applyCandidate(candidate, request);
        return new InferenceCandidateResponse(candidateRepository.save(candidate));
    }

    @Override
    public void deleteInferenceCandidate(Long assetId, Long candidateId) {
        var candidate = candidateRepository.findByIdAndAssetId(candidateId, assetId)
                .orElseThrow(() -> new BusinessException(404, "候选推断不存在，id=" + candidateId));
        candidateRepository.delete(candidate);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServiceFingerprintResponse> listServiceFingerprints(Long assetId) {
        ensureAssetExists(assetId);
        return fingerprintRepository.findByAssetIdOrderByPortAsc(assetId)
                .stream().map(ServiceFingerprintResponse::new).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ServiceFingerprintResponse getServiceFingerprint(Long assetId, Long fingerprintId) {
        AssetServiceFingerprint fp = fingerprintRepository.findByIdAndAssetId(fingerprintId, assetId)
                .orElseThrow(() -> new BusinessException(404, "服务指纹不存在，id=" + fingerprintId));
        return new ServiceFingerprintResponse(fp);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EvidenceResponse> listEvidences(Long assetId) {
        ensureAssetExists(assetId);
        return evidenceRepository.findByAssetIdOrderByCollectedAtDesc(assetId)
                .stream().map(EvidenceResponse::new).toList();
    }

    @Override
    public EvidenceResponse createEvidence(Long assetId, EvidenceRequest request) {
        ensureAssetExists(assetId);
        var evidence = new AssetEvidence();
        evidence.setAssetId(assetId);
        applyEvidence(evidence, request);
        return new EvidenceResponse(evidenceRepository.save(evidence));
    }

    @Override
    public EvidenceResponse updateEvidence(Long assetId, Long evidenceId, EvidenceRequest request) {
        var evidence = evidenceRepository.findByIdAndAssetId(evidenceId, assetId)
                .orElseThrow(() -> new BusinessException(404, "证据来源不存在，id=" + evidenceId));
        applyEvidence(evidence, request);
        return new EvidenceResponse(evidenceRepository.save(evidence));
    }

    @Override
    public void deleteEvidence(Long assetId, Long evidenceId) {
        var evidence = evidenceRepository.findByIdAndAssetId(evidenceId, assetId)
                .orElseThrow(() -> new BusinessException(404, "证据来源不存在，id=" + evidenceId));
        evidenceRepository.delete(evidence);
    }

    // ---- helpers ----

    private com.camera.app.asset.entity.Asset findAssetById(Long assetId) {
        return assetRepository.findById(assetId)
                .orElseThrow(() -> new BusinessException(404, "资产不存在，id=" + assetId));
    }

    private void ensureAssetExists(Long assetId) {
        if (!assetRepository.existsById(assetId)) {
            throw new BusinessException(404, "资产不存在，id=" + assetId);
        }
    }

    private AssetTechnicalProfile emptyProfile(Long assetId) {
        var p = new AssetTechnicalProfile();
        p.setAssetId(assetId);
        return p;
    }

    private void applyCandidate(AssetInferenceCandidate c, InferenceCandidateRequest req) {
        if (StringUtils.hasText(req.getFieldName())) {
            c.setFieldName(req.getFieldName().trim());
        }
        if (StringUtils.hasText(req.getCandidateValue())) {
            c.setCandidateValue(req.getCandidateValue().trim());
        }
        if (req.getConfidence() != null) {
            c.setConfidence(req.getConfidence());
        }
        if (req.getReason() != null) {
            c.setReason(req.getReason());
        }
        if (req.getSourceType() != null) {
            c.setSourceType(req.getSourceType());
        }
        if (req.getConfirmed() != null) {
            c.setConfirmed(req.getConfirmed());
        }
    }

    private void applyEvidence(AssetEvidence e, EvidenceRequest req) {
        if (StringUtils.hasText(req.getFieldName())) {
            e.setFieldName(req.getFieldName().trim());
        }
        if (req.getFieldValue() != null) {
            e.setFieldValue(req.getFieldValue());
        }
        if (req.getSourceType() != null) {
            e.setSourceType(req.getSourceType());
        }
        if (req.getRawEvidence() != null) {
            e.setRawEvidence(req.getRawEvidence());
        }
        if (req.getConfidence() != null) {
            e.setConfidence(req.getConfidence());
        }
        e.setCollectedAt(req.getCollectedAt() != null ? req.getCollectedAt() : LocalDateTime.now());
        if (req.getNote() != null) {
            e.setNote(req.getNote());
        }
    }

    private List<ProtocolCapabilityResponse> buildProtocolCapabilities(Long assetId) {
        var allResults = collectionResultRepository.findByAssetIdOrderByCollectedAtDesc(assetId);
        var resultsByType = allResults.stream()
                .collect(Collectors.groupingBy(AssetCollectionResult::getProbeType));

        List<ProtocolCapabilityResponse> capabilities = new ArrayList<>();
        for (var entry : PROTOCOL_PROBE_ENTRIES) {
            String protocol = entry.getKey();
            ProbeType probeType = entry.getValue();
            var results = resultsByType.getOrDefault(probeType, List.of());
            capabilities.add(aggregateProtocol(protocol, results));
        }
        // WS_DISCOVERY has no probe type yet — always UNDETECTED
        capabilities.add(undetected("WS_DISCOVERY"));
        return capabilities;
    }

    private ProtocolCapabilityResponse aggregateProtocol(String protocol, List<AssetCollectionResult> results) {
        if (results.isEmpty()) {
            return undetected(protocol);
        }
        var successResult = results.stream().filter(AssetCollectionResult::isSuccess).findFirst();
        if (successResult.isPresent()) {
            return buildSupported(protocol, successResult.get());
        }
        return buildFailed(protocol, results.get(0));
    }

    private ProtocolCapabilityResponse buildSupported(String protocol, AssetCollectionResult result) {
        if ("ONVIF".equals(protocol)) {
            return buildOnvifCapability(result);
        }
        return ProtocolCapabilityResponse.builder()
                .protocol(protocol)
                .status("SUPPORTED")
                .supported(true)
                .summary("协议支持，服务可达")
                .lastDetectedAt(result.getCollectedAt())
                .sourceTaskId(result.getTaskId())
                .port(result.getTargetPort())
                .build();
    }

    @SuppressWarnings("unchecked")
    private ProtocolCapabilityResponse buildOnvifCapability(AssetCollectionResult result) {
        boolean authRequired = false;
        Map<String, Object> details = new HashMap<>();
        if (result.getParsedData() != null) {
            try {
                Map<String, Object> parsed = objectMapper.readValue(result.getParsedData(), Map.class);
                details.putAll(parsed);
                authRequired = Boolean.TRUE.equals(parsed.get("authRequired"));
            } catch (Exception ignored) {}
        }
        String status = authRequired ? "AUTH_REQUIRED" : "SUPPORTED";
        String summary = authRequired ? "设备服务可达，需认证" : "ONVIF 服务可达";
        return ProtocolCapabilityResponse.builder()
                .protocol("ONVIF")
                .status(status)
                .supported(true)
                .summary(summary)
                .lastDetectedAt(result.getCollectedAt())
                .sourceTaskId(result.getTaskId())
                .port(result.getTargetPort())
                .details(details.isEmpty() ? null : details)
                .build();
    }

    private ProtocolCapabilityResponse buildFailed(String protocol, AssetCollectionResult result) {
        return ProtocolCapabilityResponse.builder()
                .protocol(protocol)
                .status("FAILED")
                .supported(false)
                .summary("探测失败")
                .lastDetectedAt(result.getCollectedAt())
                .sourceTaskId(result.getTaskId())
                .port(result.getTargetPort())
                .build();
    }

    private ProtocolCapabilityResponse undetected(String protocol) {
        return ProtocolCapabilityResponse.builder()
                .protocol(protocol)
                .status("UNDETECTED")
                .supported(false)
                .summary("尚未探测")
                .build();
    }

    private List<String> computeMissingFields(AssetResponse basic, TechnicalProfileResponse tech) {
        List<String> missing = new ArrayList<>();
        if (!StringUtils.hasText(basic.getBrand())) {
            missing.add("brand");
        }
        if (!StringUtils.hasText(basic.getModel())) {
            missing.add("model");
        }
        if (basic.getType() == null) {
            missing.add("type");
        }
        if (tech == null || tech.getOpenPorts().isEmpty()) {
            missing.add("openPorts");
        }
        if (tech == null || tech.getProtocols().isEmpty()) {
            missing.add("protocols");
        }
        if (tech == null || !StringUtils.hasText(tech.getFirmwareVersion())) {
            missing.add("firmwareVersion");
        }
        return missing;
    }
}
