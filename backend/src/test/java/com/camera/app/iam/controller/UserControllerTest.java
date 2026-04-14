package com.camera.app.iam.controller;

import com.camera.app.common.exception.BusinessException;
import com.camera.app.common.response.PageResult;
import com.camera.app.iam.dto.UserCreateRequest;
import com.camera.app.iam.dto.UserResponse;
import com.camera.app.iam.entity.Role;
import com.camera.app.iam.entity.User;
import com.camera.app.iam.service.UserService;
import com.camera.app.security.JwtTokenProvider;
import com.camera.app.security.SecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice test for UserController.
 *
 * Uses @Import(SecurityConfig.class) so that:
 *  - The real SecurityFilterChain (with @EnableMethodSecurity) is active.
 *  - JwtTokenProvider is mocked → JWT filter always passes through.
 *  - @WithMockUser sets up the SecurityContext (no real DB needed).
 */
@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    UserService userService;

    /** Replaces real JwtTokenProvider — validate() returns false → JWT filter skips. */
    @MockBean
    JwtTokenProvider jwtTokenProvider;

    /** Replaces UserDetailsServiceImpl (a @Service, not loaded by @WebMvcTest). */
    @MockBean
    UserDetailsService userDetailsService;

    private UserResponse sampleUser;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setUsername("operator01");
        user.setNickname("操作员01");
        user.setEmail("op01@camera.local");
        user.setEnabled(true);
        Role role = new Role();
        role.setName("ROLE_OPERATOR");
        user.setRoles(Set.of(role));
        sampleUser = new UserResponse(user);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 1. admin 可查看用户列表
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanListUsers() throws Exception {
        PageResult<UserResponse> pageResult =
                new PageResult<>(new PageImpl<>(List.of(sampleUser), PageRequest.of(0, 20), 1));
        when(userService.listUsers(any(), any(), anyInt(), anyInt())).thenReturn(pageResult);

        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content[0].username").value("operator01"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 2. operator 可查看用户列表
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @WithMockUser(roles = "OPERATOR")
    void operatorCanListUsers() throws Exception {
        PageResult<UserResponse> pageResult =
                new PageResult<>(new PageImpl<>(List.of(sampleUser), PageRequest.of(0, 20), 1));
        when(userService.listUsers(any(), any(), anyInt(), anyInt())).thenReturn(pageResult);

        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isOk());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 3. viewer 不能访问用户管理接口
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @WithMockUser(roles = "VIEWER")
    void viewerCannotListUsers() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isForbidden());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 4. admin 可创建用户
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanCreateUser() throws Exception {
        UserCreateRequest req = new UserCreateRequest();
        req.setUsername("newuser");
        req.setPassword("Pass@123");
        req.setNickname("新用户");
        req.setEmail("new@camera.local");
        req.setEnabled(true);
        req.setRoles(Set.of("ROLE_OPERATOR"));

        when(userService.createUser(any())).thenReturn(sampleUser);

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 5. operator 不能创建用户
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @WithMockUser(roles = "OPERATOR")
    void operatorCannotCreateUser() throws Exception {
        UserCreateRequest req = new UserCreateRequest();
        req.setUsername("newuser");
        req.setPassword("Pass@123");
        req.setRoles(Set.of("ROLE_OPERATOR"));

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 6. 用户名重复创建失败 → 409
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @WithMockUser(roles = "ADMIN")
    void duplicateUsernameShouldReturn409() throws Exception {
        UserCreateRequest req = new UserCreateRequest();
        req.setUsername("admin");
        req.setPassword("Pass@123");
        req.setRoles(Set.of("ROLE_ADMIN"));

        when(userService.createUser(any()))
                .thenThrow(new BusinessException(409, "用户名已存在: admin"));

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 7. 查询不存在用户返回 404
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @WithMockUser(roles = "ADMIN")
    void getUserNotFoundShouldReturn404() throws Exception {
        when(userService.getUser(999L))
                .thenThrow(new BusinessException(404, "用户不存在，id=999"));

        mockMvc.perform(get("/api/v1/users/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 8. operator 可查看用户详情，不能修改/删除
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @WithMockUser(roles = "OPERATOR")
    void operatorCanGetUserDetail() throws Exception {
        when(userService.getUser(1L)).thenReturn(sampleUser);

        mockMvc.perform(get("/api/v1/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("operator01"));
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void operatorCannotDeleteUser() throws Exception {
        mockMvc.perform(delete("/api/v1/users/1"))
                .andExpect(status().isForbidden());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 9. 校验失败 → 400
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @WithMockUser(roles = "ADMIN")
    void createUserWithBlankUsernameShouldReturn400() throws Exception {
        UserCreateRequest req = new UserCreateRequest();
        req.setUsername("ab"); // too short, < 3 chars
        req.setPassword("Pass@123");
        req.setRoles(Set.of("ROLE_OPERATOR"));

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }
}
