package com.camera.app.alert.service;

import com.camera.app.alert.entity.Alert;
import com.camera.app.alert.entity.AlertSeverity;
import com.camera.app.alert.entity.AlertStatus;
import com.camera.app.alert.repository.AlertRepository;
import com.camera.app.asset.entity.Asset;
import com.camera.app.asset.repository.AssetRepository;
import com.camera.app.alert.entity.AlertSeverity;
import com.camera.app.alert.entity.AlertStatus;
import com.camera.app.common.response.PageResult;
import com.camera.app.risk.dto.AlertTrendEntry;
import com.camera.app.risk.dto.HighRiskAssetResponse;
import com.camera.app.risk.dto.RiskOverviewResponse;
import com.camera.app.risk.dto.SourceDistributionEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AlertRiskScoreService {

    private static final List<AlertStatus> ACTIVE = List.of(AlertStatus.NEW, AlertStatus.CONFIRMED);

    private final AlertRepository alertRepository;
    private final AssetRepository assetRepository;

    // ═══════════════════════════════════════════════════════════════
    // Risk Overview — statistics for the dashboard
    // ═══════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public RiskOverviewResponse getOverview() {
        List<AlertStatus> activeStatuses = ACTIVE;
        long total        = alertRepository.count();
        long open         = alertRepository.countByStatus(AlertStatus.NEW)
                          + alertRepository.countByStatus(AlertStatus.CONFIRMED);
        long critical     = alertRepository.countBySeverityAndStatusIn(AlertSeverity.CRITICAL, activeStatuses);
        long high         = alertRepository.countBySeverityAndStatusIn(AlertSeverity.HIGH, activeStatuses);
        long medium       = alertRepository.countBySeverityAndStatusIn(AlertSeverity.MEDIUM, activeStatuses);
        long low          = alertRepository.countBySeverityAndStatusIn(AlertSeverity.LOW, activeStatuses);
        long confirmed    = alertRepository.countByStatus(AlertStatus.CONFIRMED);
        long fp           = alertRepository.countByStatus(AlertStatus.FALSE_POSITIVE);
        long resolved     = alertRepository.countByStatus(AlertStatus.RESOLVED);
        long ignored      = alertRepository.countByStatus(AlertStatus.IGNORED);
        long riskAssets   = assetRepository.countByRiskScoreGreaterThanEqual(1);
        return RiskOverviewResponse.builder()
                .totalAlerts(total).openAlerts(open)
                .criticalAlerts(critical).highAlerts(high).mediumAlerts(medium).lowAlerts(low)
                .confirmedAlerts(confirmed).falsePositiveAlerts(fp)
                .resolvedAlerts(resolved).ignoredAlerts(ignored)
                .highRiskAssets(riskAssets)
                .build();
    }

    @Transactional(readOnly = true)
    public List<AlertTrendEntry> getAlertTrends(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<Object[]> rows = alertRepository.dailyTrends(since);
        return rows.stream().map(row -> new AlertTrendEntry(
                row[0].toString(),
                ((Number) row[1]).longValue(),
                ((Number) row[2]).longValue()
        )).toList();
    }

    @Transactional(readOnly = true)
    public List<SourceDistributionEntry> getSourceDistribution() {
        List<Object[]> rows = alertRepository.countGroupBySourceType();
        return rows.stream().map(row -> new SourceDistributionEntry(
                (com.camera.app.alert.entity.AlertSourceType) row[0],
                ((Number) row[1]).longValue()
        )).toList();
    }

    // ═══════════════════════════════════════════════════════════════

    /**
     * Recalculate and persist the riskScore for a single asset.
     * Called after any alert state change involving that asset.
     */
    @Transactional
    public void recalculate(Long assetId) {
        if (assetId == null) return;
        List<Alert> active = alertRepository.findByAssetIdAndStatusIn(assetId, ACTIVE);
        int score = active.stream().mapToInt(a -> a.getSeverity().weight()).sum();
        score = Math.min(score, 100);
        final int finalScore = score;
        assetRepository.findById(assetId).ifPresent(asset -> {
            asset.setRiskScore(finalScore);
            assetRepository.save(asset);
        });
    }

    /**
     * High-risk asset list ordered by riskScore DESC.
     * Enriched with open-alert stats per asset.
     */
    @Transactional(readOnly = true)
    public PageResult<HighRiskAssetResponse> highRiskAssets(Integer minScore, int page, int size) {
        // Default threshold = 1 so zero-risk assets are excluded unless caller explicitly passes minScore=0
        final int threshold = (minScore != null && minScore >= 0) ? minScore : 1;
        Specification<Asset> spec = (root, query, cb) ->
                cb.greaterThanOrEqualTo(root.get("riskScore"), threshold);
        Page<Asset> assets = assetRepository.findAll(spec,
                PageRequest.of(page, size, Sort.by("riskScore").descending()));

        // Batch-load open alerts for all assets on this page
        List<Long> ids = assets.getContent().stream().map(Asset::getId).toList();
        Map<Long, List<Alert>> alertsByAsset = ids.isEmpty() ? Map.of() :
                alertRepository.findByAssetIdInAndStatusIn(ids, ACTIVE).stream()
                        .collect(Collectors.groupingBy(Alert::getAssetId));

        return new PageResult<>(assets.map(asset -> {
            List<Alert> openAlerts = alertsByAsset.getOrDefault(asset.getId(), List.of());
            long openCount = openAlerts.size();
            AlertSeverity highest = openAlerts.stream()
                    .map(Alert::getSeverity)
                    .max(Comparator.comparingInt(AlertSeverity::weight))
                    .orElse(null);
            LocalDateTime latestAt = openAlerts.stream()
                    .map(Alert::getLastTriggeredAt)
                    .filter(Objects::nonNull)
                    .max(Comparator.naturalOrder())
                    .orElse(null);
            return new HighRiskAssetResponse(asset, openCount, highest, latestAt);
        }));
    }
}
