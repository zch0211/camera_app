# camera_app — 视频监控系统安全态势智能感知平台

## 本地快速启动

### 前置条件
- Docker Desktop（运行 MySQL / MinIO / RabbitMQ）
- JDK 17+
- Maven 3.9+（或直接用 `mvnw`）

---

### 第一步：启动基础设施

```bash
cd deploy
docker compose up -d
```

等待 MySQL 健康检查通过（约 30 秒）：

```bash
docker compose ps          # 确认 camera_mysql 状态为 healthy
```

---

### 第二步：启动后端

```bash
cd backend
./mvnw spring-boot:run
# Windows CMD: mvnw.cmd spring-boot:run
```

首次启动 Flyway 自动建表，`DataInitializer` 自动写入默认账号。

---

### 第三步：验证

| 地址 | 说明 |
|------|------|
| `GET  http://localhost:8080/api/v1/system/health` | 健康检查（无需认证） |
| `http://localhost:8080/swagger-ui.html` | Swagger UI |
| `POST http://localhost:8080/api/v1/auth/login` | 登录获取 Token |

---

## 默认账号

| 账号 | 密码 | 角色 |
|------|------|------|
| `admin` | `Admin@123` | ROLE_ADMIN |

登录示例：

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@123"}' | jq .
```

返回的 `token` 放入后续请求头：`Authorization: Bearer <token>`

---

## 基础设施服务端口

| 服务 | 端口 | 默认账号 |
|------|------|---------|
| MySQL 8 | 3306 | camera / camera123 |
| MinIO API | 9000 | minioadmin / minioadmin123 |
| MinIO Console | 9001 | minioadmin / minioadmin123 |
| RabbitMQ AMQP | 5672 | camera / camera123 |
| RabbitMQ 管控台 | 15672 | camera / camera123 |

---

## 常用 Maven 命令

```bash
# 编译
./mvnw compile

# 跑测试（需 MySQL 在线）
./mvnw test

# 打包可运行 jar
./mvnw package -DskipTests

# 只跑单个测试类
./mvnw test -Dtest=CameraAppApplicationTests
```

---

## 停止并清理

```bash
cd deploy
docker compose down          # 保留数据卷
docker compose down -v       # 同时删除数据卷
```
