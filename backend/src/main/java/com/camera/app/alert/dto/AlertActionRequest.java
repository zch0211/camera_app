package com.camera.app.alert.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "告警操作请求（可选备注）")
public class AlertActionRequest {

    @Schema(description = "操作备注（可空）", example = "已通知相关人员处置")
    private String comment;
}
