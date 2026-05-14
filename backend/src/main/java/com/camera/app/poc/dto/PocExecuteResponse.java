package com.camera.app.poc.dto;

import com.camera.app.poc.entity.ExecutionMode;
import com.camera.app.poc.entity.TargetStrategy;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder(toBuilder = true)
@Schema(description = "POC 执行结果")
public class PocExecuteResponse {

    private Long pocId;

    @Schema(description = "执行记录 ID，executed=true 时存在；保存失败时为 null")
    private Long executionId;

    @Schema(description = "是否实际启动了子进程（false 表示因校验失败而未执行）")
    private boolean executed;

    @Schema(description = "执行是否成功（exitCode == 0）；executed=false 时为 null")
    private Boolean success;

    @Schema(description = "进程退出码；超时/未执行时为 null")
    private Integer exitCode;

    private String stdout;
    private String stderr;

    @Schema(description = "stdout 或 stderr 任一被截断时为 true")
    private boolean truncated;

    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long durationMs;

    @Schema(description = "本次执行模式：CHECK / EXPLOIT")
    private ExecutionMode mode;

    @Schema(description = "本次目标策略：EXPLICIT_PORT / RECOMMENDED_PORT_SCAN")
    private TargetStrategy targetStrategy;

    @Schema(description = "本次实际使用的端口，无端口注入时为 null")
    private Integer finalPort;

    @Schema(description = "本次目标字符串，如 http://192.168.1.100:8080 或裸 IP；无资产关联时为 null")
    private String usedTarget;

    @Schema(description = "未执行或超时时的说明信息")
    private String message;
}
