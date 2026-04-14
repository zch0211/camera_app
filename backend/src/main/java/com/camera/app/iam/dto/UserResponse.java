package com.camera.app.iam.dto;

import com.camera.app.iam.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@Schema(description = "用户信息")
public class UserResponse {

    @Schema(description = "用户 ID")
    private final Long id;

    @Schema(description = "用户名")
    private final String username;

    @Schema(description = "昵称")
    private final String nickname;

    @Schema(description = "邮箱")
    private final String email;

    @Schema(description = "是否启用")
    private final boolean enabled;

    @Schema(description = "角色列表")
    private final Set<String> roles;

    @Schema(description = "创建时间")
    private final LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private final LocalDateTime updatedAt;

    public UserResponse(User user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.nickname = user.getNickname();
        this.email = user.getEmail();
        this.enabled = user.isEnabled();
        this.roles = user.getRoles().stream()
                .map(r -> r.getName())
                .collect(Collectors.toSet());
        this.createdAt = user.getCreatedAt();
        this.updatedAt = user.getUpdatedAt();
    }
}
