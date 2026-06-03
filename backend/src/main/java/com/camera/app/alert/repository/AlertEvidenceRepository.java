package com.camera.app.alert.repository;

import com.camera.app.alert.entity.AlertEvidence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlertEvidenceRepository extends JpaRepository<AlertEvidence, Long> {

    List<AlertEvidence> findByAlertIdOrderByCreatedAtAsc(Long alertId);
}
