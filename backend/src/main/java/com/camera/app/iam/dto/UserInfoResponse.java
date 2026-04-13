package com.camera.app.iam.dto;

import lombok.Getter;

import java.util.Set;

@Getter
public class UserInfoResponse {
    private final Long id;
    private final String username;
    private final String nickname;
    private final String email;
    private final boolean enabled;
    private final Set<String> roles;

    public UserInfoResponse(Long id, String username, String nickname, String email,
                            boolean enabled, Set<String> roles) {
        this.id = id;
        this.username = username;
        this.nickname = nickname;
        this.email = email;
        this.enabled = enabled;
        this.roles = roles;
    }
}
