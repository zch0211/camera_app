package com.camera.app.poc.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "POC 元数据修改请求（不替换文件本体，只改元数据）")
public class PocUpdateRequest {

    @Size(max = 200, message = "名称不能超过 200 个字符")
    @Schema(description = "POC 名称")
    private String name;

    @Size(max = 2000, message = "描述不能超过 2000 个字符")
    @Schema(description = "描述")
    private String description;

    @Size(max = 64, message = "CVE 编号不能超过 64 个字符")
    @Schema(description = "CVE 编号，如 CVE-2024-12345")
    private String cveId;

    @Schema(description = "严重等级：LOW / MEDIUM / HIGH / CRITICAL")
    private String severity;

    @Schema(description = "文件语言：PYTHON / JAVA / GO / SHELL / OTHER")
    private String language;

    @Schema(description = "目标类型：CAMERA / ROUTER / NVR / PLATFORM / OTHER")
    private String targetType;

    @Size(max = 100, message = "厂商不能超过 100 个字符")
    @Schema(description = "适用厂商")
    private String vendor;

    @Schema(description = "协议类型：HTTP / HTTPS / RTSP / ONVIF / TCP / UDP / OTHER")
    private String protocol;

    @Size(max = 500, message = "入口点不能超过 500 个字符")
    @Schema(description = "执行入口点（预留字段，供后续扫描执行器使用）")
    private String entryPoint;

    @Schema(description = "是否启用")
    private Boolean enabled;
}
