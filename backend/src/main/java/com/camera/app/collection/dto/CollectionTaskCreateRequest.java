package com.camera.app.collection.dto;

import com.camera.app.collection.entity.CollectionTaskType;
import com.camera.app.collection.entity.TaskPreset;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Schema(description = "发起采集任务请求")
public class CollectionTaskCreateRequest {

    @Schema(
            description = "任务类型。LIGHTWEIGHT_PROBE=使用预设插件组合（推荐）；PLUGIN_PROBE=自定义插件列表。默认 LIGHTWEIGHT_PROBE",
            allowableValues = {"LIGHTWEIGHT_PROBE", "PLUGIN_PROBE"},
            example = "LIGHTWEIGHT_PROBE"
    )
    private CollectionTaskType taskType = CollectionTaskType.LIGHTWEIGHT_PROBE;

    @Schema(
            description = "探测预设。CAMERA_PRESET=摄像头；NVR_PRESET=录像机；ROUTER_PRESET=路由器；FULL_PRESET=全量；CUSTOM=自定义。默认 CAMERA_PRESET",
            allowableValues = {"CAMERA_PRESET", "NVR_PRESET", "ROUTER_PRESET", "FULL_PRESET", "CUSTOM"},
            example = "CAMERA_PRESET"
    )
    private TaskPreset preset = TaskPreset.CAMERA_PRESET;

    @Schema(
            description = "自定义启用的插件名称列表（仅 preset=CUSTOM 时生效）。" +
                    "可选值: port-probe, http-fingerprint, rtsp-probe, onvif-probe, web-fingerprint, " +
                    "snmp-probe, ssh-banner, telnet-banner, upnp-probe",
            example = "[\"port-probe\",\"rtsp-probe\",\"onvif-probe\"]"
    )
    private List<String> enabledPlugins;

    @Schema(
            description = "待探测端口列表。不传时由预设自动决定",
            example = "[80, 443, 554, 8000]",
            type = "array",
            implementation = Integer.class
    )
    private List<Integer> ports;

    @Schema(description = "兼容字段，已无实际作用（HTTP/HTTPS 探测由插件框架接管）。保留以兼容旧调用方")
    private boolean enableHttpProbe = true;

    @Schema(description = "兼容字段，已无实际作用。保留以兼容旧调用方")
    private boolean enableHttpsProbe = true;

    @Schema(
            description = "每个端口的探测超时时间（毫秒），默认 2000，建议范围 500~5000",
            example = "2000"
    )
    private Integer timeoutMillis;
}
