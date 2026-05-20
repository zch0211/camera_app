package com.camera.app.asset.repository;

import com.camera.app.asset.entity.AssetInferenceCandidate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssetInferenceCandidateRepository extends JpaRepository<AssetInferenceCandidate, Long> {

    List<AssetInferenceCandidate> findByAssetIdOrderByConfidenceDesc(Long assetId);

    Optional<AssetInferenceCandidate> findByIdAndAssetId(Long id, Long assetId);

    Optional<AssetInferenceCandidate> findByAssetIdAndFieldNameAndCandidateValue(
            Long assetId, String fieldName, String candidateValue);

    List<AssetInferenceCandidate> findByAssetIdAndFieldNameOrderByConfidenceDesc(
            Long assetId, String fieldName);
}
