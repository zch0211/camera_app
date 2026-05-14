package com.camera.app.poc.service;

import com.camera.app.asset.repository.AssetRepository;
import com.camera.app.common.exception.BusinessException;
import com.camera.app.poc.dto.PocExecuteRequest;
import com.camera.app.poc.dto.PocExecuteResponse;
import com.camera.app.poc.dto.PocExecutionSchema;
import com.camera.app.poc.entity.*;
import com.camera.app.poc.repository.PocRepository;
import com.camera.app.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class PocExecutionServiceImpl implements PocExecutionService {

    private static final int MAX_OUTPUT_BYTES = 64 * 1024;

    private final PocRepository pocRepository;
    private final FileStorageService fileStorageService;
    private final AssetRepository assetRepository;
    private final PocExecutionLogService pocExecutionLogService;

    private record ExecCtx(List<String> args, TargetStrategy targetStrategy,
                           Integer finalPort, String usedTarget) {}

    // ─── Interface: getExecutionSchema ────────────────────────────────────────

    @Override
    public PocExecutionSchema getExecutionSchema(Long pocId) {
        Poc poc = findActivePoc(pocId);

        boolean executable = poc.getLanguage() == Language.PYTHON
                && poc.getOriginalFilename().toLowerCase().endsWith(".py");
        String reason = null;
        if (!executable) {
            reason = poc.getLanguage() != Language.PYTHON
                    ? "仅支持 Python 脚本执行" : "文件扩展名不是 .py";
        }

        List<Integer> recommendedPorts = deriveRecommendedPorts(poc);

        return PocExecutionSchema.builder()
                .pocId(pocId)
                .language(poc.getLanguage() != null ? poc.getLanguage().name() : "UNKNOWN")
                .executable(executable)
                .reason(reason)
                .modes(List.of(ExecutionMode.CHECK, ExecutionMode.EXPLOIT))
                .defaultMode(ExecutionMode.CHECK)
                .defaultTimeoutSeconds(10)
                .supportedTargetStrategies(
                        List.of(TargetStrategy.EXPLICIT_PORT, TargetStrategy.RECOMMENDED_PORT_SCAN))
                .recommendedPorts(recommendedPorts)
                .supportsExplicitPort(true)
                .supportsAutoPortSuggestion(!recommendedPorts.isEmpty())
                .highRisk(false)
                .schemaVersion(1)
                .paramSchema(List.of())
                .build();
    }

    // ─── Interface: execute ───────────────────────────────────────────────────

    @Override
    public PocExecuteResponse execute(Long pocId, PocExecuteRequest request, String executedBy) {
        Poc poc = findActivePoc(pocId);

        if (!poc.getOriginalFilename().toLowerCase().endsWith(".py")) {
            return PocExecuteResponse.builder()
                    .pocId(pocId)
                    .executed(false)
                    .mode(request.getMode())
                    .message("仅支持 .py 文件执行")
                    .build();
        }

        ExecutionMode mode = request.getMode() != null ? request.getMode() : ExecutionMode.CHECK;
        ExecCtx ctx = buildExecCtx(request, poc, mode);

        // ── Dry-run: return resolved argv without running the script ──────────
        if (request.isDryRun()) {
            List<String> previewCmd = new ArrayList<>();
            previewCmd.add("python");
            previewCmd.add(poc.getOriginalFilename());
            previewCmd.addAll(ctx.args());
            log.info("[POC-DRYRUN] pocId={} mode={} strategy={} assetId={} port={} target={} argv={}",
                    pocId, mode, ctx.targetStrategy(), request.getAssetId(),
                    ctx.finalPort(), ctx.usedTarget(), previewCmd);
            return PocExecuteResponse.builder()
                    .pocId(pocId)
                    .executed(false)
                    .mode(mode)
                    .targetStrategy(ctx.targetStrategy())
                    .finalPort(ctx.finalPort())
                    .usedTarget(ctx.usedTarget())
                    .message("DRY-RUN: argv=" + previewCmd)
                    .build();
        }

        Path tempDir = null;
        PocExecuteResponse response;
        try {
            tempDir = Files.createTempDirectory("poc-exec-");
            Path script = tempDir.resolve(poc.getOriginalFilename());
            try (InputStream is = fileStorageService.download(poc.getObjectKey())) {
                Files.copy(is, script);
            }

            // Debug log showing the exact argv passed to the subprocess
            List<String> debugCmd = new ArrayList<>();
            debugCmd.add("python");
            debugCmd.add(poc.getOriginalFilename());
            debugCmd.addAll(ctx.args());
            log.debug("[POC-EXEC] pocId={} mode={} strategy={} assetId={} port={} target={} argv={}",
                    pocId, mode, ctx.targetStrategy(), request.getAssetId(),
                    ctx.finalPort(), ctx.usedTarget(), debugCmd);

            response = runScript(pocId, script, ctx.args(), request.getTimeoutSeconds(),
                    mode, ctx.targetStrategy(), ctx.finalPort(), ctx.usedTarget());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("POC execution setup failed, id={}", pocId, e);
            throw new BusinessException(500, "执行准备失败: " + e.getMessage());
        } finally {
            deleteQuietly(tempDir);
        }

        Long executionId = saveExecutionLog(pocId, request.getAssetId(), executedBy, response);
        return response.toBuilder().executionId(executionId).build();
    }

    // ─── Private: context builder ─────────────────────────────────────────────

    /**
     * Resolves target IP/port/strategy from the request, then delegates to
     * {@link PocArgAssembler#assemble} for the actual mode→flag mapping.
     * This keeps target-resolution logic separate from mode→argv logic.
     */
    private ExecCtx buildExecCtx(PocExecuteRequest request, Poc poc, ExecutionMode mode) {
        TargetStrategy strategy = null;
        Integer finalPort = null;
        String usedTarget = null;
        boolean urlMode = false;

        if (request.getAssetId() != null) {
            String assetIp = assetRepository.findById(request.getAssetId())
                    .orElseThrow(() -> new BusinessException(404, "资产不存在，id=" + request.getAssetId()))
                    .getIp();

            strategy = request.getTargetStrategy();
            Integer effectivePort;

            if (strategy == TargetStrategy.RECOMMENDED_PORT_SCAN) {
                List<Integer> recommended = deriveRecommendedPorts(poc);
                effectivePort = scanForPort(assetIp, recommended);
                if (effectivePort == null && !recommended.isEmpty()) {
                    effectivePort = recommended.get(0);
                    log.debug("No reachable port found, falling back to {}", effectivePort);
                }
            } else {
                // EXPLICIT_PORT or legacy: new `port` field takes precedence over `assetPort`
                effectivePort = request.getPort() != null ? request.getPort() : request.getAssetPort();
                if (effectivePort != null) {
                    strategy = TargetStrategy.EXPLICIT_PORT;
                }
            }

            if (effectivePort != null) {
                finalPort = effectivePort;
                usedTarget = "http://" + assetIp + ":" + effectivePort;
                urlMode = true;
            } else {
                usedTarget = assetIp;  // legacy positional-arg mode
                urlMode = false;
            }
        }

        // Delegate mode→flag mapping to PocArgAssembler (single responsibility)
        List<String> args = PocArgAssembler.assemble(mode, usedTarget, urlMode, request.getArguments());
        return new ExecCtx(args, strategy, finalPort, usedTarget);
    }

    // ─── Private: port scanning ───────────────────────────────────────────────

    private Integer scanForPort(String ip, List<Integer> ports) {
        for (Integer port : ports) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(ip, port), 1000);
                log.debug("Port scan hit: {}:{}", ip, port);
                return port;
            } catch (Exception ignored) {
                log.debug("Port scan miss: {}:{}", ip, port);
            }
        }
        return null;
    }

    private List<Integer> deriveRecommendedPorts(Poc poc) {
        if (poc.getProtocol() != null) {
            return switch (poc.getProtocol()) {
                case HTTP  -> List.of(80, 8080, 8000, 8888);
                case HTTPS -> List.of(443, 8443);
                case RTSP  -> List.of(554, 8554);
                case ONVIF -> List.of(80, 8080);
                case TCP   -> List.of(80, 443, 8080);
                case UDP   -> List.of();
                default    -> List.of(80, 8080);
            };
        }
        return switch (poc.getTargetType()) {
            case CAMERA   -> List.of(80, 8080, 443, 8443, 554);
            case NVR      -> List.of(80, 8080, 443, 8000, 8088);
            case ROUTER   -> List.of(80, 8080, 443);
            case PLATFORM -> List.of(80, 443, 8080, 8443);
            default       -> List.of(80, 8080);
        };
    }

    // ─── Private: script runner ───────────────────────────────────────────────

    private PocExecuteResponse runScript(Long pocId, Path script, List<String> args, int timeoutSeconds,
                                          ExecutionMode mode, TargetStrategy targetStrategy,
                                          Integer finalPort, String usedTarget) {
        List<String> cmd = new ArrayList<>();
        cmd.add("python");
        cmd.add(script.toAbsolutePath().toString());
        cmd.addAll(args);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(script.getParent().toFile());

        LocalDateTime startedAt = LocalDateTime.now();
        long startNano = System.nanoTime();

        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            throw new BusinessException(500, "启动进程失败: " + e.getMessage());
        }

        AtomicBoolean stdoutTruncated = new AtomicBoolean(false);
        AtomicBoolean stderrTruncated = new AtomicBoolean(false);
        byte[][] stdoutHolder = {null};
        byte[][] stderrHolder = {null};

        Thread outThread = drainThread(process.getInputStream(), stdoutHolder, 0, stdoutTruncated);
        Thread errThread = drainThread(process.getErrorStream(), stderrHolder, 0, stderrTruncated);
        outThread.start();
        errThread.start();

        boolean finished;
        try {
            finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new BusinessException(500, "执行被中断");
        }

        if (!finished) {
            process.destroyForcibly();
        }

        joinQuietly(outThread);
        joinQuietly(errThread);

        LocalDateTime finishedAt = LocalDateTime.now();
        long durationMs = (System.nanoTime() - startNano) / 1_000_000;
        String stdout = decode(stdoutHolder[0]);
        String stderr = decode(stderrHolder[0]);
        boolean truncated = stdoutTruncated.get() || stderrTruncated.get();

        if (!finished) {
            return PocExecuteResponse.builder()
                    .pocId(pocId).executed(true).success(false)
                    .stdout(stdout).stderr(stderr).truncated(truncated)
                    .startedAt(startedAt).finishedAt(finishedAt).durationMs(durationMs)
                    .mode(mode).targetStrategy(targetStrategy)
                    .finalPort(finalPort).usedTarget(usedTarget)
                    .message("执行超时，已强制终止（超时 " + timeoutSeconds + " 秒）")
                    .build();
        }

        int exitCode = process.exitValue();
        return PocExecuteResponse.builder()
                .pocId(pocId).executed(true).success(exitCode == 0).exitCode(exitCode)
                .stdout(stdout).stderr(stderr).truncated(truncated)
                .startedAt(startedAt).finishedAt(finishedAt).durationMs(durationMs)
                .mode(mode).targetStrategy(targetStrategy)
                .finalPort(finalPort).usedTarget(usedTarget)
                .build();
    }

    // ─── Private: log persistence ─────────────────────────────────────────────

    private Long saveExecutionLog(Long pocId, Long assetId, String executedBy, PocExecuteResponse resp) {
        try {
            PocExecutionLog record = new PocExecutionLog();
            record.setPocId(pocId);
            record.setAssetId(assetId);
            record.setExecutedBy(executedBy);
            record.setMode(resp.getMode());
            record.setTargetStrategy(resp.getTargetStrategy());
            record.setFinalPort(resp.getFinalPort());
            record.setUsedTarget(resp.getUsedTarget());
            record.setSuccess(resp.getSuccess());
            record.setExitCode(resp.getExitCode());
            record.setStdout(resp.getStdout());
            record.setStderr(resp.getStderr());
            record.setTruncated(resp.isTruncated());
            record.setStartedAt(resp.getStartedAt());
            record.setFinishedAt(resp.getFinishedAt());
            record.setDurationMs(resp.getDurationMs());
            return pocExecutionLogService.save(record).getId();
        } catch (Exception e) {
            log.warn("Failed to persist execution log for poc={}", pocId, e);
            return null;
        }
    }

    // ─── Private: subprocess utilities ───────────────────────────────────────

    private Poc findActivePoc(Long id) {
        Poc poc = pocRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "POC 不存在，id=" + id));
        if (poc.getStatus() == PocStatus.DELETED) {
            throw new BusinessException(404, "POC 不存在，id=" + id);
        }
        return poc;
    }

    /**
     * Reads up to MAX_OUTPUT_BYTES into holder[index], then drains the remainder
     * without storing it so the subprocess never blocks on a full pipe buffer.
     */
    private Thread drainThread(InputStream is, byte[][] holder, int index, AtomicBoolean truncatedFlag) {
        return new Thread(() -> {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int totalRead = 0;
            int n;
            try {
                while ((n = is.read(chunk)) != -1) {
                    if (totalRead < MAX_OUTPUT_BYTES) {
                        int toStore = Math.min(n, MAX_OUTPUT_BYTES - totalRead);
                        buf.write(chunk, 0, toStore);
                    } else {
                        truncatedFlag.set(true);
                    }
                    totalRead += n;
                }
            } catch (IOException ignored) {}
            holder[index] = buf.toByteArray();
        }, "poc-drain-" + index);
    }

    private String decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "";
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private void joinQuietly(Thread t) {
        try { t.join(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private void deleteQuietly(Path dir) {
        if (dir == null) return;
        try {
            Files.walk(dir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
        } catch (IOException e) {
            log.warn("Failed to clean up temp dir: {}", dir, e);
        }
    }
}
