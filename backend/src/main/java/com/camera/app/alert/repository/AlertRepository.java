package com.camera.app.alert.repository;

import com.camera.app.alert.entity.Alert;
import com.camera.app.alert.entity.AlertStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;

public interface AlertRepository extends JpaRepository<Alert, Long>, JpaSpecificationExecutor<Alert> {

    List<Alert> findByAssetIdAndStatusIn(Long assetId, Collection<AlertStatus> statuses);

    List<Alert> findByAssetIdInAndStatusIn(Collection<Long> assetIds, Collection<AlertStatus> statuses);

    long countByAssetIdAndStatusIn(Long assetId, Collection<AlertStatus> statuses);
}
