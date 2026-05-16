package com.camera.app.asset.dto;

import com.camera.app.asset.entity.AssetTechnicalProfile;
import com.camera.app.asset.util.TechnicalProfileConverter;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Schema(description = "设备技术特征")
public class TechnicalProfileResponse {

    @Schema(description = "记录 ID")
    private final Long id;

    @Schema(description = "资产 ID")
    private final Long assetId;

    @Schema(
            description = "开放端口列表，空时返回空数组",
            example = "[22, 80, 443]",
            type = "array",
            implementation = Integer.class
    )
    private final List<Integer> openPorts;

    @Schema(
            description = "协议列表，空时返回空数组",
            example = "[\"SSH\", \"HTTP\"]",
            type = "array",
            implementation = String.class
    )
    private final List<String> protocols;

    @Schema(description = "[兼容字段] 服务 Banner。采集任务不再自动写回此字段；端口级详情请查看 serviceFingerprints")
    private final String serviceBanner;

    @Schema(description = "[兼容字段] Web 标题。采集任务不再自动写回此字段；端口级详情请查看 serviceFingerprints")
    private final String webTitle;

    @Schema(description = "固件版本")
    private final String firmwareVersion;

    @Schema(description = "序列号")
    private final String serialNumber;

    @Schema(description = "MAC 地址")
    private final String macAddress;

    @Schema(description = "[兼容字段] 厂商线索。采集任务不再自动写回此字段；端口级详情请查看 serviceFingerprints")
    private final String vendorHint;

    @Schema(description = "最后指纹采集时间，ISO datetime 格式", example = "2026-05-10T00:00:00")
    private final LocalDateTime lastFingerprintAt;

    @Schema(description = "创建时间")
    private final LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private final LocalDateTime updatedAt;

    public TechnicalProfileResponse(AssetTechnicalProfile p) {
        this.id = p.getId();
        this.assetId = p.getAssetId();
        this.openPorts = TechnicalProfileConverter.parsePorts(p.getOpenPorts());
        this.protocols = TechnicalProfileConverter.parseProtocols(p.getProtocols());
        this.serviceBanner = p.getServiceBanner();
        this.webTitle = p.getWebTitle();
        this.firmwareVersion = p.getFirmwareVersion();
        this.serialNumber = p.getSerialNumber();
        this.macAddress = p.getMacAddress();
        this.vendorHint = p.getVendorHint();
        this.lastFingerprintAt = p.getLastFingerprintAt();
        this.createdAt = p.getCreatedAt();
        this.updatedAt = p.getUpdatedAt();
    }
}
