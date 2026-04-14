package com.camera.app.iam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Set;

@Data
@Schema(description = "创建用户请求")
public class UserCreateRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 64, message = "用户名长度 3-64 位")
    @Schema(description = "用户名（唯一，3-64 位）", example = "operator01")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, message = "密码最少 6 位")
    @Schema(description = "密码（明文，最少 6 位）", example = "Pass@123")
    private String password;

    @Size(max = 64, message = "昵称最长 64 位")
    @Schema(description = "昵称", example = "操作员01")
    private String nickname;

    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱最长 128 位")
    @Schema(description = "邮箱", example = "op01@camera.local")
    private String email;

    @Schema(description = "是否启用（默认 true）", example = "true")
    private boolean enabled = true;

    @NotEmpty(message = "角色不能为空")
    @Schema(description = "角色列表，可选值: ROLE_ADMIN / ROLE_OPERATOR / ROLE_VIEWER",
            example = "[\"ROLE_OPERATOR\"]")
    private Set<String> roles;
}
