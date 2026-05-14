package com.camera.app.poc.service;

import com.camera.app.asset.entity.Asset;
import com.camera.app.asset.repository.AssetRepository;
import com.camera.app.common.exception.BusinessException;
import com.camera.app.poc.dto.PocExecuteRequest;
import com.camera.app.poc.dto.PocExecuteResponse;
import com.camera.app.poc.entity.ExecutionMode;
import com.camera.app.poc.entity.Language;
import com.camera.app.poc.entity.Poc;
import com.camera.app.poc.entity.PocExecutionLog;
import com.camera.app.poc.entity.PocStatus;
import com.camera.app.poc.entity.TargetStrategy;
import com.camera.app.poc.repository.PocRepository;
import com.camera.app.storage.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PocExecutionServiceImplTest {

    @Mock PocRepository pocRepository;
    @Mock FileStorageService fileStorageService;
    @Mock AssetRepository assetRepository;
    @Mock PocExecutionLogService pocExecutionLogService;

    @InjectMocks PocExecutionServiceImpl service;

    private Poc pyPoc;
    private Poc nonPyPoc;

    @BeforeEach
    void setUp() {
        pyPoc = new Poc();
        pyPoc.setId(1L);
        pyPoc.setOriginalFilename("exploit.py");
        pyPoc.setObjectKey("pocs/test/exploit.py");
        pyPoc.setStatus(PocStatus.ACTIVE);

        nonPyPoc = new Poc();
        nonPyPoc.setId(2L);
        nonPyPoc.setOriginalFilename("exploit.jar");
        nonPyPoc.setObjectKey("pocs/test/exploit.jar");
        nonPyPoc.setStatus(PocStatus.ACTIVE);
    }

    // ─── 1. 非 .py 文件不保存执行日志 ────────────────────────────────────────

    @Test
    void nonPyFileDoesNotSaveLog() {
        when(pocRepository.findById(2L)).thenReturn(Optional.of(nonPyPoc));

        PocExecuteResponse resp = service.execute(2L, new PocExecuteRequest(), "admin");

        assertThat(resp.isExecuted()).isFalse();
        assertThat(resp.getExecutionId()).isNull();
        verify(pocExecutionLogService, never()).save(any());
    }

    // ─── 2. .py 文件执行后保存日志并返回 executionId ─────────────────────────
    // 此测试需要环境中安装 Python，否则跳过

    @Test
    void pyFileExecutionSavesLog() throws Exception {
        assumeTrue(isPythonAvailable(), "Skipped: Python not available in this environment");

        when(pocRepository.findById(1L)).thenReturn(Optional.of(pyPoc));
        // Return a trivial Python script that exits 0
        when(fileStorageService.download("pocs/test/exploit.py"))
                .thenReturn(new ByteArrayInputStream("import sys; sys.exit(0)".getBytes()));

        PocExecutionLog savedLog = new PocExecutionLog();
        savedLog.setId(42L);
        when(pocExecutionLogService.save(any())).thenReturn(savedLog);

        PocExecuteRequest req = new PocExecuteRequest();
        req.setTimeoutSeconds(10);

        PocExecuteResponse resp = service.execute(1L, req, "admin");

        assertThat(resp.isExecuted()).isTrue();
        assertThat(resp.getExecutionId()).isEqualTo(42L);

        ArgumentCaptor<PocExecutionLog> captor = ArgumentCaptor.forClass(PocExecutionLog.class);
        verify(pocExecutionLogService).save(captor.capture());

        PocExecutionLog log = captor.getValue();
        assertThat(log.getPocId()).isEqualTo(1L);
        assertThat(log.getExecutedBy()).isEqualTo("admin");
        assertThat(log.getStartedAt()).isNotNull();
        assertThat(log.getFinishedAt()).isNotNull();
        assertThat(log.getDurationMs()).isNotNegative();
    }

    // ─── 3. 日志保存失败不影响主响应 ─────────────────────────────────────────

    @Test
    void logSaveFailureDoesNotBreakResponse() throws Exception {
        assumeTrue(isPythonAvailable(), "Skipped: Python not available in this environment");

        when(pocRepository.findById(1L)).thenReturn(Optional.of(pyPoc));
        when(fileStorageService.download("pocs/test/exploit.py"))
                .thenReturn(new ByteArrayInputStream("import sys; sys.exit(0)".getBytes()));
        when(pocExecutionLogService.save(any())).thenThrow(new RuntimeException("DB down"));

        PocExecuteResponse resp = service.execute(1L, new PocExecuteRequest(), "admin");

        assertThat(resp.isExecuted()).isTrue();
        assertThat(resp.getExecutionId()).isNull(); // graceful degradation
    }

    // ─── 4. dry-run resolves argv without executing subprocess ───────────────

    @Test
    void dryRun_checkModeExplicitPort_returnsArgvWithoutExecuting() {
        pyPoc.setLanguage(Language.PYTHON);
        when(pocRepository.findById(1L)).thenReturn(Optional.of(pyPoc));

        Asset asset = new Asset();
        asset.setId(3L);
        asset.setIp("192.168.1.100");
        when(assetRepository.findById(3L)).thenReturn(Optional.of(asset));

        PocExecuteRequest req = new PocExecuteRequest();
        req.setMode(ExecutionMode.CHECK);
        req.setTargetStrategy(TargetStrategy.EXPLICIT_PORT);
        req.setPort(8080);
        req.setAssetId(3L);
        req.setDryRun(true);

        PocExecuteResponse resp = service.execute(1L, req, "admin");

        assertThat(resp.isExecuted()).isFalse();
        assertThat(resp.getMessage()).contains("DRY-RUN");
        assertThat(resp.getMessage()).contains("-u");
        assertThat(resp.getMessage()).contains("http://192.168.1.100:8080");
        assertThat(resp.getMessage()).contains("--check");

        verify(fileStorageService, never()).download(any());
        verify(pocExecutionLogService, never()).save(any());
    }

    // ─── 5. EXPLOIT + params.cmd → dry-run argv contains --cmd ───────────────

    @Test
    void dryRun_exploitModeWithCmd_argvContainsCmdFlag() {
        pyPoc.setLanguage(Language.PYTHON);
        when(pocRepository.findById(1L)).thenReturn(Optional.of(pyPoc));

        Asset asset = new Asset();
        asset.setId(3L);
        asset.setIp("192.168.1.100");
        when(assetRepository.findById(3L)).thenReturn(Optional.of(asset));

        PocExecuteRequest req = new PocExecuteRequest();
        req.setMode(ExecutionMode.EXPLOIT);
        req.setTargetStrategy(TargetStrategy.EXPLICIT_PORT);
        req.setPort(8080);
        req.setAssetId(3L);
        req.setParams(Map.of("cmd", "whoami"));
        req.setDryRun(true);

        PocExecuteResponse resp = service.execute(1L, req, "admin");

        assertThat(resp.isExecuted()).isFalse();
        assertThat(resp.getMessage()).contains("DRY-RUN");
        assertThat(resp.getMessage()).contains("-u");
        assertThat(resp.getMessage()).contains("http://192.168.1.100:8080");
        assertThat(resp.getMessage()).contains("--cmd");
        assertThat(resp.getMessage()).contains("whoami");
        assertThat(resp.getMessage()).doesNotContain("--check");

        verify(fileStorageService, never()).download(any());
        verify(pocExecutionLogService, never()).save(any());
    }

    // ─── 6. EXPLOIT missing params.cmd → 400 BusinessException ───────────────

    @Test
    void exploitMode_missingCmd_throws400() {
        pyPoc.setLanguage(Language.PYTHON);
        when(pocRepository.findById(1L)).thenReturn(Optional.of(pyPoc));

        PocExecuteRequest req = new PocExecuteRequest();
        req.setMode(ExecutionMode.EXPLOIT);
        req.setDryRun(true);

        assertThatThrownBy(() -> service.execute(1L, req, "admin"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("params.cmd");

        verify(fileStorageService, never()).download(any());
        verify(pocExecutionLogService, never()).save(any());
    }

    // ─── helper ───────────────────────────────────────────────────────────────

    private static boolean isPythonAvailable() {
        try {
            Process p = new ProcessBuilder("python", "--version").start();
            return p.waitFor(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            return false;
        }
    }
}
