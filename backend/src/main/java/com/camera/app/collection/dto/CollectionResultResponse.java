package com.camera.app.collection.dto;

import com.camera.app.collection.entity.AssetCollectionResult;
import com.camera.app.collection.entity.ProbeType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Schema(description = "原始采集结果")
public class CollectionResultResponse {

    @Schema(description = "结果 ID")
    private final Long id;

    @Schema(description = "所属任务 ID")
    private final Long taskId;

    @Schema(description = "资产 ID")
    private final Long assetId;

    @Schema(description = "探测类型: PORT_SCAN / HTTP_TITLE / RTSP_PROBE / ONVIF_PROBE / WEB_FINGERPRINT / SNMP_PROBE / SSH_BANNER / TELNET_BANNER / UPNP_PROBE")
    private final ProbeType probeType;

    @Schema(description = "产生本条结果的插件名称")
    private final String pluginName;

    @Schema(description = "本次探测是否成功")
    private final boolean success;

    @Schema(description = "探测目标 Host / IP")
    private final String targetHost;

    @Schema(description = "探测目标端口，PORT_SCAN 类型有值")
    private final Integer targetPort;

    @Schema(description = "协议线索，如 HTTP / HTTPS / RTSP / TCP")
    private final String protocolHint;

    @Schema(description = "原始响应摘要")
    private final String rawData;

    @Schema(description = "结构化解析结果（JSON 字符串）")
    private final String parsedData;

    @Schema(description = "错误信息（失败时）")
    private final String errorMessage;

    @Schema(description = "采集时间")
    private final LocalDateTime collectedAt;

    @Schema(description = "创建时间")
    private final LocalDateTime createdAt;

    public CollectionResultResponse(AssetCollectionResult r) {
        this.id = r.getId();
        this.taskId = r.getTaskId();
        this.assetId = r.getAssetId();
        this.probeType = r.getProbeType();
        this.pluginName = r.getPluginName();
        this.success = r.isSuccess();
        this.targetHost = r.getTargetHost();
        this.targetPort = r.getTargetPort();
        this.protocolHint = r.getProtocolHint();
        this.rawData = r.getRawData();
        this.parsedData = r.getParsedData();
        this.errorMessage = r.getErrorMessage();
        this.collectedAt = r.getCollectedAt();
        this.createdAt = r.getCreatedAt();
    }
}
