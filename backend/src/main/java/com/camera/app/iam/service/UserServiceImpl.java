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
import org.springframework.security.core.context.SecurityContextHolder;
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

    private static final String ROLE_ROOT     = "ROLE_ROOT";
    private static final String ROLE_ADMIN    = "ROLE_ADMIN";
    private static final String ROLE_OPERATOR = "ROLE_OPERATOR";
    private static final String ROLE_VIEWER   = "ROLE_VIEWER";

    /** All valid role names in the system */
    private static final Set<String> ALL_VALID_ROLES =
            Set.of(ROLE_ROOT, ROLE_ADMIN, ROLE_OPERATOR, ROLE_VIEWER);
    /** Roles that ROOT callers may assign (ROOT excluded by policy) */
    private static final Set<String> ROOT_ASSIGNABLE =
            Set.of(ROLE_ADMIN, ROLE_OPERATOR, ROLE_VIEWER);
    /** Roles that ADMIN callers may assign */
    private static final Set<String> ADMIN_ASSIGNABLE =
            Set.of(ROLE_OPERATOR, ROLE_VIEWER);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    // ─── public API ───────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public PageResult<UserResponse> listUsers(String keyword, Boolean enabled, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return new PageResult<>(userRepository.findAll(buildSpec(keyword, enabled), pageable)
                .map(UserResponse::new));
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
        Set<String> normalizedRoles = normalizeRoleNames(request.getRoles());
        validateAssignableRoles(normalizedRoles);
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname());
        user.setEmail(request.getEmail());
        user.setEnabled(request.isEnabled());
        user.setRoles(resolveRoles(normalizedRoles));
        return new UserResponse(userRepository.save(user));
    }

    @Override
    public UserResponse updateUser(Long id, UserUpdateRequest request) {
        User user = findById(id);
        validateCallerCanManageTarget(user);

        Set<String> normalizedRoles = null;
        if (request.getRoles() != null) {
            if (request.getRoles().isEmpty()) {
                throw new BusinessException(400, "角色列表不能为空，至少需要指定一个角色");
            }
            normalizedRoles = normalizeRoleNames(request.getRoles());
            validateAssignableRoles(normalizedRoles);
            // Protect last ROOT from role downgrade
            if (isRoot(user) && !normalizedRoles.contains(ROLE_ROOT)) {
                guardLastRoot(user.getId(),
                        "无法降权最后一个 ROOT 用户，系统至少需保留一个 ROOT 账户");
            }
        }
        // Protect last enabled ROOT from being disabled
        if (Boolean.FALSE.equals(request.getEnabled()) && isRoot(user)) {
            if (userRepository.countEnabledByRoleName(ROLE_ROOT) <= 1) {
                throw new BusinessException(403,
                        "系统至少需保留一个启用状态的 ROOT 账户，无法禁用最后一个启用的 ROOT 用户");
            }
        }

        if (request.getNickname() != null) user.setNickname(request.getNickname());
        if (request.getEmail()    != null) user.setEmail(request.getEmail());
        if (request.getEnabled()  != null) user.setEnabled(request.getEnabled());
        if (normalizedRoles      != null) user.setRoles(resolveRoles(normalizedRoles));
        if (StringUtils.hasText(request.getPassword())) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        return new UserResponse(userRepository.save(user));
    }

    @Override
    public void deleteUser(Long id) {
        User user = findById(id);
        validateCallerCanManageTarget(user);
        if ("admin".equals(user.getUsername())) {
            throw new BusinessException(403, "默认管理员账号不允许删除");
        }
        if (isRoot(user)) {
            guardLastRoot(user.getId(),
                    "无法删除最后一个 ROOT 用户，系统至少需保留一个 ROOT 账户");
        }
        userRepository.delete(user);
    }

    // ─── permission helpers ───────────────────────────────────────────────────

    private boolean callerIsRoot() {
        return SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream()
                .anyMatch(a -> ROLE_ROOT.equals(a.getAuthority()));
    }

    private boolean isRoot(User user) {
        return user.getRoles().stream().anyMatch(r -> ROLE_ROOT.equals(r.getName()));
    }

    private boolean isAdminOrAbove(User user) {
        return user.getRoles().stream()
                .anyMatch(r -> ROLE_ROOT.equals(r.getName()) || ROLE_ADMIN.equals(r.getName()));
    }

    /**
     * ROOT passes freely.
     * ADMIN callers may not manage ROOT or ADMIN targets.
     */
    private void validateCallerCanManageTarget(User target) {
        if (callerIsRoot()) return;
        if (isRoot(target)) {
            throw new BusinessException(403, "无权管理该用户：普通管理员不能修改超级管理员账户");
        }
        if (isAdminOrAbove(target)) {
            throw new BusinessException(403, "无权管理该用户：普通管理员不能修改管理员账户");
        }
    }

    /**
     * Normalize a role name to ROLE_* format.
     * Accepts both full names (ROLE_OPERATOR) and short names (OPERATOR / VISITOR).
     */
    private static String normalizeRoleName(String name) {
        if (name == null) return null;
        String upper = name.trim().toUpperCase();
        if (upper.startsWith("ROLE_")) return upper;
        return switch (upper) {
            case "ROOT"     -> ROLE_ROOT;
            case "ADMIN"    -> ROLE_ADMIN;
            case "OPERATOR" -> ROLE_OPERATOR;
            case "VIEWER"   -> ROLE_VIEWER;
            case "VISITOR"  -> ROLE_VIEWER;  // frontend alias for VIEWER
            default         -> upper;         // pass through; will fail validation with clear message
        };
    }

    private static Set<String> normalizeRoleNames(Set<String> names) {
        Set<String> result = new HashSet<>();
        for (String n : names) result.add(normalizeRoleName(n));
        return result;
    }

    /**
     * Validate that the current caller is permitted to assign the given (already-normalized) roles.
     * ROOT: may assign ADMIN / OPERATOR / VIEWER.
     * ADMIN: may only assign OPERATOR / VIEWER.
     */
    private void validateAssignableRoles(Set<String> normalizedRoles) {
        Set<String> assignable = callerIsRoot() ? ROOT_ASSIGNABLE : ADMIN_ASSIGNABLE;
        for (String r : normalizedRoles) {
            if (!ALL_VALID_ROLES.contains(r)) {
                throw new BusinessException(400,
                        "无效角色值: " + r + "。可选值: ROLE_ADMIN / ROLE_OPERATOR / ROLE_VIEWER"
                        + "（也接受简写: ADMIN / OPERATOR / VIEWER / VISITOR）");
            }
            if (!assignable.contains(r)) {
                String allowed = callerIsRoot()
                        ? "ROLE_ADMIN / ROLE_OPERATOR / ROLE_VIEWER"
                        : "ROLE_OPERATOR / ROLE_VIEWER";
                throw new BusinessException(403,
                        "无法分配角色 " + r + "：超出当前操作者的可分配范围。可分配角色: " + allowed);
            }
        }
    }

    /** Throw 403 if the given user is the last ROOT account. */
    private void guardLastRoot(Long userId, String message) {
        if (userRepository.countByRoleName(ROLE_ROOT) <= 1) {
            throw new BusinessException(403, message);
        }
    }

    // ─── data helpers ─────────────────────────────────────────────────────────

    private User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "用户不存在，id=" + id));
    }

    private Set<Role> resolveRoles(Set<String> roleNames) {
        Set<Role> roles = new HashSet<>();
        for (String name : roleNames) {
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
