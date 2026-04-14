package com.camera.app.asset.dto;

import com.camera.app.asset.entity.Asset;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Schema(description = "资产详情响应")
public class AssetResponse {

    @Schema(description = "资产 ID")
    private final Long id;

    @Schema(description = "IP 地址")
    private final String ip;

    @Schema(description = "名称")
    private final String name;

    @Schema(description = "品牌")
    private final String brand;

    @Schema(description = "型号")
    private final String model;

    @Schema(description = "安装位置")
    private final String location;

    @Schema(description = "是否在线")
    private final boolean online;

    @Schema(description = "风险分值")
    private final Integer riskScore;

    @Schema(description = "所属组织 ID")
    private final Long orgId;

    @Schema(description = "创建时间")
    private final LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private final LocalDateTime updatedAt;

    public AssetResponse(Asset asset) {
        this.id = asset.getId();
        this.ip = asset.getIp();
        this.name = asset.getName();
        this.brand = asset.getBrand();
        this.model = asset.getModel();
        this.location = asset.getLocation();
        this.online = asset.isOnline();
        this.riskScore = asset.getRiskScore();
        this.orgId = asset.getOrgId();
        this.createdAt = asset.getCreatedAt();
        this.updatedAt = asset.getUpdatedAt();
    }
}
