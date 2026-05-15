package com.camera.app.asset.controller;

import com.camera.app.asset.dto.InferenceCandidateRequest;
import com.camera.app.asset.dto.InferenceCandidateResponse;
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

@Tag(name = "候选推断维护", description = "设备画像候选推断结果 CRUD。ADMIN 可写；ADMIN / OPERATOR 可读")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/assets/{id}/inference-candidates")
@RequiredArgsConstructor
public class AssetInferenceCandidateController {

    private final AssetProfileService profileService;

    @Operation(
            summary = "查询候选推断列表",
            description = "权限: ROLE_ADMIN / ROLE_OPERATOR。结果按置信度降序排列"
    )
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @GetMapping
    public ApiResponse<List<InferenceCandidateResponse>> list(
            @Parameter(description = "资产 ID") @PathVariable Long id) {
        return ApiResponse.ok(profileService.listInferenceCandidates(id));
    }

    @Operation(
            summary = "新增候选推断",
            description = "权限: ROLE_ADMIN。sourceType 可选值: MANUAL / RULE / KG / MODEL，默认 MANUAL。" +
                    "confidence 范围 0~1，建议精确到小数点后 3 位"
    )
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InferenceCandidateResponse> create(
            @Parameter(description = "资产 ID") @PathVariable Long id,
            @Valid @RequestBody InferenceCandidateRequest request) {
        return ApiResponse.ok(profileService.createInferenceCandidate(id, request));
    }

    @Operation(
            summary = "更新候选推断",
            description = "权限: ROLE_ADMIN。支持部分更新，可单独修改 confirmed 字段来确认推断结果"
    )
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{candidateId}")
    public ApiResponse<InferenceCandidateResponse> update(
            @Parameter(description = "资产 ID") @PathVariable Long id,
            @Parameter(description = "候选推断 ID") @PathVariable Long candidateId,
            @Valid @RequestBody InferenceCandidateRequest request) {
        return ApiResponse.ok(profileService.updateInferenceCandidate(id, candidateId, request));
    }

    @Operation(
            summary = "删除候选推断",
            description = "权限: ROLE_ADMIN"
    )
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{candidateId}")
    public ApiResponse<Void> delete(
            @Parameter(description = "资产 ID") @PathVariable Long id,
            @Parameter(description = "候选推断 ID") @PathVariable Long candidateId) {
        profileService.deleteInferenceCandidate(id, candidateId);
        return ApiResponse.ok(null);
    }
}
