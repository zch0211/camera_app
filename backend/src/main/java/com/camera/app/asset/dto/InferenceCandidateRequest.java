package com.camera.app.asset.dto;

import com.camera.app.asset.entity.InferenceCandidateSourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Schema(description = "候选推断创建/更新请求")
public class InferenceCandidateRequest {

    @NotBlank
    @Schema(description = "推断字段名，如 brand / model / firmwareVersion", requiredMode = Schema.RequiredMode.REQUIRED)
    private String fieldName;

    @NotBlank
    @Schema(description = "候选值", requiredMode = Schema.RequiredMode.REQUIRED)
    private String candidateValue;

    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    @Schema(description = "置信度 0~1", requiredMode = Schema.RequiredMode.REQUIRED, example = "0.85")
    private BigDecimal confidence;

    @Schema(description = "推断依据说明")
    private String reason;

    @Schema(
            description = "推断来源类型，不传默认 MANUAL。" +
                    "MANUAL=人工录入；RULE=规则引擎推断；KG=知识图谱推断；MODEL=大模型推断。" +
                    "传入非法值时接口返回 400 并列出全部合法值",
            allowableValues = {"MANUAL", "RULE", "KG", "MODEL"},
            example = "MANUAL"
    )
    private InferenceCandidateSourceType sourceType = InferenceCandidateSourceType.MANUAL;

    @Schema(description = "是否已确认，默认 false")
    private Boolean confirmed = false;
}
