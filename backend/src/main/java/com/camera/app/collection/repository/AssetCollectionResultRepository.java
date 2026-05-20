package com.camera.app.collection.repository;

import com.camera.app.collection.entity.AssetCollectionResult;
import com.camera.app.collection.entity.ProbeType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssetCollectionResultRepository extends JpaRepository<AssetCollectionResult, Long> {

    List<AssetCollectionResult> findByTaskIdOrderByCollectedAtAsc(Long taskId);

    long countByTaskId(Long taskId);

    List<AssetCollectionResult> findByAssetIdOrderByCollectedAtDesc(Long assetId);

    List<AssetCollectionResult> findByAssetIdAndProbeTypeOrderByCollectedAtDesc(Long assetId, ProbeType probeType);
}
