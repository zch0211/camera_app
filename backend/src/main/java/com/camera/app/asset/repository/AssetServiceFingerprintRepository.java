package com.camera.app.asset.repository;

import com.camera.app.asset.entity.AssetServiceFingerprint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssetServiceFingerprintRepository extends JpaRepository<AssetServiceFingerprint, Long> {

    List<AssetServiceFingerprint> findByAssetIdOrderByPortAsc(Long assetId);

    Optional<AssetServiceFingerprint> findByAssetIdAndPort(Long assetId, Integer port);

    Optional<AssetServiceFingerprint> findByIdAndAssetId(Long id, Long assetId);
}
