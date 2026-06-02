package com.camera.app.poc.service;

import com.camera.app.asset.repository.AssetRepository;
import com.camera.app.common.exception.BusinessException;
import com.camera.app.poc.dto.ArtifactInfo;
import com.camera.app.poc.dto.ParamField;
import com.camera.app.poc.dto.PocAction;
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
import java.util.Map;
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
    private final PocActionSchemaBuilder actionSchemaBuilder;

    private record ExecCtx(List<String> args, TargetStrategy targetStrategy,
                           Integer finalPort, String usedTarget) {}

    // ─── Interface: getExecutionSchema ────────────────────────────────────────

    @Override
    public PocExecutionSchema getExecutionSchema(Long pocId) {
        Poc poc = findActivePoc(pocId);

        boolean executable = isPythonPoc(poc);
        String reason = executable ? null : notExecutableReason(poc);
        List<Integer> recommendedPorts = deriveRecommendedPorts(poc);

        // For Python POCs, try to read script content for action detection
        String scriptContent = null;
        if (executable && poc.getObjectKey() != null) {
            try (InputStream is = fileStorageService.download(poc.getObjectKey())) {
                scriptContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                log.warn("[Schema] Could not read script for action detection, pocId={}: {}", pocId, e.getMessage());
            }
        }

        List<PocAction> actions = actionSchemaBuilder.buildActions(poc, scriptContent);

        // v1 compat: derive paramSchemaByMode from action list
        Map<String, List<ParamField>> compatParamSchema = buildCompatParamSchema(actions);

        return PocExecutionSchema.builder()
                .pocId(pocId)
                .language(poc.getLanguage() != null ? poc.getLanguage().name() : "UNKNOWN")
                .executable(executable)
                .reason(reason)
                .schemaVersion(2)
                .defaultTimeoutSeconds(10)
                .supportedTargetStrategies(
                        List.of(TargetStrategy.EXPLICIT_PORT, TargetStrategy.RECOMMENDED_PORT_SCAN))
                .recommendedPorts(recommendedPorts)
                .supportsExplicitPort(true)
                .supportsAutoPortSuggestion(!recommendedPorts.isEmpty())
                .actions(actions)
                // v1 compat fields
                .modes(List.of(ExecutionMode.CHECK, ExecutionMode.EXPLOIT))
                .defaultMode(ExecutionMode.CHECK)
                .highRisk(false)
                .paramSchemaByMode(compatParamSchema)
                .build();
    }

    // ─── Interface: execute ───────────────────────────────────────────────────

    @Override
    public PocExecuteResponse execute(Long pocId, PocExecuteRequest request, String executedBy) {
        Poc poc = findActivePoc(pocId);

        if (!isPyFile(poc)) {
            return PocExecuteResponse.builder()
                    .pocId(pocId)
                    .executed(false)
                    .message("仅支持 .py 文件执行")
                    .build();
        }

        // Resolve action key: explicit actionKey > compat mode mapping > default CHECK_VULN
        String actionKey = resolveActionKey(request);
        validateActionParams(actionKey, request);

        ExecCtx ctx = buildExecCtx(request, poc, actionKey);

        String actionLabel = actionSchemaBuilder.getLabel(actionKey);
        String outputType  = actionSchemaBuilder.getOutputType(actionKey);
        ExecutionMode compatMode = toCompatMode(actionKey);

        // ── Dry-run: return resolved argv without running the script ──────────
        if (request.isDryRun()) {
            List<String> previewCmd = new ArrayList<>();
            previewCmd.add("python");
            previewCmd.add(poc.getOriginalFilename());
            previewCmd.addAll(ctx.args());
            log.info("[POC-DRYRUN] pocId={} actionKey={} strategy={} assetId={} port={} target={} params={} argv={}",
                    pocId, actionKey, ctx.targetStrategy(), request.getAssetId(),
                    ctx.finalPort(), ctx.usedTarget(), request.getParams(), previewCmd);
            return PocExecuteResponse.builder()
                    .pocId(pocId)
                    .executed(false)
                    .actionKey(actionKey)
                    .actionLabel(actionLabel)
                    .outputType(outputType)
                    .artifacts(List.of())
                    .mode(compatMode)
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

            List<String> debugCmd = new ArrayList<>();
            debugCmd.add("python");
            debugCmd.add(poc.getOriginalFilename());
            debugCmd.addAll(ctx.args());
            log.debug("[POC-EXEC] pocId={} actionKey={} strategy={} assetId={} port={} target={} params={} argv={}",
                    pocId, actionKey, ctx.targetStrategy(), request.getAssetId(),
                    ctx.finalPort(), ctx.usedTarget(), request.getParams(), debugCmd);

            PocExecuteResponse base = runScript(pocId, script, ctx.args(), request.getTimeoutSeconds(),
                    ctx.targetStrategy(), ctx.finalPort(), ctx.usedTarget());

            response = base.toBuilder()
                    .actionKey(actionKey)
                    .actionLabel(actionLabel)
                    .outputType(outputType)
                    .artifacts(List.of())
                    .mode(compatMode)
                    .build();
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

    // ─── Private: action key resolution ──────────────────────────────────────

    /**
     * Priority: explicit actionKey > compat mode mapping > default CHECK_VULN.
     * Mode compat: CHECK → CHECK_VULN, EXPLOIT → EXEC_COMMAND.
     */
    private String resolveActionKey(PocExecuteRequest request) {
        if (request.getActionKey() != null && !request.getActionKey().isBlank()) {
            return request.getActionKey().trim();
        }
        if (request.getMode() == ExecutionMode.EXPLOIT) {
            return "EXEC_COMMAND";
        }
        return "CHECK_VULN";
    }

    private void validateActionParams(String actionKey, PocExecuteRequest request) {
        if ("EXEC_COMMAND".equals(actionKey)) {
            String cmd = extractParamString(request.getParams(), "cmd");
            if (cmd == null || cmd.isBlank()) {
                throw new BusinessException(400, "动作 EXEC_COMMAND 需要 params.cmd");
            }
        }
    }

    /** Maps action key back to v1 ExecutionMode for compat fields. */
    private ExecutionMode toCompatMode(String actionKey) {
        return "EXEC_COMMAND".equals(actionKey) ? ExecutionMode.EXPLOIT : ExecutionMode.CHECK;
    }

    // ─── Private: context builder ─────────────────────────────────────────────

    private ExecCtx buildExecCtx(PocExecuteRequest request, Poc poc, String actionKey) {
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
                usedTarget = assetIp;
                urlMode = false;
            }
        }

        List<String> args = PocArgAssembler.assembleForAction(
                actionKey, usedTarget, urlMode, request.getArguments(), request.getParams());
        return new ExecCtx(args, strategy, finalPort, usedTarget);
    }

    // ─── Private: schema helpers ──────────────────────────────────────────────

    /** Used by getExecutionSchema: requires both language=PYTHON AND .py extension. */
    private boolean isPythonPoc(Poc poc) {
        return poc.getLanguage() == Language.PYTHON
                && poc.getOriginalFilename() != null
                && poc.getOriginalFilename().toLowerCase().endsWith(".py");
    }

    /** Used by execute: only requires .py extension (matches original behavior). */
    private boolean isPyFile(Poc poc) {
        return poc.getOriginalFilename() != null
                && poc.getOriginalFilename().toLowerCase().endsWith(".py");
    }

    private String notExecutableReason(Poc poc) {
        if (poc.getLanguage() != Language.PYTHON) return "仅支持 Python 脚本执行";
        return "文件扩展名不是 .py";
    }

    /** Builds v1-compat paramSchemaByMode from the action list. */
    private Map<String, List<ParamField>> buildCompatParamSchema(List<PocAction> actions) {
        List<ParamField> checkParams = actions.stream()
                .filter(a -> "CHECK_VULN".equals(a.getKey()))
                .map(PocAction::getParams)
                .findFirst()
                .orElse(List.of());
        List<ParamField> exploitParams = actions.stream()
                .filter(a -> "EXEC_COMMAND".equals(a.getKey()))
                .map(PocAction::getParams)
                .findFirst()
                .orElseGet(() -> List.of(ParamField.builder()
                        .name("cmd").label("命令").type("text")
                        .required(true).placeholder("请输入要执行的命令，如 whoami").build()));
        return Map.of("CHECK", checkParams, "EXPLOIT", exploitParams);
    }

    // ─── Private: port resolution ─────────────────────────────────────────────

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
                                          TargetStrategy targetStrategy,
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
                    .targetStrategy(targetStrategy)
                    .finalPort(finalPort).usedTarget(usedTarget)
                    .message("执行超时，已强制终止（超时 " + timeoutSeconds + " 秒）")
                    .build();
        }

        int exitCode = process.exitValue();
        return PocExecuteResponse.builder()
                .pocId(pocId).executed(true).success(exitCode == 0).exitCode(exitCode)
                .stdout(stdout).stderr(stderr).truncated(truncated)
                .startedAt(startedAt).finishedAt(finishedAt).durationMs(durationMs)
                .targetStrategy(targetStrategy)
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
            record.setActionKey(resp.getActionKey());
            record.setOutputType(resp.getOutputType());
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

    private String extractParamString(Map<String, Object> params, String key) {
        if (params == null) return null;
        Object v = params.get(key);
        return v == null ? null : v.toString();
    }

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
