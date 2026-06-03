package com.camera.app.alert.repository;

import com.camera.app.alert.entity.Alert;
import com.camera.app.alert.entity.AlertSeverity;
import com.camera.app.alert.entity.AlertStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AlertRepository extends JpaRepository<Alert, Long>, JpaSpecificationExecutor<Alert> {

    // ── used by AlertGenerationService (dedup) ────────────────────────────────
    Optional<Alert> findFirstByFingerprintAndStatusIn(String fingerprint,
                                                      Collection<AlertStatus> statuses);

    // ── used by AlertRiskScoreService (per-asset recalc) ─────────────────────
    List<Alert> findByAssetIdAndStatusIn(Long assetId, Collection<AlertStatus> statuses);

    List<Alert> findByAssetIdInAndStatusIn(Collection<Long> assetIds,
                                           Collection<AlertStatus> statuses);

    long countByAssetIdAndStatusIn(Long assetId, Collection<AlertStatus> statuses);

    // ── used by RiskOverview: status counts ───────────────────────────────────
    long countByStatus(AlertStatus status);

    long countBySeverityAndStatusIn(AlertSeverity severity, Collection<AlertStatus> statuses);

    // ── used by RiskOverview: source distribution ─────────────────────────────
    @Query("SELECT a.sourceType, COUNT(a) FROM Alert a GROUP BY a.sourceType")
    List<Object[]> countGroupBySourceType();

    // ── used by RiskOverview: daily trends (native for DATE() function) ────────
    @Query(value = """
            SELECT DATE(created_at)                                                   AS d,
                   COUNT(*)                                                           AS total,
                   SUM(CASE WHEN status IN ('NEW','CONFIRMED') THEN 1 ELSE 0 END)    AS open_cnt
            FROM   alerts
            WHERE  created_at >= :since
            GROUP  BY DATE(created_at)
            ORDER  BY d ASC
            """, nativeQuery = true)
    List<Object[]> dailyTrends(@Param("since") LocalDateTime since);

    // ── used by latest-alerts endpoint ────────────────────────────────────────
    Page<Alert> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
