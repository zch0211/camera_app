package com.camera.app.alert.service;

import com.camera.app.alert.dto.*;
import com.camera.app.alert.entity.*;
import com.camera.app.alert.repository.AlertEvidenceRepository;
import com.camera.app.alert.repository.AlertOperationRepository;
import com.camera.app.alert.repository.AlertRepository;
import com.camera.app.asset.entity.Asset;
import com.camera.app.asset.repository.AssetRepository;
import com.camera.app.common.exception.BusinessException;
import com.camera.app.common.response.PageResult;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class AlertServiceImpl implements AlertService {

    private final AlertRepository            alertRepository;
    private final AlertEvidenceRepository    alertEvidenceRepository;
    private final AlertOperationRepository   alertOperationRepository;
    private final AssetRepository            assetRepository;
    private final AlertRiskScoreService      riskScoreService;

    // ─── public API ───────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public PageResult<AlertListItemResponse> listAlerts(String keyword, AlertSeverity severity,
            AlertStatus status, AlertSourceType sourceType, Long assetId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Alert> alerts = alertRepository.findAll(buildSpec(keyword, severity, status, sourceType, assetId), pageable);

        // Batch-load assets to avoid N+1
        Set<Long> assetIds = alerts.getContent().stream()
                .map(Alert::getAssetId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Asset> assetMap = assetIds.isEmpty() ? Map.of() :
                assetRepository.findAllById(assetIds).stream()
                        .collect(Collectors.toMap(Asset::getId, a -> a));

        return new PageResult<>(alerts.map(a -> new AlertListItemResponse(a, assetMap.get(a.getAssetId()))));
    }

    @Override
    @Transactional(readOnly = true)
    public AlertDetailResponse getAlert(Long id) {
        return buildDetail(findById(id));
    }

    @Override
    public AlertDetailResponse createAlert(AlertCreateRequest request, String createdBy) {
        // Validate assetId: if provided, the asset must exist
        Asset asset = null;
        if (request.getAssetId() != null) {
            asset = assetRepository.findById(request.getAssetId())
                    .orElseThrow(() -> new BusinessException(404,
                            "关联资产不存在: assetId=" + request.getAssetId()));
        }

        Alert alert = new Alert();
        alert.setAssetId(request.getAssetId());
        alert.setTitle(request.getTitle());
        alert.setSourceType(request.getSourceType());
        alert.setSeverity(request.getSeverity());
        alert.setStatus(AlertStatus.NEW);
        alert.setSummary(request.getSummary());
        alert.setRuleCode(request.getRuleCode());
        alert.setRuleName(request.getRuleName());
        LocalDateTime now = LocalDateTime.now();
        alert.setFirstTriggeredAt(now);
        alert.setLastTriggeredAt(now);
        alert.setTriggerCount(1);

        // Write asset snapshot so detail page still works after asset deletion
        if (asset != null) {
            alert.setAssetNameSnapshot(asset.getName());
            alert.setAssetIpSnapshot(asset.getIp());
            alert.setAssetTypeSnapshot(asset.getType() != null ? asset.getType().name() : null);
        }

        alertRepository.save(alert);
        writeOperation(alert.getId(), AlertOperationType.CREATE, createdBy, null);
        riskScoreService.recalculate(alert.getAssetId());
        return buildDetail(alert);
    }

    @Override
    public AlertDetailResponse confirm(Long id, String comment, String operatorUsername) {
        Alert alert = findById(id);
        requireActive(alert);
        alert.setStatus(AlertStatus.CONFIRMED);
        alertRepository.save(alert);
        writeOperation(id, AlertOperationType.CONFIRM, operatorUsername, comment);
        riskScoreService.recalculate(alert.getAssetId());
        return buildDetail(alert);
    }

    @Override
    public AlertDetailResponse markFalsePositive(Long id, String comment, String operatorUsername) {
        Alert alert = findById(id);
        requireActive(alert);
        alert.setStatus(AlertStatus.FALSE_POSITIVE);
        alertRepository.save(alert);
        writeOperation(id, AlertOperationType.MARK_FALSE_POSITIVE, operatorUsername, comment);
        riskScoreService.recalculate(alert.getAssetId());
        return buildDetail(alert);
    }

    @Override
    public AlertDetailResponse resolve(Long id, String comment, String operatorUsername) {
        Alert alert = findById(id);
        requireActive(alert);
        alert.setStatus(AlertStatus.RESOLVED);
        alert.setResolvedAt(LocalDateTime.now());
        alert.setResolvedBy(operatorUsername);
        alertRepository.save(alert);
        writeOperation(id, AlertOperationType.RESOLVE, operatorUsername, comment);
        riskScoreService.recalculate(alert.getAssetId());
        return buildDetail(alert);
    }

    @Override
    public AlertDetailResponse ignore(Long id, String comment, String operatorUsername) {
        Alert alert = findById(id);
        requireActive(alert);
        alert.setStatus(AlertStatus.IGNORED);
        alertRepository.save(alert);
        writeOperation(id, AlertOperationType.IGNORE, operatorUsername, comment);
        riskScoreService.recalculate(alert.getAssetId());
        return buildDetail(alert);
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private Alert findById(Long id) {
        return alertRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "告警不存在，id=" + id));
    }

    private void requireActive(Alert alert) {
        if (alert.getStatus() != AlertStatus.NEW && alert.getStatus() != AlertStatus.CONFIRMED) {
            throw new BusinessException(400,
                    "告警已处于终止状态（" + alert.getStatus() + "），无法继续流转。如需重新处理请联系管理员");
        }
    }

    private void writeOperation(Long alertId, AlertOperationType type,
                                String operator, String comment) {
        AlertOperation op = new AlertOperation();
        op.setAlertId(alertId);
        op.setOperationType(type);
        op.setOperatorUsername(operator);
        op.setComment(comment);
        alertOperationRepository.save(op);
    }

    private AlertDetailResponse buildDetail(Alert alert) {
        Asset asset = alert.getAssetId() != null
                ? assetRepository.findById(alert.getAssetId()).orElse(null) : null;
        List<AlertEvidence>  evidences  = alertEvidenceRepository.findByAlertIdOrderByCreatedAtAsc(alert.getId());
        List<AlertOperation> operations = alertOperationRepository.findByAlertIdOrderByCreatedAtAsc(alert.getId());
        return new AlertDetailResponse(alert, asset, evidences, operations);
    }

    private Specification<Alert> buildSpec(String keyword, AlertSeverity severity,
            AlertStatus status, AlertSourceType sourceType, Long assetId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(keyword)) {
                String like = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), like),
                        cb.like(cb.lower(root.get("summary")), like)
                ));
            }
            if (severity   != null) predicates.add(cb.equal(root.get("severity"),   severity));
            if (status     != null) predicates.add(cb.equal(root.get("status"),     status));
            if (sourceType != null) predicates.add(cb.equal(root.get("sourceType"), sourceType));
            if (assetId    != null) predicates.add(cb.equal(root.get("assetId"),    assetId));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
