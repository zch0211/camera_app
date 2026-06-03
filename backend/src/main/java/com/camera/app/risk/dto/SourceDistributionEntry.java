package com.camera.app.risk.dto;

import com.camera.app.alert.entity.AlertSourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Schema(description = "按来源类型的告警分布")
public class SourceDistributionEntry {

    @Schema(description = "来源类型")
    private final AlertSourceType sourceType;

    @Schema(description = "该来源的告警数量")
    private final long count;
}
