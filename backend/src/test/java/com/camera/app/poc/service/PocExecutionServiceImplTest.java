package com.camera.app.poc.service;

import com.camera.app.asset.repository.AssetRepository;
import com.camera.app.poc.dto.PocExecuteRequest;
import com.camera.app.poc.dto.PocExecuteResponse;
import com.camera.app.poc.entity.Poc;
import com.camera.app.poc.entity.PocExecutionLog;
import com.camera.app.poc.entity.PocStatus;
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
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
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
