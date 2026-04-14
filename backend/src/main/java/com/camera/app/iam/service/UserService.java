package com.camera.app.iam.service;

import com.camera.app.common.response.PageResult;
import com.camera.app.iam.dto.UserCreateRequest;
import com.camera.app.iam.dto.UserResponse;
import com.camera.app.iam.dto.UserUpdateRequest;

public interface UserService {

    PageResult<UserResponse> listUsers(String keyword, Boolean enabled, int page, int size);

    UserResponse getUser(Long id);

    UserResponse createUser(UserCreateRequest request);

    UserResponse updateUser(Long id, UserUpdateRequest request);

    void deleteUser(Long id);
}
