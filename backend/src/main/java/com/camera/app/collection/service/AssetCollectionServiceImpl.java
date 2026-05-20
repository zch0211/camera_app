package com.camera.app.collection.service;

import com.camera.app.asset.repository.AssetRepository;
import com.camera.app.collection.dto.CollectionResultResponse;
import com.camera.app.collection.dto.CollectionTaskCreateRequest;
import com.camera.app.collection.dto.CollectionTaskResponse;
import com.camera.app.collection.entity.*;
import com.camera.app.collection.plugin.*;
import com.camera.app.collection.repository.AssetCollectionResultRepository;
import com.camera.app.collection.repository.AssetCollectionTaskRepository;
import com.camera.app.collection.rules.CategoryDetectionResult;
import com.camera.app.collection.rules.DeviceTypeRuleEngine;
import com.camera.app.common.exception.BusinessException;
import com.camera.app.common.response.PageResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AssetCollectionServiceImpl implements AssetCollectionService {

    private final AssetRepository assetRepository;
    private final AssetCollectionTaskRepository taskRepository;
    private final AssetCollectionResultRepository resultRepository;
    private final ProbeRegistry probeRegistry;
    private final ProbeWritebackService writebackService;
    private final DeviceTypeRuleEngine ruleEngine;
    private final ObjectMapper objectMapper;

    @Override
    public CollectionTaskResponse createAndExecuteTask(Long assetId, CollectionTaskCreateRequest req) {
        var asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new BusinessException(404, "资产不存在，id=" + assetId));

        TaskPreset preset = req.getPreset() != null ? req.getPreset() : TaskPreset.CAMERA_PRESET;

        // Persist enabled plugins list as JSON
        String enabledPluginsJson = null;
        if (req.getEnabledPlugins() != null && !req.getEnabledPlugins().isEmpty()) {
            try { enabledPluginsJson = objectMapper.writeValueAsString(req.getEnabledPlugins()); }
            catch (Exception ignored) {}
        }

        var task = new AssetCollectionTask();
        task.setAssetId(assetId);
        task.setTaskType(CollectionTaskType.PLUGIN_PROBE);
        task.setPreset(preset);
        task.setEnabledPlugins(enabledPluginsJson);
        task.setStatus(CollectionTaskStatus.RUNNING);
        task.setTriggerType(TriggerType.MANUAL);
        task.setStartedAt(LocalDateTime.now());
        task = taskRepository.save(task);

        // Resolve ports (request overrides preset defaults)
        List<Integer> ports;
        if (req.getPorts() != null && !req.getPorts().isEmpty()) {
            ports = req.getPorts();
        } else {
            ports = probeRegistry.resolveDefaultPorts(preset);
        }

        int timeoutMs = req.getTimeoutMillis() != null ? req.getTimeoutMillis() : 2000;

        // Resolve plugins
        List<ProbePlugin> plugins = probeRegistry.resolvePlugins(preset, req.getEnabledPlugins());
        if (plugins.isEmpty()) {
            log.warn("No plugins resolved for preset={}, falling back to port+http", preset);
            plugins = probeRegistry.resolvePlugins(TaskPreset.CUSTOM,
                    List.of("port-probe", "http-fingerprint"));
        }

        try {
            // Build context (config can carry per-plugin settings)
            ProbeContext ctx = new ProbeContext(assetId, task.getId(), asset.getIp(),
                    ports, timeoutMs, preset, Map.of());

            // Execute plugins
            List<ProbeResult> allResults = new ArrayList<>();
            for (ProbePlugin plugin : plugins) {
                if (!plugin.supports(ctx)) continue;
                try {
                    List<ProbeResult> results = plugin.execute(ctx);
                    allResults.addAll(results);
                    log.debug("Plugin {} produced {} results", plugin.getName(), results.size());
                } catch (Exception e) {
                    log.error("Plugin {} failed: {}", plugin.getName(), e.getMessage(), e);
                }
            }

            // Persist raw results + fingerprints + device profile + evidences
            writebackService.processResults(assetId, task.getId(), allResults);

            // Run device-type rules engine → generates inference candidates
            List<CategoryDetectionResult> detections = ruleEngine.detect(assetId, allResults);

            // Build human-readable summary
            String summary = buildSummary(allResults, detections, plugins);

            task.setStatus(CollectionTaskStatus.SUCCESS);
            task.setFinishedAt(LocalDateTime.now());
            task.setSuccess(true);
            task.setSummary(summary);
            task.setWritebackApplied(true);

        } catch (Exception e) {
            log.error("采集任务执行失败 assetId={}", assetId, e);
            task.setStatus(CollectionTaskStatus.FAILED);
            task.setFinishedAt(LocalDateTime.now());
            task.setSuccess(false);
            task.setErrorMessage(e.getMessage());
        }

        task = taskRepository.save(task);
        return new CollectionTaskResponse(task, resultRepository.countByTaskId(task.getId()));
    }

    private String buildSummary(List<ProbeResult> results,
                                 List<CategoryDetectionResult> detections,
                                 List<ProbePlugin> plugins) {
        long openPorts = results.stream()
                .filter(r -> r.getProbeType() == ProbeType.PORT_SCAN && r.isPortOpen()).count();
        List<Integer> openPortNums = results.stream()
                .filter(r -> r.getProbeType() == ProbeType.PORT_SCAN && r.isPortOpen() && r.getTargetPort() != null)
                .map(ProbeResult::getTargetPort).sorted().collect(Collectors.toList());

        long rtspOk  = results.stream().filter(r -> r.getProbeType() == ProbeType.RTSP_PROBE && r.isSuccess()).count();
        long onvifOk = results.stream().filter(r -> r.getProbeType() == ProbeType.ONVIF_PROBE && r.isSuccess()).count();
        long snmpOk  = results.stream().filter(r -> r.getProbeType() == ProbeType.SNMP_PROBE && r.isSuccess()).count();

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("插件 %d 个执行完毕。", plugins.size()));
        sb.append(String.format("开放端口 %d 个: %s。", openPorts, openPortNums));
        if (rtspOk  > 0) sb.append(String.format("RTSP 探测成功 %d 次。", rtspOk));
        if (onvifOk > 0) sb.append(String.format("ONVIF 探测成功 %d 次。", onvifOk));
        if (snmpOk  > 0) sb.append(String.format("SNMP 探测成功 %d 次。", snmpOk));
        if (!detections.isEmpty()) {
            String cats = detections.stream()
                    .map(d -> d.getCategory() + "(" + d.getConfidence() + ")")
                    .collect(Collectors.joining(", "));
            sb.append(String.format("规则引擎推断设备类别: %s。", cats));
        }
        return sb.toString().trim();
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
