package com.camera.app.system;

import com.camera.app.asset.entity.InferenceCandidateSourceType;
import com.camera.app.collection.entity.CollectionTaskStatus;
import com.camera.app.collection.entity.CollectionTaskType;
import com.camera.app.collection.entity.ProbeType;
import com.camera.app.common.response.ApiResponse;
import com.camera.app.poc.entity.Language;
import com.camera.app.poc.entity.PocStatus;
import com.camera.app.poc.entity.Protocol;
import com.camera.app.poc.entity.Severity;
import com.camera.app.poc.entity.TargetType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "系统", description = "系统管理接口")
@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

    @Operation(summary = "健康检查（无需认证）")
    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.ok(Map.of(
                "status", "UP",
                "timestamp", LocalDateTime.now().toString(),
                "service", "camera-app"
        ));
    }

    @Operation(
            summary = "获取系统枚举字典（无需认证）",
            description = "返回前端所有下拉框/标签所需枚举值，避免硬编码。" +
                    "包含角色、POC 严重等级、语言、目标类型、协议、状态，以及画像模块枚举（inferenceCandidateSourceTypes）"
    )
    @GetMapping("/enums")
    public ApiResponse<Map<String, Object>> enums() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("roles", List.of("ROLE_ADMIN", "ROLE_OPERATOR", "ROLE_VIEWER"));
        result.put("pocSeverity", Arrays.stream(Severity.values()).map(Enum::name).toList());
        result.put("pocLanguage", Arrays.stream(Language.values()).map(Enum::name).toList());
        result.put("pocTargetType", Arrays.stream(TargetType.values()).map(Enum::name).toList());
        result.put("pocProtocol", Arrays.stream(Protocol.values()).map(Enum::name).toList());
        result.put("pocStatus", Arrays.stream(PocStatus.values()).map(Enum::name).toList());
        result.put("enabledStatus", List.of("ENABLED", "DISABLED"));
        result.put("inferenceCandidateSourceTypes", Arrays.stream(InferenceCandidateSourceType.values()).map(Enum::name).toList());
        result.put("collectionTaskTypes",    Arrays.stream(CollectionTaskType.values()).map(Enum::name).toList());
        result.put("collectionTaskStatuses", Arrays.stream(CollectionTaskStatus.values()).map(Enum::name).toList());
        result.put("collectionProbeTypes",   Arrays.stream(ProbeType.values()).map(Enum::name).toList());
        return ApiResponse.ok(result);
    }
}
