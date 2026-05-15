package com.camera.app.asset.controller;

import com.camera.app.asset.dto.EvidenceRequest;
import com.camera.app.asset.dto.EvidenceResponse;
import com.camera.app.asset.service.AssetProfileService;
import com.camera.app.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "证据来源维护", description = "设备画像证据来源 CRUD。ADMIN 可写；ADMIN / OPERATOR 可读")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/assets/{id}/evidences")
@RequiredArgsConstructor
public class AssetEvidenceController {

    private final AssetProfileService profileService;

    @Operation(
            summary = "查询证据来源列表",
            description = "权限: ROLE_ADMIN / ROLE_OPERATOR。结果按采集时间降序排列"
    )
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @GetMapping
    public ApiResponse<List<EvidenceResponse>> list(
            @Parameter(description = "资产 ID") @PathVariable Long id) {
        return ApiResponse.ok(profileService.listEvidences(id));
    }

    @Operation(
            summary = "新增证据来源",
            description = "权限: ROLE_ADMIN。sourceType 可选值: MANUAL / SCAN / SNIFF / KG / MODEL / IMPORT，默认 MANUAL。" +
                    "collectedAt 不填则默认当前时间"
    )
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<EvidenceResponse> create(
            @Parameter(description = "资产 ID") @PathVariable Long id,
            @Valid @RequestBody EvidenceRequest request) {
        return ApiResponse.ok(profileService.createEvidence(id, request));
    }

    @Operation(
            summary = "更新证据来源",
            description = "权限: ROLE_ADMIN。支持部分更新"
    )
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{evidenceId}")
    public ApiResponse<EvidenceResponse> update(
            @Parameter(description = "资产 ID") @PathVariable Long id,
            @Parameter(description = "证据 ID") @PathVariable Long evidenceId,
            @Valid @RequestBody EvidenceRequest request) {
        return ApiResponse.ok(profileService.updateEvidence(id, evidenceId, request));
    }

    @Operation(
            summary = "删除证据来源",
            description = "权限: ROLE_ADMIN"
    )
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{evidenceId}")
    public ApiResponse<Void> delete(
            @Parameter(description = "资产 ID") @PathVariable Long id,
            @Parameter(description = "证据 ID") @PathVariable Long evidenceId) {
        profileService.deleteEvidence(id, evidenceId);
        return ApiResponse.ok(null);
    }
}
