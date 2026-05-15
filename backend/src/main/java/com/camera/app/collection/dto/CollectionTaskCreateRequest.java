package com.camera.app.collection.dto;

import com.camera.app.collection.entity.CollectionTaskType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Schema(description = "发起采集任务请求")
public class CollectionTaskCreateRequest {

    @Schema(
            description = "任务类型，当前仅支持 LIGHTWEIGHT_PROBE，不传默认 LIGHTWEIGHT_PROBE",
            allowableValues = {"LIGHTWEIGHT_PROBE"},
            example = "LIGHTWEIGHT_PROBE"
    )
    private CollectionTaskType taskType = CollectionTaskType.LIGHTWEIGHT_PROBE;

    @Schema(
            description = "待探测端口列表，不传则使用默认集合 [80, 443, 554, 8000, 8080, 8443]",
            example = "[80, 443, 554, 8080]",
            type = "array",
            implementation = Integer.class
    )
    private List<Integer> ports;

    @Schema(description = "是否对开放的 HTTP 端口（80/8000/8080）做页面标题与响应头探测，默认 true")
    private boolean enableHttpProbe = true;

    @Schema(description = "是否对开放的 HTTPS 端口（443/8443）做页面标题与响应头探测（忽略证书），默认 true")
    private boolean enableHttpsProbe = true;

    @Schema(
            description = "每个端口的探测超时时间（毫秒），默认 2000，建议范围 500~5000",
            example = "2000"
    )
    private Integer timeoutMillis;
}
