package com.camera.app.asset.controller;

import com.camera.app.asset.dto.ServiceFingerprintResponse;
import com.camera.app.asset.service.AssetProfileService;
import com.camera.app.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "服务指纹", description = "资产端口/服务识别结果查询。每条记录对应一个端口的最新探测信息。ADMIN / OPERATOR 可读；VIEWER 无权限")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/assets/{id}/service-fingerprints")
@RequiredArgsConstructor
public class AssetServiceFingerprintController {

    private final AssetProfileService profileService;

    @Operation(
            summary = "查询资产全部端口/服务识别结果",
            description = "权限: ROLE_ADMIN / ROLE_OPERATOR。" +
                    "返回该资产所有已探测端口的服务识别结果，按端口升序排列。" +
                    "每条记录包含端口号、应用层协议、HTTP 标题、Server 响应头、厂商线索等。" +
                    "不同端口的结果互相独立，不会互相覆盖。"
    )
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @GetMapping
    public ApiResponse<List<ServiceFingerprintResponse>> listServiceFingerprints(
            @Parameter(description = "资产 ID") @PathVariable Long id) {
        return ApiResponse.ok(profileService.listServiceFingerprints(id));
    }

    @Operation(
            summary = "查询单条端口/服务识别结果详情",
            description = "权限: ROLE_ADMIN / ROLE_OPERATOR。根据指纹记录 ID 查询详情。"
    )
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @GetMapping("/{fingerprintId}")
    public ApiResponse<ServiceFingerprintResponse> getServiceFingerprint(
            @Parameter(description = "资产 ID") @PathVariable Long id,
            @Parameter(description = "指纹记录 ID") @PathVariable Long fingerprintId) {
        return ApiResponse.ok(profileService.getServiceFingerprint(id, fingerprintId));
    }
}
