package com.camera.app.discovery.service;

import com.camera.app.asset.entity.Asset;
import com.camera.app.asset.entity.AssetType;
import com.camera.app.asset.repository.AssetRepository;
import com.camera.app.common.exception.BusinessException;
import com.camera.app.common.response.PageResult;
import com.camera.app.discovery.dto.*;
import com.camera.app.discovery.entity.*;
import com.camera.app.discovery.repository.DiscoveryResultRepository;
import com.camera.app.discovery.repository.DiscoveryTaskRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DiscoveryServiceImpl implements DiscoveryService {

    private final DiscoveryTaskRepository taskRepository;
    private final DiscoveryResultRepository resultRepository;
    private final AssetRepository assetRepository;
    private final DiscoveryTaskExecutor taskExecutor;
    private final ObjectMapper objectMapper;

    @Override
    public DiscoveryTaskResponse createTask(DiscoveryTaskCreateRequest req) {
        DiscoveryTask task = new DiscoveryTask();
        task.setTaskType(req.getTaskType());
        task.setPreset(req.getPreset());
        task.setTargetScope(req.getTargetScope());
        task.setStatus(DiscoveryStatus.PENDING);

        int timeoutMs = req.getTimeoutMillis() != null ? req.getTimeoutMillis() : 2000;
        task.setTimeoutMillis(timeoutMs);

        if (req.getSniffDurationSeconds() != null) {
            task.setSniffDurationSec(req.getSniffDurationSeconds());
        }
        if (req.getNetworkInterface() != null) {
            task.setNetworkInterface(req.getNetworkInterface());
        }

        List<Integer> ports = resolvePorts(req);
        if (req.getPorts() != null && !req.getPorts().isEmpty()) {
            try { task.setPorts(objectMapper.writeValueAsString(req.getPorts())); }
            catch (Exception ignored) {}
        }

        task = taskRepository.save(task);

        // Capture finals for afterCommit lambda
        final Long taskId          = task.getId();
        final DiscoveryTaskType    type      = task.getTaskType();
        final DiscoveryPreset      preset    = task.getPreset();
        final String               scope     = task.getTargetScope();
        final List<Integer>        finalPorts = ports;
        final int                  finalTimeout = timeoutMs;
        final int                  sniffSec  = task.getSniffDurationSec();
        final String               iface     = task.getNetworkInterface();

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                taskExecutor.executeAsync(taskId, type, preset, scope,
                        finalPorts, finalTimeout, sniffSec, iface);
            }
        });

        log.info("发现任务已创建 taskId={} type={} scope={}", taskId, type, scope);
        return new DiscoveryTaskResponse(task);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<DiscoveryTaskResponse> listTasks(int page, int size) {
        return new PageResult<>(taskRepository.findAllByOrderByCreatedAtDesc(
                PageRequest.of(page, size)).map(DiscoveryTaskResponse::new));
    }

    @Override
    @Transactional(readOnly = true)
    public DiscoveryTaskResponse getTask(Long taskId) {
        return new DiscoveryTaskResponse(loadTask(taskId));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<DiscoveryResultResponse> listResults(Long taskId, Boolean managed,
                                                           DiscoverySourceType sourceType,
                                                           String deviceType, String keyword,
                                                           int page, int size) {
        loadTask(taskId); // ensure task exists
        return new PageResult<>(resultRepository.findByTaskIdWithFilters(
                taskId, managed, sourceType, deviceType, keyword,
                PageRequest.of(page, size)).map(DiscoveryResultResponse::new));
    }

    @Override
    public BatchImportResponse batchImport(BatchImportRequest req) {
        List<DiscoveryResult> results = resultRepository.findByIdIn(req.getResultIds());

        int importedCount = 0;
        int skippedCount  = 0;
        int failedCount   = 0;
        List<Long>   importedAssetIds = new ArrayList<>();
        List<String> failedIps        = new ArrayList<>();

        for (DiscoveryResult dr : results) {
            if (dr.isManaged() && dr.getLinkedAssetId() != null) {
                skippedCount++;
                continue;
            }
            try {
                // Double-check by IP — might have been imported by another session
                if (assetRepository.existsByIp(dr.getIp())) {
                    assetRepository.findAll().stream()
                            .filter(a -> a.getIp().equals(dr.getIp()))
                            .findFirst()
                            .ifPresent(a -> {
                                dr.setLinkedAssetId(a.getId());
                                dr.setManaged(true);
                            });
                    resultRepository.save(dr);
                    skippedCount++;
                    continue;
                }

                Asset asset = buildAsset(dr, req.getDefaultLocation(), req.getDefaultOrgId());
                asset = assetRepository.save(asset);

                dr.setLinkedAssetId(asset.getId());
                dr.setManaged(true);
                resultRepository.save(dr);

                importedAssetIds.add(asset.getId());
                importedCount++;
                log.info("发现结果已导入资产 resultId={} assetId={} ip={}", dr.getId(), asset.getId(), dr.getIp());
            } catch (Exception e) {
                log.error("导入资产失败 resultId={} ip={}: {}", dr.getId(), dr.getIp(), e.getMessage());
                failedIps.add(dr.getIp());
                failedCount++;
            }
        }

        return new BatchImportResponse(importedCount, skippedCount, failedCount, importedAssetIds, failedIps);
    }

    @Override
    public DiscoveryTaskResponse importSingle(Long resultId, String defaultLocation, Long defaultOrgId) {
        DiscoveryResult dr = resultRepository.findById(resultId)
                .orElseThrow(() -> new BusinessException(404, "发现结果不存在，id=" + resultId));

        if (dr.isManaged() && dr.getLinkedAssetId() != null) {
            return getTask(dr.getTaskId());
        }

        if (assetRepository.existsByIp(dr.getIp())) {
            throw new BusinessException(409, "IP " + dr.getIp() + " 对应的资产已存在");
        }

        Asset asset = buildAsset(dr, defaultLocation, defaultOrgId);
        asset = assetRepository.save(asset);
        dr.setLinkedAssetId(asset.getId());
        dr.setManaged(true);
        resultRepository.save(dr);

        log.info("单条发现结果已导入 resultId={} assetId={}", resultId, asset.getId());
        return getTask(dr.getTaskId());
    }

    // ---- helpers ----

    private DiscoveryTask loadTask(Long taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(404, "发现任务不存在，id=" + taskId));
    }

    private List<Integer> resolvePorts(DiscoveryTaskCreateRequest req) {
        if (req.getPorts() != null && !req.getPorts().isEmpty()) return req.getPorts();
        if (req.getTaskType() == DiscoveryTaskType.ACTIVE_SCAN) return req.getPreset().defaultPorts();
        return List.of(); // passive sniff doesn't use port list
    }

    private Asset buildAsset(DiscoveryResult dr, String defaultLocation, Long defaultOrgId) {
        Asset asset = new Asset();
        asset.setIp(dr.getIp());
        asset.setName(buildAssetName(dr));
        asset.setBrand(dr.getVendorHint());
        asset.setType(mapDeviceType(dr.getDeviceTypeCandidate()));
        asset.setOnline(true);
        asset.setLocation(defaultLocation);
        asset.setOrgId(defaultOrgId);
        return asset;
    }

    private String buildAssetName(DiscoveryResult dr) {
        if (dr.getHostname() != null && !dr.getHostname().isBlank()) return dr.getHostname();
        if (dr.getVendorHint() != null && !dr.getVendorHint().isBlank())
            return dr.getVendorHint() + "-" + dr.getIp();
        return "发现设备-" + dr.getIp();
    }

    private AssetType mapDeviceType(String candidate) {
        if (candidate == null) return AssetType.OTHER;
        return switch (candidate) {
            case "CAMERA" -> AssetType.CAMERA;
            case "NVR"    -> AssetType.NVR;
            case "ROUTER" -> AssetType.ROUTER;
            case "SERVER" -> AssetType.SERVER;
            default       -> AssetType.OTHER;
        };
    }
}
