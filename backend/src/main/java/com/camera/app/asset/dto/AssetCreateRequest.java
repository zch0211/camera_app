package com.camera.app.asset.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "创建资产请求")
public class AssetCreateRequest {

    @NotBlank(message = "IP 不能为空")
    @Size(max = 64, message = "IP 长度不能超过 64")
    @Schema(description = "资产 IP 地址（全局唯一）", example = "192.168.1.100", requiredMode = Schema.RequiredMode.REQUIRED)
    private String ip;

    @NotBlank(message = "名称不能为空")
    @Size(max = 128, message = "名称长度不能超过 128")
    @Schema(description = "资产名称", example = "摄像头-A栋1楼", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Size(max = 64, message = "品牌长度不能超过 64")
    @Schema(description = "品牌", example = "海康威视")
    private String brand;

    @Size(max = 64, message = "型号长度不能超过 64")
    @Schema(description = "型号", example = "DS-2CD2T85")
    private String model;

    @Size(max = 256, message = "位置长度不能超过 256")
    @Schema(description = "安装位置", example = "A栋1楼大厅")
    private String location;

    @Schema(description = "是否在线，默认 false")
    private boolean online = false;

    @Schema(description = "风险分值，默认 0")
    private Integer riskScore = 0;

    @Schema(description = "所属组织 ID（可选）")
    private Long orgId;
}
