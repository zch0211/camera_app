# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 技术栈
- **后端**: Java 17, Spring Boot 3.2.x, Maven (`backend/`)
- **安全**: Spring Security 6 + JWT (JJWT 0.12.x, stateless)
- **文档**: Springdoc OpenAPI 2.x → `http://localhost:8080/swagger-ui.html`
- **ORM**: Spring Data JPA + Hibernate (ddl-auto=validate; Flyway owns the schema)
- **迁移**: Flyway — migrations in `backend/src/main/resources/db/migration/`
- **基础设施**: MySQL 8 / MinIO / RabbitMQ via `deploy/docker-compose.yml`

## 常用命令

```bash
# 启动基础设施
cd deploy && docker compose up -d

# 启动后端（profile=local 自动激活）
cd backend && ./mvnw spring-boot:run

# 编译
./mvnw compile

# 跑全部测试（需 MySQL 在线）
./mvnw test

# 跑单个测试类
./mvnw test -Dtest=ClassName

# 打可运行 jar
./mvnw package -DskipTests
```

## 项目结构

```
backend/src/main/java/com/camera/app/
├── CameraAppApplication.java       # 入口，@ConfigurationPropertiesScan
├── common/
│   ├── response/ApiResponse.java   # 统一返回 {code,message,data,traceId}
│   └── exception/
│       ├── BusinessException.java  # 业务异常（带 code 字段）
│       └── GlobalExceptionHandler  # @RestControllerAdvice 全局处理
├── security/
│   ├── JwtProperties.java          # @ConfigurationProperties("app.jwt")
│   ├── JwtTokenProvider.java       # 生成/解析/验证 JWT
│   ├── JwtAuthenticationFilter     # OncePerRequestFilter，注入 SecurityContext
│   └── SecurityConfig.java         # 白名单: /api/v1/auth/**, /health, /swagger-ui/**
├── iam/
│   ├── entity/{User,Role}.java     # users / roles / user_roles 表
│   ├── repository/                 # UserRepository, RoleRepository
│   ├── service/UserDetailsServiceImpl
│   ├── dto/{LoginRequest,LoginResponse}
│   └── controller/AuthController   # POST /api/v1/auth/login
├── system/SystemController.java    # GET /api/v1/system/health（无需认证）
└── config/
    ├── DataInitializer.java        # 启动时 seed roles + admin 用户
    └── OpenApiConfig.java          # Swagger bearerAuth scheme
```

## 关键约定

- **统一返回**: 所有接口返回 `ApiResponse<T>`，字段: `code / message / data / traceId`
- **异常**: 业务错误抛 `BusinessException(code, message)`；全局兜底由 `GlobalExceptionHandler` 处理
- **Schema**: 只用 Flyway 管理，不要开 `ddl-auto=create/update`；新表写 `V{n}__description.sql`
- **模块分包**: `iam / asset / poc / scan / alert / dashboard / audit`，每个模块自包含 entity/repo/service/controller
- **审计**: 写操作记录到 `audit_log` 表（user_id, action, resource_type, resource_id, ip_address, details）
- **JWT secret**: Base64 编码，解码后须 ≥ 32 bytes；生产环境通过环境变量 `APP_JWT_SECRET` 覆盖

## 默认账号

| 账号 | 密码 | 角色 |
|------|------|------|
| admin | Admin@123 | ROLE_ADMIN |

角色枚举: `ROLE_ADMIN` / `ROLE_OPERATOR` / `ROLE_VIEWER`
