package com.camera.app.poc.service;

import com.camera.app.asset.repository.AssetRepository;
import com.camera.app.common.exception.BusinessException;
import com.camera.app.poc.dto.PocExecuteRequest;
import com.camera.app.poc.dto.PocExecuteResponse;
import com.camera.app.poc.entity.Poc;
import com.camera.app.poc.entity.PocStatus;
import com.camera.app.poc.repository.PocRepository;
import com.camera.app.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
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

    /** Each of stdout / stderr is capped at 64 KB before being returned. */
    private static final int MAX_OUTPUT_BYTES = 64 * 1024;

    private final PocRepository pocRepository;
    private final FileStorageService fileStorageService;
    private final AssetRepository assetRepository;

    @Override
    @Transactional(readOnly = true)
    public PocExecuteResponse execute(Long pocId, PocExecuteRequest request) {
        Poc poc = findActivePoc(pocId);

        if (!poc.getOriginalFilename().toLowerCase().endsWith(".py")) {
            return PocExecuteResponse.builder()
                    .pocId(pocId)
                    .executed(false)
                    .message("only .py files are supported for execution")
                    .build();
        }

        // Build argument list: optional asset IP first, then caller-supplied args
        List<String> args = buildArguments(request);

        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("poc-exec-");
            Path script = tempDir.resolve(poc.getOriginalFilename());
            try (InputStream is = fileStorageService.download(poc.getObjectKey())) {
                Files.copy(is, script);
            }
            return runScript(pocId, script, args, request.getTimeoutSeconds());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("POC execution setup failed, id={}", pocId, e);
            throw new BusinessException(500, "执行准备失败: " + e.getMessage());
        } finally {
            deleteQuietly(tempDir);
        }
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private List<String> buildArguments(PocExecuteRequest request) {
        List<String> args = new ArrayList<>();

        if (request.getAssetId() != null) {
            String assetIp = assetRepository.findById(request.getAssetId())
                    .orElseThrow(() -> new BusinessException(404, "资产不存在，id=" + request.getAssetId()))
                    .getIp();
            if (request.getAssetPort() != null) {
                // argparse -u URL 型 POC，如 CVE-2021-36260
                args.add("-u");
                args.add("http://" + assetIp + ":" + request.getAssetPort());
            } else {
                // 位置参数型 POC，直接注入裸 IP
                args.add(assetIp);
            }
        }

        if (request.getArguments() != null) {
            for (String arg : request.getArguments()) {
                if (arg != null && !arg.contains("\u0000")) {   // reject null-byte injection
                    args.add(arg);
                }
            }
        }
        return args;
    }

    private PocExecuteResponse runScript(Long pocId, Path script, List<String> args, int timeoutSeconds) {
        List<String> cmd = new ArrayList<>();
        cmd.add("python");
        cmd.add(script.toAbsolutePath().toString());
        cmd.addAll(args);

        // ProcessBuilder with explicit array — never shell=true
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

        // Drain both streams concurrently to prevent pipe-buffer deadlock
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
                    .pocId(pocId)
                    .executed(true)
                    .success(false)
                    .stdout(stdout)
                    .stderr(stderr)
                    .truncated(truncated)
                    .startedAt(startedAt)
                    .finishedAt(finishedAt)
                    .durationMs(durationMs)
                    .message("execution timed out after " + timeoutSeconds + " seconds")
                    .build();
        }

        int exitCode = process.exitValue();
        return PocExecuteResponse.builder()
                .pocId(pocId)
                .executed(true)
                .success(exitCode == 0)
                .exitCode(exitCode)
                .stdout(stdout)
                .stderr(stderr)
                .truncated(truncated)
                .startedAt(startedAt)
                .finishedAt(finishedAt)
                .durationMs(durationMs)
                .build();
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
                        // continue reading (draining) without storing
                    }
                    totalRead += n;
                }
            } catch (IOException ignored) {
                // stream closed by process termination
            }
            holder[index] = buf.toByteArray();
        }, "poc-drain-" + index);
    }

    private Poc findActivePoc(Long id) {
        Poc poc = pocRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "POC 不存在，id=" + id));
        if (poc.getStatus() == PocStatus.DELETED) {
            throw new BusinessException(404, "POC 不存在，id=" + id);
        }
        return poc;
    }

    private String decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "";
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
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
