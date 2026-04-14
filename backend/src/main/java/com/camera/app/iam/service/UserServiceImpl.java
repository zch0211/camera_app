package com.camera.app.iam.service;

import com.camera.app.common.exception.BusinessException;
import com.camera.app.common.response.PageResult;
import com.camera.app.iam.dto.UserCreateRequest;
import com.camera.app.iam.dto.UserResponse;
import com.camera.app.iam.dto.UserUpdateRequest;
import com.camera.app.iam.entity.Role;
import com.camera.app.iam.entity.User;
import com.camera.app.iam.repository.RoleRepository;
import com.camera.app.iam.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements UserService {

    private static final Set<String> ALLOWED_ROLES =
            Set.of("ROLE_ADMIN", "ROLE_OPERATOR", "ROLE_VIEWER");

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public PageResult<UserResponse> listUsers(String keyword, Boolean enabled, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Specification<User> spec = buildSpec(keyword, enabled);
        return new PageResult<>(userRepository.findAll(spec, pageable).map(UserResponse::new));
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUser(Long id) {
        return new UserResponse(findById(id));
    }

    @Override
    public UserResponse createUser(UserCreateRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException(409, "用户名已存在: " + request.getUsername());
        }
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname());
        user.setEmail(request.getEmail());
        user.setEnabled(request.isEnabled());
        user.setRoles(resolveRoles(request.getRoles()));
        return new UserResponse(userRepository.save(user));
    }

    @Override
    public UserResponse updateUser(Long id, UserUpdateRequest request) {
        User user = findById(id);
        if (request.getNickname() != null) {
            user.setNickname(request.getNickname());
        }
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }
        if (request.getEnabled() != null) {
            user.setEnabled(request.getEnabled());
        }
        if (request.getRoles() != null && !request.getRoles().isEmpty()) {
            user.setRoles(resolveRoles(request.getRoles()));
        }
        if (StringUtils.hasText(request.getPassword())) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        return new UserResponse(userRepository.save(user));
    }

    @Override
    public void deleteUser(Long id) {
        User user = findById(id);
        if ("admin".equals(user.getUsername())) {
            throw new BusinessException(403, "禁止删除默认管理员账号");
        }
        userRepository.delete(user);
    }

    private User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "用户不存在，id=" + id));
    }

    private Set<Role> resolveRoles(Set<String> roleNames) {
        Set<Role> roles = new HashSet<>();
        for (String name : roleNames) {
            if (!ALLOWED_ROLES.contains(name)) {
                throw new BusinessException(400, "无效角色: " + name
                        + "，可用角色: ROLE_ADMIN / ROLE_OPERATOR / ROLE_VIEWER");
            }
            roles.add(roleRepository.findByName(name)
                    .orElseThrow(() -> new BusinessException(500, "角色数据缺失: " + name)));
        }
        return roles;
    }

    private Specification<User> buildSpec(String keyword, Boolean enabled) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(keyword)) {
                String like = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("username")), like),
                        cb.like(cb.lower(root.get("nickname")), like)
                ));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
