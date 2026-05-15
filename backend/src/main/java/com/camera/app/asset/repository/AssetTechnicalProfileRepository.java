package com.camera.app.asset.repository;

import com.camera.app.asset.entity.AssetTechnicalProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AssetTechnicalProfileRepository extends JpaRepository<AssetTechnicalProfile, Long> {

    Optional<AssetTechnicalProfile> findByAssetId(Long assetId);

    void deleteByAssetId(Long assetId);
}
