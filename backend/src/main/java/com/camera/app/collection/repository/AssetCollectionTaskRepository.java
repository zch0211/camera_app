package com.camera.app.collection.repository;

import com.camera.app.collection.entity.AssetCollectionTask;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AssetCollectionTaskRepository extends JpaRepository<AssetCollectionTask, Long> {

    Page<AssetCollectionTask> findByAssetIdOrderByCreatedAtDesc(Long assetId, Pageable pageable);

    Optional<AssetCollectionTask> findByIdAndAssetId(Long id, Long assetId);
}
