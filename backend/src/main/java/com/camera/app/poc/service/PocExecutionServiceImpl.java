package com.camera.app.poc.service;

import com.camera.app.alert.service.AlertGenerationService;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
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
    private final ObjectMapper objectMapper;
    private final AlertGenerationService alertGenerationService;

    private record ExecCtx(List<String> args, TargetStrategy targetStrategy,
                           Integer finalPort, String usedTarget) {}

    // ─── Interface: getExecutionSchema ────────────────────────────────────────

    @Override
    public PocExecutionSchema getExecutionSchema(Long pocId) {
        Poc poc = findActivePoc(pocId);

        boolean executable = isPythonPoc(poc);
        String reason = executable ? null : notExecutableReason(poc);
        List<Integer> recommendedPorts = deriveRecommendedPorts(poc);

        String scriptContent = null;
        if (executable && poc.getObjectKey() != null) {
            try (InputStream is = fileStorageService.download(poc.getObjectKey())) {
                scriptContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                log.warn("[Schema] Could not read script for action detection, pocId={}: {}", pocId, e.getMessage());
            }
        }

        List<PocAction> actions = actionSchemaBuilder.buildActions(poc, scriptContent);
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

        String actionKey = resolveActionKey(request);
        validateActionParams(actionKey, request);

        // For FETCH_SNAPSHOT / DOWNLOAD_CONFIG: auto-fill outputFile if not provided
        Map<String, Object> effectiveParams = prepareParams(actionKey, poc, request.getParams());
        String outputFileName = StandardActions.supportsOutputFile(actionKey)
                ? extractParamString(effectiveParams, "outputFile") : null;

        ExecCtx ctx = buildExecCtx(request, poc, actionKey, effectiveParams);

        String actionLabel = StandardActions.label(actionKey);
        String outputType  = StandardActions.outputType(actionKey);
        ExecutionMode compatMode = toCompatMode(actionKey);

        // ── Dry-run ──────────────────────────────────────────────────────────
        if (request.isDryRun()) {
            List<String> previewCmd = new ArrayList<>();
            previewCmd.add("python");
            previewCmd.add(poc.getOriginalFilename());
            previewCmd.addAll(ctx.args());
            log.info("[POC-DRYRUN] pocId={} actionKey={} strategy={} assetId={} port={} target={} params={} argv={}",
                    pocId, actionKey, ctx.targetStrategy(), request.getAssetId(),
                    ctx.finalPort(), ctx.usedTarget(), effectiveParams, previewCmd);
            return PocExecuteResponse.builder()
                    .pocId(pocId).executed(false)
                    .actionKey(actionKey).actionLabel(actionLabel).outputType(outputType)
                    .artifacts(List.of()).mode(compatMode)
                    .targetStrategy(ctx.targetStrategy())
                    .finalPort(ctx.finalPort()).usedTarget(ctx.usedTarget())
                    .message("DRY-RUN: argv=" + previewCmd)
                    .build();
        }

        Path tempDir = null;
        List<ArtifactInfo> artifacts = new ArrayList<>();
        Object parsedOutput = null;
        PocExecuteResponse baseResponse;

        try {
            tempDir = Files.createTempDirectory("poc-exec-");
            Path script = tempDir.resolve(poc.getOriginalFilename());
            try (InputStream is = fileStorageService.download(poc.getObjectKey())) {
                Files.copy(is, script);
            }

            log.debug("[POC-EXEC] pocId={} actionKey={} strategy={} assetId={} port={} target={} params={} argv={}",
                    pocId, actionKey, ctx.targetStrategy(), request.getAssetId(),
                    ctx.finalPort(), ctx.usedTarget(), effectiveParams,
                    buildDebugCmd(poc.getOriginalFilename(), ctx.args()));

            baseResponse = runScript(pocId, script, ctx.args(), request.getTimeoutSeconds(),
                    ctx.targetStrategy(), ctx.finalPort(), ctx.usedTarget());

            // Collect file artifact from tempDir — must happen before deleteQuietly
            if (baseResponse.isExecuted() && Boolean.TRUE.equals(baseResponse.getSuccess())
                    && outputFileName != null) {
                collectArtifact(artifacts, tempDir, pocId, actionKey, outputFileName);
            }

            // Parse stdout as JSON for JSON-output actions
            if (baseResponse.isExecuted() && "JSON".equals(outputType)) {
                parsedOutput = tryParseJson(baseResponse.getStdout());
            }

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("POC execution setup failed, id={}", pocId, e);
            throw new BusinessException(500, "执行准备失败: " + e.getMessage());
        } finally {
            deleteQuietly(tempDir);
        }

        PocExecuteResponse response = baseResponse.toBuilder()
                .actionKey(actionKey)
                .actionLabel(actionLabel)
                .outputType(outputType)
                .artifacts(artifacts)
                .parsedOutput(parsedOutput)
                .mode(compatMode)
                .build();

        // FETCH_SNAPSHOT-specific guard: success but no IMAGE artifact is a warning state
        if ("FETCH_SNAPSHOT".equals(actionKey) && Boolean.TRUE.equals(response.getSuccess())
                && artifacts.isEmpty()) {
            log.warn("[POC-ARTIFACT] FETCH_SNAPSHOT succeeded (pocId={}) but produced no IMAGE artifact — "
                    + "script may not have written the output file or used a different filename", pocId);
            response = response.toBuilder()
                    .message("快照动作成功执行，但未生成图片附件（脚本未写出文件或文件名与预期不符：" + outputFileName + "）")
                    .build();
        }

        Long executionId = saveExecutionLog(pocId, request.getAssetId(), executedBy, response);

        // Auto-generate alert if execution succeeded (non-blocking — failures are swallowed)
        try {
            alertGenerationService.generateFromPocExecution(
                    pocId, request.getAssetId(), actionKey,
                    Boolean.TRUE.equals(response.getSuccess()), executionId);
        } catch (Exception e) {
            log.warn("[AlertGen] POC alert generation failed pocId={} action={}: {}", pocId, actionKey, e.getMessage());
        }

        // Enrich artifact URLs with executionId (known only after log is saved)
        if (executionId != null && !artifacts.isEmpty()) {
            List<ArtifactInfo> enriched = artifacts.stream()
                    .map(a -> enrichArtifactUrl(a, executionId))
                    .toList();
            return response.toBuilder().executionId(executionId).artifacts(enriched).build();
        }
        return response.toBuilder().executionId(executionId).build();
    }

    // ─── Private: action key resolution ──────────────────────────────────────

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

    private ExecutionMode toCompatMode(String actionKey) {
        return "EXEC_COMMAND".equals(actionKey) ? ExecutionMode.EXPLOIT : ExecutionMode.CHECK;
    }

    // ─── Private: params preparation ─────────────────────────────────────────

    /**
     * For artifact-producing actions (FETCH_SNAPSHOT / DOWNLOAD_CONFIG):
     * auto-generates outputFile if caller did not supply one.
     */
    private Map<String, Object> prepareParams(String actionKey, Poc poc, Map<String, Object> requestParams) {
        if (!StandardActions.supportsOutputFile(actionKey)) {
            return requestParams != null ? requestParams : Map.of();
        }
        Map<String, Object> params = new HashMap<>(requestParams != null ? requestParams : Map.of());
        String of = extractParamString(params, "outputFile");
        if (of == null || of.isBlank()) {
            String ts = String.valueOf(System.currentTimeMillis());
            String ext = "FETCH_SNAPSHOT".equals(actionKey) ? ".jpg" : ".dat";
            params.put("outputFile", actionKey.toLowerCase() + "_poc" + poc.getId() + "_" + ts + ext);
        }
        return params;
    }

    // ─── Private: context builder ─────────────────────────────────────────────

    private ExecCtx buildExecCtx(PocExecuteRequest request, Poc poc, String actionKey,
                                  Map<String, Object> effectiveParams) {
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
                actionKey, usedTarget, urlMode, request.getArguments(), effectiveParams);
        return new ExecCtx(args, strategy, finalPort, usedTarget);
    }

    // ─── Private: artifact collection ────────────────────────────────────────

    /**
     * After the script exits successfully, looks for outputFileName in tempDir.
     * If found, uploads to MinIO and appends ArtifactInfo to the artifacts list.
     */
    private void collectArtifact(List<ArtifactInfo> artifacts, Path tempDir, Long pocId,
                                  String actionKey, String outputFileName) {
        Path artifactFile = tempDir.resolve(outputFileName);
        if (!Files.exists(artifactFile)) {
            log.warn("[POC-ARTIFACT] Expected output file not found: {} (pocId={})", outputFileName, pocId);
            return;
        }
        try {
            long size = Files.size(artifactFile);
            String mimeType = resolveMimeType(outputFileName, actionKey);
            String objectKey = "artifacts/poc-" + pocId + "/" + outputFileName;

            try (InputStream is = Files.newInputStream(artifactFile)) {
                fileStorageService.upload(objectKey, is, size, mimeType);
            }

            String artifactType = mimeType.startsWith("image/") ? "IMAGE" : "FILE";
            artifacts.add(ArtifactInfo.builder()
                    .name(outputFileName)
                    .type(artifactType)
                    .mimeType(mimeType)
                    .sizeBytes(size)
                    .objectKey(objectKey)
                    .path(objectKey)
                    .previewable("IMAGE".equals(artifactType))
                    .downloadable(true)
                    .build());

            log.info("[POC-ARTIFACT] Uploaded artifact: {} ({} bytes, pocId={})", objectKey, size, pocId);
        } catch (Exception e) {
            log.warn("[POC-ARTIFACT] Failed to collect artifact {} for pocId={}: {}",
                    outputFileName, pocId, e.getMessage());
        }
    }

    private String resolveMimeType(String filename, String actionKey) {
        // FETCH_SNAPSHOT always produces a JPEG
        if ("FETCH_SNAPSHOT".equals(actionKey)) return "image/jpeg";
        String lower = filename.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png"))  return "image/png";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".xml"))  return "application/xml";
        if (lower.endsWith(".txt"))  return "text/plain";
        return "application/octet-stream";
    }

    /**
     * Enrich an ArtifactInfo with downloadUrl/previewUrl once executionId is known.
     * previewUrl → /preview (inline, browser-renderable, supports ?token= for <img src>)
     * downloadUrl → /download (attachment, forces Save dialog, supports ?token= for direct links)
     */
    private ArtifactInfo enrichArtifactUrl(ArtifactInfo a, Long executionId) {
        String base = "/api/v1/poc-executions/" + executionId
                + "/artifacts/" + URLEncoder.encode(a.getName(), StandardCharsets.UTF_8);
        return a.toBuilder()
                .downloadUrl(base + "/download")
                .previewUrl("IMAGE".equals(a.getType()) ? base + "/preview" : null)
                .build();
    }

    // ─── Private: JSON output parsing ────────────────────────────────────────

    private Object tryParseJson(String stdout) {
        if (stdout == null || stdout.isBlank()) return null;
        String trimmed = stdout.trim();
        // Find the first JSON object '{' or array '[' in stdout
        int objIdx = trimmed.indexOf('{');
        int arrIdx = trimmed.indexOf('[');
        int start = -1;
        if (objIdx >= 0 && arrIdx >= 0) start = Math.min(objIdx, arrIdx);
        else if (objIdx >= 0) start = objIdx;
        else if (arrIdx >= 0) start = arrIdx;
        if (start < 0) return null;
        try {
            return objectMapper.readValue(trimmed.substring(start), Object.class);
        } catch (Exception e) {
            log.debug("[POC-JSON] Could not parse stdout as JSON: {}", e.getMessage());
            return null;
        }
    }

    // ─── Private: schema helpers ──────────────────────────────────────────────

    private boolean isPythonPoc(Poc poc) {
        return poc.getLanguage() == Language.PYTHON
                && poc.getOriginalFilename() != null
                && poc.getOriginalFilename().toLowerCase().endsWith(".py");
    }

    private boolean isPyFile(Poc poc) {
        return poc.getOriginalFilename() != null
                && poc.getOriginalFilename().toLowerCase().endsWith(".py");
    }

    private String notExecutableReason(Poc poc) {
        if (poc.getLanguage() != Language.PYTHON) return "仅支持 Python 脚本执行";
        return "文件扩展名不是 .py";
    }

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
            // Persist artifact metadata (objectKey preserved; URLs are runtime-derived)
            if (resp.getArtifacts() != null && !resp.getArtifacts().isEmpty()) {
                record.setArtifactSummary(serializeArtifacts(resp.getArtifacts()));
            }
            return pocExecutionLogService.save(record).getId();
        } catch (Exception e) {
            log.warn("Failed to persist execution log for poc={}", pocId, e);
            return null;
        }
    }

    private String serializeArtifacts(List<ArtifactInfo> artifacts) {
        try {
            List<Map<String, Object>> summary = artifacts.stream().map(a -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", a.getName());
                m.put("type", a.getType());
                m.put("mimeType", a.getMimeType());
                m.put("sizeBytes", a.getSizeBytes());
                m.put("objectKey", a.getObjectKey());
                return m;
            }).toList();
            return objectMapper.writeValueAsString(summary);
        } catch (Exception e) {
            log.warn("Failed to serialize artifact summary: {}", e.getMessage());
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

    private List<String> buildDebugCmd(String filename, List<String> args) {
        List<String> cmd = new ArrayList<>();
        cmd.add("python");
        cmd.add(filename);
        cmd.addAll(args);
        return cmd;
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
