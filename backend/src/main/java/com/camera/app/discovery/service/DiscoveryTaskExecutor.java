package com.camera.app.discovery.service;

import com.camera.app.discovery.engine.ActiveScanEngine;
import com.camera.app.discovery.engine.IpRangeExpander;
import com.camera.app.discovery.engine.PassiveSniffEngine;
import com.camera.app.discovery.engine.SniffHostResult;
import com.camera.app.discovery.entity.DiscoveryLevel;
import com.camera.app.discovery.entity.DiscoveryPreset;
import com.camera.app.discovery.entity.DiscoveryTaskType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Async executor for discovery tasks.
 * Separated from DiscoveryServiceImpl so @Async + @Transactional proxy works correctly.
 *
 * State machine: PENDING → RUNNING → SUCCESS / FAILED / CANCELED
 *
 * ACTIVE_SCAN dual-level flow:
 *   For every alive IP (ICMP or open TCP port), persistOneScanResult() is called
 *   immediately in its own transaction and returns DiscoveryLevel.
 *   aliveCount / candidateCount are updated atomically and reflected in DB on each
 *   discovery event and periodically for non-alive IPs.
 *   Cancellation is checked from DB every 20 non-alive IPs; stopSignal AtomicBoolean
 *   propagates to the engine's poll loop for fast shutdown.
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

        // PENDING → RUNNING; no-op if already CANCELED (stop called before task started)
        statusService.markRunning(taskId);

        if (statusService.isCanceled(taskId)) {
            log.info("发现任务在启动前已被取消 taskId={}", taskId);
            statusService.finalizeCanceled(taskId, 0, 0, "任务已停止（启动前取消）");
            return;
        }

        try {
            AtomicBoolean cancelSignal = new AtomicBoolean(false);
            int[] counts; // [aliveCount, candidateCount]

            if (taskType == DiscoveryTaskType.ACTIVE_SCAN) {
                counts = runActiveScan(taskId, targetScope, ports, timeoutMs, cancelSignal);
            } else {
                counts = runPassiveSniff(taskId, networkInterface, sniffDurationSec);
            }

            int aliveCount     = counts[0];
            int candidateCount = counts[1];

            // If canceled during execution, finalize the CANCELED status with accurate counts
            if (cancelSignal.get() || statusService.isCanceled(taskId)) {
                statusService.finalizeCanceled(taskId, aliveCount, candidateCount,
                        buildCanceledSummary(taskType, targetScope, aliveCount, candidateCount));
                log.info("发现任务已停止 taskId={} alive={} candidate={}", taskId, aliveCount, candidateCount);
                return;
            }

            statusService.markSuccess(taskId,
                    buildSuccessSummary(taskType, targetScope, aliveCount, candidateCount),
                    aliveCount, candidateCount);
            log.info("发现任务完成 taskId={} alive={} candidate={}", taskId, aliveCount, candidateCount);

        } catch (Exception e) {
            log.error("发现任务异常失败 taskId={}: {}", taskId, e.getMessage(), e);
            if (!statusService.isCanceled(taskId)) {
                statusService.markFailed(taskId, e.getMessage());
            }
        }
    }

    // ---- active scan with incremental persistence ----

    private int[] runActiveScan(Long taskId, String targetScope,
                                List<Integer> ports, int timeoutMs,
                                AtomicBoolean cancelSignal) {
        List<String> ips = IpRangeExpander.expand(targetScope);
        if (ips.isEmpty()) {
            log.warn("targetScope '{}' expanded to 0 IPs", targetScope);
            return new int[]{0, 0};
        }

        int total = ips.size();
        log.info("主动扫描 taskId={} total={} ports={} timeout={}ms", taskId, total, ports, timeoutMs);

        AtomicInteger scannedCount    = new AtomicInteger(0);
        AtomicInteger aliveCount      = new AtomicInteger(0);
        AtomicInteger candidateCount  = new AtomicInteger(0);

        // Initial progress marker so RUNNING doesn't show empty summary
        statusService.updateProgress(taskId, 0, 0,
                String.format("正在扫描 %s，共 %d 个目标 IP，存活 0 台，候选 0 台", targetScope, total));

        activeScanEngine.scan(ips, ports, timeoutMs, cancelSignal, result -> {
            int scanned = scannedCount.incrementAndGet();

            if (result.alive()) {
                DiscoveryLevel level;
                try {
                    level = persistService.persistOneScanResult(taskId, result);
                } catch (Exception e) {
                    log.warn("Persist failed ip={}: {}", result.ip(), e.getMessage());
                    return;
                }
                if (level == DiscoveryLevel.CANDIDATE) {
                    candidateCount.incrementAndGet();
                } else {
                    aliveCount.incrementAndGet();
                }
                int alive     = aliveCount.get();
                int candidate = candidateCount.get();
                statusService.updateProgress(taskId, alive, candidate,
                        String.format("正在扫描，已处理 %d/%d 个IP，存活 %d 台，候选 %d 台",
                                scanned, total, alive, candidate));
            } else {
                // For non-alive IPs: periodic progress update + cancel check every 20 IPs
                if (scanned % 20 == 0) {
                    int alive     = aliveCount.get();
                    int candidate = candidateCount.get();
                    statusService.updateProgress(taskId, alive, candidate,
                            String.format("正在扫描，已处理 %d/%d 个IP，存活 %d 台，候选 %d 台",
                                    scanned, total, alive, candidate));
                    // Check DB for external cancellation signal
                    if (statusService.isCanceled(taskId)) {
                        cancelSignal.set(true);
                    }
                }
            }
        });

        return new int[]{aliveCount.get(), candidateCount.get()};
    }

    // ---- passive sniff ----

    private int[] runPassiveSniff(Long taskId, String networkInterface, int durationSec) {
        log.info("被动嗅探 taskId={} iface={} duration={}s", taskId, networkInterface, durationSec);
        statusService.updateProgress(taskId, 0, 0,
                String.format("被动嗅探中，监听 %s，持续 %d 秒…",
                        networkInterface != null ? networkInterface : "默认网卡", durationSec));
        List<SniffHostResult> results = passiveSniffEngine.sniff(networkInterface, durationSec);
        int candidateCount = persistService.persistSniffResults(taskId, results);
        // All sniff results are CANDIDATE
        return new int[]{0, candidateCount};
    }

    // ---- summary builders ----

    private String buildSuccessSummary(DiscoveryTaskType type, String scope,
                                       int aliveCount, int candidateCount) {
        if (type == DiscoveryTaskType.ACTIVE_SCAN) {
            return String.format("主动扫描完成，扫描目标: %s，存活主机 %d 台，候选设备 %d 台",
                    scope, aliveCount, candidateCount);
        }
        return String.format("被动嗅探完成，候选设备 %d 台（ARP/SSDP/WS-Discovery/mDNS）", candidateCount);
    }

    private String buildCanceledSummary(DiscoveryTaskType type, String scope,
                                        int aliveCount, int candidateCount) {
        if (type == DiscoveryTaskType.ACTIVE_SCAN) {
            return String.format("主动扫描已停止（用户取消），扫描目标: %s，存活主机 %d 台，候选设备 %d 台",
                    scope, aliveCount, candidateCount);
        }
        return String.format("被动嗅探已停止（用户取消），候选设备 %d 台", candidateCount);
    }
}
