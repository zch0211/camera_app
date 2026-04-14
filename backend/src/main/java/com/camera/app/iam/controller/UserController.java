package com.camera.app.iam.controller;

import com.camera.app.common.response.ApiResponse;
import com.camera.app.common.response.PageResult;
import com.camera.app.iam.dto.UserCreateRequest;
import com.camera.app.iam.dto.UserResponse;
import com.camera.app.iam.dto.UserUpdateRequest;
import com.camera.app.iam.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "用户管理", description = "用户 CRUD。ADMIN 可全部访问；OPERATOR 仅可查询；VIEWER 无权限")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(
            summary = "分页查询用户列表",
            description = "权限: ROLE_ADMIN / ROLE_OPERATOR。支持按 username/nickname 模糊搜索及启用状态过滤"
    )
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @GetMapping
    public ApiResponse<PageResult<UserResponse>> listUsers(
            @Parameter(description = "关键词（模糊匹配 username / nickname）")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "启用状态过滤：true=启用，false=禁用，不传=全部")
            @RequestParam(required = false) Boolean enabled,
            @Parameter(description = "页码，从 0 开始，默认 0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页条数，默认 20")
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(userService.listUsers(keyword, enabled, page, size));
    }

    @Operation(
            summary = "查询用户详情",
            description = "权限: ROLE_ADMIN / ROLE_OPERATOR"
    )
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @GetMapping("/{id}")
    public ApiResponse<UserResponse> getUser(
            @Parameter(description = "用户 ID") @PathVariable Long id) {
        return ApiResponse.ok(userService.getUser(id));
    }

    @Operation(
            summary = "创建用户",
            description = "权限: ROLE_ADMIN。角色可选值: ROLE_ADMIN / ROLE_OPERATOR / ROLE_VIEWER"
    )
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserResponse> createUser(
            @Valid @RequestBody UserCreateRequest request) {
        return ApiResponse.ok(userService.createUser(request));
    }

    @Operation(
            summary = "修改用户",
            description = "权限: ROLE_ADMIN。所有字段均为可选，仅传需修改的字段"
    )
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ApiResponse<UserResponse> updateUser(
            @Parameter(description = "用户 ID") @PathVariable Long id,
            @Valid @RequestBody UserUpdateRequest request) {
        return ApiResponse.ok(userService.updateUser(id, request));
    }

    @Operation(
            summary = "删除用户",
            description = "权限: ROLE_ADMIN。物理删除，禁止删除默认 admin 账号"
    )
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteUser(
            @Parameter(description = "用户 ID") @PathVariable Long id) {
        userService.deleteUser(id);
        return ApiResponse.ok(null);
    }
}
