package com.camera.app.discovery.service;

import com.camera.app.discovery.engine.ActiveScanEngine;
import com.camera.app.discovery.engine.IpRangeExpander;
import com.camera.app.discovery.engine.PassiveSniffEngine;
import com.camera.app.discovery.engine.ScanHostResult;
import com.camera.app.discovery.engine.SniffHostResult;
import com.camera.app.discovery.entity.DiscoveryPreset;
import com.camera.app.discovery.entity.DiscoveryTaskType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Async executor for discovery tasks.
 * Separated from DiscoveryServiceImpl so @Async + @Transactional proxy works correctly.
 *
 * State machine: PENDING → RUNNING → SUCCESS / FAILED
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiscoveryTaskExecutor {

    private final ActiveScanEngine activeScanEngine;
    private final PassiveSniffEngine passiveSniffEngine;
    private final DiscoveryResultPersistService persistService;
    private final DiscoveryTaskStatusService statusService;

    @Async("discoveryTaskPool")
    public void executeAsync(Long taskId, DiscoveryTaskType taskType, DiscoveryPreset preset,
                             String targetScope, List<Integer> ports, int timeoutMs,
                             int sniffDurationSec, String networkInterface) {

        log.info("发现任务开始 taskId={} type={} scope={}", taskId, taskType, targetScope);
        statusService.markRunning(taskId);

        try {
            int count;
            if (taskType == DiscoveryTaskType.ACTIVE_SCAN) {
                count = runActiveScan(taskId, preset, targetScope, ports, timeoutMs);
            } else {
                count = runPassiveSniff(taskId, networkInterface, sniffDurationSec);
            }
            String summary = buildSummary(taskType, targetScope, count);
            statusService.markSuccess(taskId, summary, count);
            log.info("发现任务完成 taskId={} discovered={}", taskId, count);
        } catch (Exception e) {
            log.error("发现任务失败 taskId={}: {}", taskId, e.getMessage(), e);
            statusService.markFailed(taskId, e.getMessage());
        }
    }

    private int runActiveScan(Long taskId, DiscoveryPreset preset, String targetScope,
                              List<Integer> ports, int timeoutMs) {
        List<String> ips = IpRangeExpander.expand(targetScope);
        if (ips.isEmpty()) {
            log.warn("targetScope '{}' expanded to 0 IPs", targetScope);
            return 0;
        }
        log.info("主动扫描 taskId={} ips={} ports={}", taskId, ips.size(), ports.size());
        List<ScanHostResult> results = activeScanEngine.scan(ips, ports, timeoutMs);
        return persistService.persistScanResults(taskId, results);
    }

    private int runPassiveSniff(Long taskId, String networkInterface, int durationSec) {
        log.info("被动嗅探 taskId={} iface={} duration={}s", taskId, networkInterface, durationSec);
        List<SniffHostResult> results = passiveSniffEngine.sniff(networkInterface, durationSec);
        return persistService.persistSniffResults(taskId, results);
    }

    private String buildSummary(DiscoveryTaskType type, String scope, int count) {
        if (type == DiscoveryTaskType.ACTIVE_SCAN) {
            return String.format("主动扫描完成，扫描目标: %s，发现存活设备: %d 台", scope, count);
        } else {
            return String.format("被动嗅探完成，发现设备: %d 台（ARP/SSDP/WS-Discovery/mDNS）", count);
        }
    }
}
