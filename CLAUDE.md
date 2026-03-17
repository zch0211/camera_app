# 项目：视频监控系统安全态势智能感知平台（camera_app）

## 技术栈（默认）
- 后端：Java 17, Spring Boot 3.x, Maven
- 安全：Spring Security + JWT
- 文档：Springdoc OpenAPI (Swagger UI)
- DB：MySQL 8（Flyway 管理迁移）
- 队列：RabbitMQ（先简单）
- 对象存储：MinIO（POC与证据）
- 实时：WebSocket（大屏推送）
- 运行：Docker Compose（本地一键启动）

## MVP范围（必须先做可运行闭环）
1) 登录 + RBAC（admin/operator/viewer）
2) 资产管理（CRUD、筛选、详情）
3) POC仓库（上传POC文件到MinIO + 元数据入库）
4) 扫描任务（创建任务、任务状态、结果入库）
5) 告警中心（POC命中 -> 告警）
6) 大屏接口（聚合指标API）+ WebSocket 推送基础事件

## 代码规范
- 模块化单体分包：iam/asset/poc/scan/alert/dashboard/audit
- 统一返回：{code,message,data,traceId}
- 全量异常处理：GlobalExceptionHandler
- 写操作记录审计（audit_log）
- 所有接口补 OpenAPI 注解，可在 /swagger-ui.html 查看