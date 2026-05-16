package com.camera.app.asset.dto;

import com.camera.app.asset.entity.AssetServiceFingerprint;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Schema(description = "端口/服务识别结果")
public class ServiceFingerprintResponse {

    @Schema(description = "记录 ID")
    private final Long id;

    @Schema(description = "端口号")
    private final Integer port;

    @Schema(description = "传输协议，目前固定为 TCP")
    private final String transportProtocol;

    @Schema(description = "应用层协议，如 HTTP / HTTPS / UNKNOWN")
    private final String applicationProtocol;

    @Schema(description = "URL scheme，如 http / https")
    private final String scheme;

    @Schema(description = "服务 Banner（TCP 握手时读到的原始字节）")
    private final String serviceBanner;

    @Schema(description = "HTTP 页面标题")
    private final String webTitle;

    @Schema(description = "HTTP Server 响应头")
    private final String serverHeader;

    @Schema(description = "厂商线索（来自 Server 头或页面特征）")
    private final String vendorHint;

    @Schema(description = "产品线索")
    private final String productHint;

    @Schema(description = "端口状态：OPEN / CLOSED / UNKNOWN")
    private final String status;

    @Schema(description = "最近一次采集时间")
    private final LocalDateTime lastCollectedAt;

    @Schema(description = "最近一次采集任务 ID")
    private final Long lastTaskId;

    @Schema(description = "本端口探测原始摘要")
    private final String rawSummary;

    @Schema(description = "创建时间")
    private final LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private final LocalDateTime updatedAt;

    public ServiceFingerprintResponse(AssetServiceFingerprint fp) {
        this.id = fp.getId();
        this.port = fp.getPort();
        this.transportProtocol = fp.getTransportProtocol();
        this.applicationProtocol = fp.getApplicationProtocol();
        this.scheme = fp.getScheme();
        this.serviceBanner = fp.getServiceBanner();
        this.webTitle = fp.getWebTitle();
        this.serverHeader = fp.getServerHeader();
        this.vendorHint = fp.getVendorHint();
        this.productHint = fp.getProductHint();
        this.status = fp.getStatus();
        this.lastCollectedAt = fp.getLastCollectedAt();
        this.lastTaskId = fp.getLastTaskId();
        this.rawSummary = fp.getRawSummary();
        this.createdAt = fp.getCreatedAt();
        this.updatedAt = fp.getUpdatedAt();
    }
}
