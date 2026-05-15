package com.camera.app.asset.dto;

import com.camera.app.asset.util.FlexibleLocalDateTimeDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Schema(description = "技术特征更新请求（部分更新，所有字段均可选）")
public class TechnicalProfileUpdateRequest {

    @Schema(
            description = "开放端口列表（整数数组）",
            example = "[22, 80, 443, 8080]",
            type = "array",
            implementation = Integer.class
    )
    private List<Integer> openPorts;

    @Schema(
            description = "协议列表（字符串数组）",
            example = "[\"SSH\", \"HTTP\", \"RTSP\"]",
            type = "array",
            implementation = String.class
    )
    private List<String> protocols;

    @Schema(description = "服务 Banner，最长 512 字符", example = "OpenSSH_8.4p1")
    private String serviceBanner;

    @Schema(description = "Web 标题，最长 256 字符", example = "IP Camera Web")
    private String webTitle;

    @Schema(description = "固件版本，最长 128 字符", example = "V5.3.0")
    private String firmwareVersion;

    @Schema(description = "序列号，最长 128 字符", example = "DS2CD2T85-I3")
    private String serialNumber;

    @Schema(description = "MAC 地址，最长 64 字符", example = "AA:BB:CC:DD:EE:FF")
    private String macAddress;

    @Schema(description = "厂商线索，最长 128 字符", example = "HikVision")
    private String vendorHint;

    @Schema(
            description = "最后指纹采集时间。支持 ISO datetime（2026-05-10T10:30:00）或纯日期（2026-05-10，自动补 00:00:00）",
            example = "2026-05-10T00:00:00",
            type = "string",
            format = "date-time"
    )
    @JsonDeserialize(using = FlexibleLocalDateTimeDeserializer.class)
    private LocalDateTime lastFingerprintAt;
}
