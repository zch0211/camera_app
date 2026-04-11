package com.camera.app.iam.dto;

import lombok.Getter;

import java.util.Set;

@Getter
public class LoginResponse {
    private final String token;
    private final String tokenType = "Bearer";
    private final String username;
    private final Set<String> roles;

    public LoginResponse(String token, String username, Set<String> roles) {
        this.token = token;
        this.username = username;
        this.roles = roles;
    }
}
