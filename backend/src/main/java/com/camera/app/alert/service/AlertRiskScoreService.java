package com.camera.app.alert.service;

import com.camera.app.alert.entity.Alert;
import com.camera.app.alert.entity.AlertSeverity;
import com.camera.app.alert.entity.AlertStatus;
import com.camera.app.alert.repository.AlertRepository;
import com.camera.app.asset.entity.Asset;
import com.camera.app.asset.repository.AssetRepository;
import com.camera.app.common.response.PageResult;
import com.camera.app.risk.dto.HighRiskAssetResponse;
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
