package com.camera.app.asset.service;

import com.camera.app.asset.dto.*;

import java.util.List;

public interface AssetProfileService {

    AssetProfileResponse getProfile(Long assetId);

    List<ServiceFingerprintResponse> listServiceFingerprints(Long assetId);

    ServiceFingerprintResponse getServiceFingerprint(Long assetId, Long fingerprintId);

    TechnicalProfileResponse getTechnicalFeatures(Long assetId);

    TechnicalProfileResponse updateTechnicalFeatures(Long assetId, TechnicalProfileUpdateRequest request);

    List<InferenceCandidateResponse> listInferenceCandidates(Long assetId);

    InferenceCandidateResponse createInferenceCandidate(Long assetId, InferenceCandidateRequest request);

    InferenceCandidateResponse updateInferenceCandidate(Long assetId, Long candidateId, InferenceCandidateRequest request);

    void deleteInferenceCandidate(Long assetId, Long candidateId);

    List<EvidenceResponse> listEvidences(Long assetId);

    EvidenceResponse createEvidence(Long assetId, EvidenceRequest request);

    EvidenceResponse updateEvidence(Long assetId, Long evidenceId, EvidenceRequest request);

    void deleteEvidence(Long assetId, Long evidenceId);
}
