package com.camera.app.asset.controller;

import com.camera.app.asset.dto.AssetProfileResponse;
import com.camera.app.asset.dto.TechnicalProfileResponse;
import com.camera.app.asset.dto.TechnicalProfileUpdateRequest;
import com.camera.app.asset.service.AssetProfileService;
import com.camera.app.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "设备画像", description = "设备画像聚合视图与技术特征维护。ADMIN 可全部访问；OPERATOR 只读；VIEWER 无权限")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/assets/{id}/profile")
@RequiredArgsConstructor
public class AssetProfileController {

    private final AssetProfileService profileService;

    @Operation(
            summary = "获取设备画像聚合视图",
            description = "权限: ROLE_ADMIN / ROLE_OPERATOR。" +
                    "返回结构包含 basicInfo（基础台账字段）、technicalFeatures（技术特征，可为 null）、" +
                    "missingFields（后端计算的缺失字段列表）、inferenceCandidates（候选推断结果，按置信度降序）、" +
                    "evidences（证据来源，按采集时间降序）、knowledgeEnhancement（知识图谱增强占位）"
    )
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @GetMapping
    public ApiResponse<AssetProfileResponse> getProfile(
            @Parameter(description = "资产 ID") @PathVariable Long id) {
        return ApiResponse.ok(profileService.getProfile(id));
    }

    @Operation(
            summary = "获取设备技术特征",
            description = "权限: ROLE_ADMIN / ROLE_OPERATOR。若尚未录入技术特征，返回全字段为 null 的空对象（不报 404）"
    )
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @GetMapping("/technical-features")
    public ApiResponse<TechnicalProfileResponse> getTechnicalFeatures(
            @Parameter(description = "资产 ID") @PathVariable Long id) {
        return ApiResponse.ok(profileService.getTechnicalFeatures(id));
    }

    @Operation(
            summary = "更新设备技术特征（部分更新）",
            description = "权限: ROLE_ADMIN。所有字段均可选，仅传需修改的字段。" +
                    "若技术特征记录不存在，第一次调用时自动创建。" +
                    "openPorts / protocols 建议传 JSON 字符串，如 [80,443] / [\"RTSP\",\"HTTP\"]"
    )
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/technical-features")
    public ApiResponse<TechnicalProfileResponse> updateTechnicalFeatures(
            @Parameter(description = "资产 ID") @PathVariable Long id,
            @Valid @RequestBody TechnicalProfileUpdateRequest request) {
        return ApiResponse.ok(profileService.updateTechnicalFeatures(id, request));
    }
}
