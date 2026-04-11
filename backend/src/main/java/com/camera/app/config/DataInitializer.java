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
            admin.setPassword(passwordEncoder.encode("Admin@123"));
            admin.setEmail("admin@camera.local");
            admin.setRoles(Set.of(roleRepository.findByName("ROLE_ADMIN").orElseThrow()));
            userRepository.save(admin);
            log.info("Seeded default admin user (admin / Admin@123)");
        }
    }
}
