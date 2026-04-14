package com.camera.app.iam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Set;

@Data
@Schema(description = "修改用户请求（所有字段可选，仅传需修改的字段）")
public class UserUpdateRequest {

    @Size(max = 64, message = "昵称最长 64 位")
    @Schema(description = "昵称", example = "操作员02")
    private String nickname;

    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱最长 128 位")
    @Schema(description = "邮箱", example = "op02@camera.local")
    private String email;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "角色列表，可选值: ROLE_ADMIN / ROLE_OPERATOR / ROLE_VIEWER",
            example = "[\"ROLE_VIEWER\"]")
    private Set<String> roles;

    @Size(min = 6, message = "密码最少 6 位")
    @Schema(description = "新密码（可选，不传则不修改密码）", example = "NewPass@456")
    private String password;
}
