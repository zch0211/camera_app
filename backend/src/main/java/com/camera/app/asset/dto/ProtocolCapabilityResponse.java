package com.camera.app.asset.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Builder
@Schema(description = "协议能力探测结果")
public class ProtocolCapabilityResponse {

    @Schema(description = "协议名称，如 ONVIF / RTSP / SNMP / SSH / TELNET / UPNP / WS_DISCOVERY")
    private final String protocol;

    @Schema(description = "探测状态：UNDETECTED / SUPPORTED / AUTH_REQUIRED / FAILED")
    private final String status;

    @Schema(description = "是否确认协议可达（SUPPORTED 或 AUTH_REQUIRED 时为 true）")
    private final boolean supported;

    @Schema(description = "可直接展示的中文摘要")
    private final String summary;

    @Schema(description = "最近一次探测时间")
    private final LocalDateTime lastDetectedAt;

    @Schema(description = "来源采集任务 ID")
    private final Long sourceTaskId;

    @Schema(description = "探测目标端口")
    private final Integer port;

    @Schema(description = "详细探测数据（key-value）")
    private final Map<String, Object> details;
}
