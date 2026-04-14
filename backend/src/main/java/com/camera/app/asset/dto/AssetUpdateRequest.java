package com.camera.app.asset.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "修改资产请求（所有字段可选，仅传需修改的字段）")
public class AssetUpdateRequest {

    @Size(max = 64, message = "IP 长度不能超过 64")
    @Schema(description = "资产 IP 地址（唯一），不传则不修改", example = "192.168.1.101")
    private String ip;

    @Size(max = 128, message = "名称长度不能超过 128")
    @Schema(description = "资产名称，不传则不修改", example = "摄像头-A栋2楼")
    private String name;

    @Size(max = 64, message = "品牌长度不能超过 64")
    @Schema(description = "品牌，不传则不修改")
    private String brand;

    @Size(max = 64, message = "型号长度不能超过 64")
    @Schema(description = "型号，不传则不修改")
    private String model;

    @Size(max = 256, message = "位置长度不能超过 256")
    @Schema(description = "安装位置，不传则不修改")
    private String location;

    @Schema(description = "是否在线，不传则不修改")
    private Boolean online;

    @Schema(description = "风险分值，不传则不修改")
    private Integer riskScore;

    @Schema(description = "所属组织 ID，不传则不修改")
    private Long orgId;
}
