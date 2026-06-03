package com.camera.app.alert.repository;

import com.camera.app.alert.entity.AlertOperation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlertOperationRepository extends JpaRepository<AlertOperation, Long> {

    List<AlertOperation> findByAlertIdOrderByCreatedAtAsc(Long alertId);
}
