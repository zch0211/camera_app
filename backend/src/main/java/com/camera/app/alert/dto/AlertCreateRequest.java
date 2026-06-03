package com.camera.app.alert.dto;

import com.camera.app.alert.entity.AlertSeverity;
import com.camera.app.alert.entity.AlertSourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "创建告警请求。手工创建场景请使用 sourceType=RULE")
public class AlertCreateRequest {

    @NotBlank(message = "title 不能为空")
    @Schema(description = "告警标题（必填）", example = "摄像头存在未授权访问漏洞")
    private String title;

    @Schema(description = "关联资产 ID（可选）。若传入则必须是已存在的资产，否则返回 404", example = "8")
    private Long assetId;

    @NotNull(message = "sourceType 不能为空，可选值: POC / DISCOVERY / COLLECTION / INFERENCE / RULE（手工创建使用 RULE）")
    @Schema(
            description = """
                    来源类型（必填）。可选值：
                    - POC：由 POC 执行触发
                    - DISCOVERY：由资产发现触发
                    - COLLECTION：由数据采集触发
                    - INFERENCE：由推断规则触发
                    - RULE：规则匹配 / 手工创建（前端手工创建告警请使用此值）

                    注意：MANUAL 不是有效值，手工创建请统一使用 RULE。
                    """,
            example = "RULE"
    )
    private AlertSourceType sourceType;

    @NotNull(message = "severity 不能为空，可选值: CRITICAL / HIGH / MEDIUM / LOW")
    @Schema(
            description = "严重等级（必填）。可选值: CRITICAL / HIGH / MEDIUM / LOW",
            example = "HIGH"
    )
    private AlertSeverity severity;

    @Schema(description = "告警摘要描述（可选）", example = "该设备存在默认口令，可未授权访问 RTSP 流")
    private String summary;

    @Schema(description = "规则编码（可选）", example = "CVE-2023-1234")
    private String ruleCode;

    @Schema(description = "规则名称（可选）", example = "海康威视未授权访问")
    private String ruleName;
}
