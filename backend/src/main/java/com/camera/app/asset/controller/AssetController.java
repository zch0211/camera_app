package com.camera.app.asset.controller;

import com.camera.app.asset.dto.AssetCreateRequest;
import com.camera.app.asset.dto.AssetResponse;
import com.camera.app.asset.dto.AssetUpdateRequest;
import com.camera.app.asset.entity.AssetType;
import com.camera.app.asset.service.AssetService;
import com.camera.app.common.response.ApiResponse;
import com.camera.app.common.response.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "资产管理", description = "资产 CRUD。ADMIN 可全部访问；OPERATOR 仅可查询；VIEWER 无权限")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/assets")
@RequiredArgsConstructor
public class AssetController {

    private final AssetService assetService;

    @Operation(
            summary = "分页查询资产列表",
            description = "权限: ROLE_ADMIN / ROLE_OPERATOR。" +
                    "keyword / brand / model 均为模糊匹配（contains）且忽略大小写；" +
                    "online / type 为精确匹配。" +
                    "响应 data 字段为分页对象，包含 content（列表）、totalElements、totalPages、page、size"
    )
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @GetMapping
    public ApiResponse<PageResult<AssetResponse>> listAssets(
            @Parameter(description = "关键词，模糊匹配 name / ip，忽略大小写")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "品牌，模糊匹配，忽略大小写（如 hik 可匹配 HikVision）")
            @RequestParam(required = false) String brand,
            @Parameter(description = "型号，模糊匹配，忽略大小写（如 DS 可匹配 DS-2CD2T85）")
            @RequestParam(required = false) String model,
            @Parameter(description = "在线状态精确过滤：true=在线，false=离线，不传=全部")
            @RequestParam(required = false) Boolean online,
            @Parameter(description = "类型精确过滤: SERVER / DATABASE / ROUTER / IOT / CAMERA / NVR / PLATFORM / OTHER，不传=全部")
            @RequestParam(required = false) AssetType type,
            @Parameter(description = "页码，从 0 开始，默认 0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页条数，默认 20")
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(assetService.listAssets(keyword, brand, model, online, type, page, size));
    }

    @Operation(
            summary = "查询资产详情",
            description = "权限: ROLE_ADMIN / ROLE_OPERATOR"
    )
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @GetMapping("/{id}")
    public ApiResponse<AssetResponse> getAsset(
            @Parameter(description = "资产 ID") @PathVariable Long id) {
        return ApiResponse.ok(assetService.getAsset(id));
    }

    @Operation(
            summary = "创建资产",
            description = "权限: ROLE_ADMIN。IP 全局唯一，重复时返回 409。" +
                    "type 可选，不传默认 OTHER；可选值: SERVER / DATABASE / ROUTER / IOT / CAMERA / NVR / PLATFORM / OTHER"
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "创建成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "参数校验失败（ip/name 为空、type 枚举值无效等）"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "IP 已存在")
    })
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AssetResponse> createAsset(
            @Valid @RequestBody AssetCreateRequest request) {
        return ApiResponse.ok(assetService.createAsset(request));
    }

    @Operation(
            summary = "修改资产（部分更新）",
            description = "权限: ROLE_ADMIN。所有字段均为可选，仅传需修改的字段；修改 IP 时仍需保证唯一。" +
                    "type 不传则不修改；可选值: SERVER / DATABASE / ROUTER / IOT / CAMERA / NVR / PLATFORM / OTHER"
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "修改成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "参数校验失败（type 枚举值无效等）"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "资产不存在"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "IP 已被其他资产占用")
    })
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ApiResponse<AssetResponse> updateAsset(
            @Parameter(description = "资产 ID") @PathVariable Long id,
            @Valid @RequestBody AssetUpdateRequest request) {
        return ApiResponse.ok(assetService.updateAsset(id, request));
    }

    @Operation(
            summary = "删除资产",
            description = "权限: ROLE_ADMIN。物理删除"
    )
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteAsset(
            @Parameter(description = "资产 ID") @PathVariable Long id) {
        assetService.deleteAsset(id);
        return ApiResponse.ok(null);
    }
}
