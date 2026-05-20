package com.camera.app.collection.service;

import com.camera.app.collection.entity.ProbeType;
import com.camera.app.collection.entity.TaskPreset;
import com.camera.app.collection.plugin.*;
import com.camera.app.collection.rules.CategoryDetectionResult;
import com.camera.app.collection.rules.DeviceTypeRuleEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Async execution component for collection tasks.
 * Separated from AssetCollectionServiceImpl so that @Async + @Transactional
 * method calls go through the Spring AOP proxy correctly.
 *
 * State machine: PENDING → RUNNING (committed) → (plugins run) → SUCCESS / FAILED (committed)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CollectionTaskExecutor {

    private final ProbeRegistry probeRegistry;
    private final ProbeWritebackService writebackService;
    private final DeviceTypeRuleEngine ruleEngine;
    private final CollectionTaskStatusService statusService;

    @Async("probeTaskPool")
    public void executeAsync(Long assetId, Long taskId, String targetIp,
                              List<Integer> ports, int timeoutMs,
                              TaskPreset preset, List<String> enabledPluginNames) {

        log.info("采集任务开始 taskId={} assetId={} preset={}", taskId, assetId, preset);

        // Transition to RUNNING — committed in its own transaction so pollers see it
        statusService.markRunning(taskId);

        List<ProbePlugin> plugins = probeRegistry.resolvePlugins(preset, enabledPluginNames);
        if (plugins.isEmpty()) {
            log.warn("No plugins resolved for preset={}, falling back to port+http", preset);
            plugins = probeRegistry.resolvePlugins(TaskPreset.CUSTOM, List.of("port-probe", "http-fingerprint"));
        }

        ProbeContext ctx = new ProbeContext(assetId, taskId, targetIp, ports, timeoutMs, preset, Map.of());
        List<ProbeResult> allResults = new ArrayList<>();
        String firstPluginError = null;

        for (ProbePlugin plugin : plugins) {
            if (!plugin.supports(ctx)) continue;
            try {
                List<ProbeResult> results = plugin.execute(ctx);
                allResults.addAll(results);
                log.debug("Plugin {} produced {} results for task {}", plugin.getName(), results.size(), taskId);
            } catch (Exception e) {
                log.error("Plugin {} failed for task {}: {}", plugin.getName(), taskId, e.getMessage(), e);
                if (firstPluginError == null) {
                    firstPluginError = plugin.getName() + ": " + e.getMessage();
                }
            }
        }

        try {
            // writebackService and ruleEngine each manage their own @Transactional
            writebackService.processResults(assetId, taskId, allResults);
            List<CategoryDetectionResult> detections = ruleEngine.detect(assetId, allResults);
            String summary = buildSummary(allResults, detections, plugins);
            statusService.markSuccess(taskId, summary, firstPluginError);
            log.info("采集任务完成 taskId={} results={}", taskId, allResults.size());
        } catch (Exception e) {
            log.error("采集任务写回失败 taskId={}: {}", taskId, e.getMessage(), e);
            statusService.markFailed(taskId, e.getMessage());
        }
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
}
