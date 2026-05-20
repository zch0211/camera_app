package com.camera.app.collection.service;

import com.camera.app.asset.repository.AssetRepository;
import com.camera.app.collection.dto.CollectionResultResponse;
import com.camera.app.collection.dto.CollectionTaskCreateRequest;
import com.camera.app.collection.dto.CollectionTaskResponse;
import com.camera.app.collection.entity.*;
import com.camera.app.collection.plugin.ProbeRegistry;
import com.camera.app.collection.repository.AssetCollectionResultRepository;
import com.camera.app.collection.repository.AssetCollectionTaskRepository;
import com.camera.app.common.exception.BusinessException;
import com.camera.app.common.response.PageResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AssetCollectionServiceImpl implements AssetCollectionService {

    private final AssetRepository assetRepository;
    private final AssetCollectionTaskRepository taskRepository;
    private final AssetCollectionResultRepository resultRepository;
    private final ProbeRegistry probeRegistry;
    private final CollectionTaskExecutor taskExecutor;
    private final ObjectMapper objectMapper;

    @Override
    public CollectionTaskResponse createAndExecuteTask(Long assetId, CollectionTaskCreateRequest req) {
        var asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new BusinessException(404, "资产不存在，id=" + assetId));

        TaskPreset preset = req.getPreset() != null ? req.getPreset() : TaskPreset.CAMERA_PRESET;

        String enabledPluginsJson = null;
        if (req.getEnabledPlugins() != null && !req.getEnabledPlugins().isEmpty()) {
            try { enabledPluginsJson = objectMapper.writeValueAsString(req.getEnabledPlugins()); }
            catch (Exception ignored) {}
        }

        // Resolve ports now so async thread doesn't need to re-read request
        List<Integer> ports = (req.getPorts() != null && !req.getPorts().isEmpty())
                ? req.getPorts()
                : probeRegistry.resolveDefaultPorts(preset);

        int timeoutMs = req.getTimeoutMillis() != null ? req.getTimeoutMillis() : 2000;

        var task = new AssetCollectionTask();
        task.setAssetId(assetId);
        task.setTaskType(CollectionTaskType.PLUGIN_PROBE);
        task.setPreset(preset);
        task.setEnabledPlugins(enabledPluginsJson);
        task.setStatus(CollectionTaskStatus.PENDING);
        task.setTriggerType(TriggerType.MANUAL);
        task.setSummary("任务已创建，等待执行");
        task = taskRepository.save(task);

        // Capture finals for lambda; trigger async AFTER this transaction commits
        // so the task row is guaranteed to exist in DB when the async thread reads it.
        final Long taskId = task.getId();
        final String targetIp = asset.getIp();
        final List<Integer> resolvedPorts = ports;
        final int resolvedTimeout = timeoutMs;
        final TaskPreset resolvedPreset = preset;
        final List<String> enabledPluginNames = req.getEnabledPlugins();

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                taskExecutor.executeAsync(assetId, taskId, targetIp,
                        resolvedPorts, resolvedTimeout, resolvedPreset, enabledPluginNames);
            }
        });

        log.info("采集任务已创建 taskId={} assetId={} preset={}", taskId, assetId, preset);
        return new CollectionTaskResponse(task, 0L);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<CollectionTaskResponse> listTasks(Long assetId, int page, int size) {
        ensureAssetExists(assetId);
        var pageResult = taskRepository.findByAssetIdOrderByCreatedAtDesc(
                assetId, PageRequest.of(page, size));
        return new PageResult<>(pageResult.map(t ->
                new CollectionTaskResponse(t, resultRepository.countByTaskId(t.getId()))));
    }

    @Override
    @Transactional(readOnly = true)
    public CollectionTaskResponse getTask(Long assetId, Long taskId) {
        var task = taskRepository.findByIdAndAssetId(taskId, assetId)
                .orElseThrow(() -> new BusinessException(404, "采集任务不存在，id=" + taskId));
        return new CollectionTaskResponse(task, resultRepository.countByTaskId(taskId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CollectionResultResponse> getTaskResults(Long assetId, Long taskId) {
        taskRepository.findByIdAndAssetId(taskId, assetId)
                .orElseThrow(() -> new BusinessException(404, "采集任务不存在，id=" + taskId));
        return resultRepository.findByTaskIdOrderByCollectedAtAsc(taskId)
                .stream().map(CollectionResultResponse::new).toList();
    }

    private void ensureAssetExists(Long assetId) {
        if (!assetRepository.existsById(assetId)) {
            throw new BusinessException(404, "资产不存在，id=" + assetId);
        }
    }
}
