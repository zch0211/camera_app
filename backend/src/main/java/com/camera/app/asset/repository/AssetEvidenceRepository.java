package com.camera.app.asset.repository;

import com.camera.app.asset.entity.AssetEvidence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssetEvidenceRepository extends JpaRepository<AssetEvidence, Long> {

    List<AssetEvidence> findByAssetIdOrderByCollectedAtDesc(Long assetId);

    Optional<AssetEvidence> findByIdAndAssetId(Long id, Long assetId);
}
