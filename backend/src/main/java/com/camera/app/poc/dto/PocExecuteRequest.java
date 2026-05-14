package com.camera.app.poc.dto;

import com.camera.app.poc.entity.ExecutionMode;
import com.camera.app.poc.entity.TargetStrategy;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@Schema(description = "POC 执行请求")
public class PocExecuteRequest {

    @Schema(description = "执行模式：CHECK=安全检测（默认），EXPLOIT=漏洞利用（高风险）")
    private ExecutionMode mode;

    @Schema(description = "目标策略：EXPLICIT_PORT=显式指定端口，RECOMMENDED_PORT_SCAN=自动扫描推荐端口列表")
    private TargetStrategy targetStrategy;

    @Min(value = 1, message = "端口号最小 1")
    @Max(value = 65535, message = "端口号最大 65535")
    @Schema(description = "显式端口（targetStrategy=EXPLICIT_PORT 时使用；不传则无端口注入）", example = "8080")
    private Integer port;

    @Schema(description = "关联资产 ID，传入后系统自动注入资产 IP 及端口目标")
    private Long assetId;

    @Min(value = 1, message = "超时时间最小 1 秒")
    @Max(value = 30, message = "超时时间最大 30 秒")
    @Schema(description = "执行超时（秒），默认 10，最大 30", example = "10")
    private int timeoutSeconds = 10;

    @Schema(description = "结构化参数（本轮预留，可传入但暂不解析）")
    private Map<String, Object> params;

    @Schema(description = "【仅开发调试】dry-run=true 时不执行脚本，只返回解析出的 argv，用于验证参数组装是否正确",
            example = "false")
    private boolean dryRun = false;

    // ── 兼容字段（旧接口路径，新接口请使用上方字段）────────────────────────────

    @Size(max = 20, message = "最多传入 20 个参数")
    @Schema(description = "【兼容】命令行参数列表，新接口请使用 mode / targetStrategy / port 代替")
    private List<@Size(max = 1000, message = "单个参数不超过 1000 字符") String> arguments = new ArrayList<>();

    @Min(value = 1, message = "端口号最小 1")
    @Max(value = 65535, message = "端口号最大 65535")
    @Schema(description = "【兼容】与 assetId 配合使用的端口号，新接口请使用 port 代替")
    private Integer assetPort;
}
