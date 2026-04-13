package com.camera.app.iam.controller;

import com.camera.app.common.exception.BusinessException;
import com.camera.app.common.response.ApiResponse;
import com.camera.app.iam.dto.LoginRequest;
import com.camera.app.iam.dto.LoginResponse;
import com.camera.app.iam.dto.UserInfoResponse;
import com.camera.app.iam.entity.User;
import com.camera.app.iam.repository.UserRepository;
import com.camera.app.security.JwtTokenProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.stream.Collectors;

@Tag(name = "认证", description = "登录/登出/当前用户")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    @Operation(summary = "用户登录，返回 JWT Token")
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        var auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));

        String token = jwtTokenProvider.generateToken(auth.getName());
        var roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        return ApiResponse.ok(new LoginResponse(token, auth.getName(), roles));
    }

    @Operation(summary = "登出（无状态，客户端丢弃 Token 即可）")
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        return ApiResponse.ok(null);
    }

    @Operation(summary = "获取当前登录用户信息", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/me")
    public ApiResponse<UserInfoResponse> me() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(404, "用户不存在"));

        var roles = user.getRoles().stream()
                .map(r -> r.getName())
                .collect(Collectors.toSet());

        return ApiResponse.ok(new UserInfoResponse(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getEmail(),
                user.isEnabled(),
                roles
        ));
    }
}
