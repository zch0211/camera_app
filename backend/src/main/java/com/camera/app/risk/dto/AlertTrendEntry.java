package com.camera.app.risk.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Schema(description = "每日告警趋势条目")
public class AlertTrendEntry {

    @Schema(description = "日期（yyyy-MM-dd）", example = "2026-06-01")
    private final String date;

    @Schema(description = "当天新增告警总数")
    private final long total;

    @Schema(description = "当天新增的有效告警数（NEW + CONFIRMED）")
    private final long open;
}
