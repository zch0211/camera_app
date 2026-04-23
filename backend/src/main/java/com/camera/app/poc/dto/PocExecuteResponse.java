package com.camera.app.poc.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class PocExecuteResponse {

    private Long pocId;

    /** 是否实际启动了子进程（false 表示因校验失败而未执行） */
    private boolean executed;

    /** 执行是否成功（exitCode == 0）；executed=false 时为 null */
    private Boolean success;

    /** 进程退出码；超时/未执行时为 null */
    private Integer exitCode;

    private String stdout;
    private String stderr;

    /** stdout 或 stderr 任一被截断时为 true */
    private boolean truncated;

    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long durationMs;

    /** 未执行或异常时的说明信息 */
    private String message;
}
