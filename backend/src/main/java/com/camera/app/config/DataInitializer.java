package com.camera.app.config;

import com.camera.app.iam.entity.Role;
import com.camera.app.iam.entity.User;
import com.camera.app.iam.repository.RoleRepository;
import com.camera.app.iam.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedRoles();
        seedAdminUser();
    }

    private void seedRoles() {
        upsertRole("ROLE_ROOT",     "超级管理员");
        upsertRole("ROLE_ADMIN",    "系统管理员");
        upsertRole("ROLE_OPERATOR", "操作员");
        upsertRole("ROLE_VIEWER",   "只读用户");
    }

    private void upsertRole(String name, String description) {
        if (roleRepository.findByName(name).isEmpty()) {
            var role = new Role();
            role.setName(name);
            role.setDescription(description);
            roleRepository.save(role);
            log.info("Seeded role: {}", name);
        }
    }

    private void seedAdminUser() {
        if (!userRepository.existsByUsername("admin")) {
            var admin = new User();
            admin.setUsername("admin");
            admin.setNickname("超级管理员");
            admin.setPassword(passwordEncoder.encode("Admin@123"));
            admin.setEmail("admin@camera.local");
            Set<Role> roles = new HashSet<>();
            roles.add(roleRepository.findByName("ROLE_ROOT").orElseThrow());
            admin.setRoles(roles);
            userRepository.save(admin);
            log.info("Seeded default admin user (admin / Admin@123) with ROLE_ROOT");
        } else {
            upgradeAdminToRoot();
        }
    }

    private void upgradeAdminToRoot() {
        userRepository.findByUsername("admin").ifPresent(admin -> {
            boolean hasRoot = admin.getRoles().stream()
                    .anyMatch(r -> "ROLE_ROOT".equals(r.getName()));
            if (!hasRoot) {
                Role rootRole = roleRepository.findByName("ROLE_ROOT").orElseThrow();
                Set<Role> newRoles = new HashSet<>();
                newRoles.add(rootRole);
                admin.setRoles(newRoles);
                userRepository.save(admin);
                log.info("Upgraded admin user to ROLE_ROOT");
            }
        });
    }
}
