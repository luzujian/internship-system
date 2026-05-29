# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

实习管理系统，基于 Spring Boot 3.5 + Vue 3 的前后端分离架构，支持学生、教师、企业、管理员四端协作，集成 AI 智能助手（DeepSeek）与实习心得智能评分功能。

## 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| 后端 | Spring Boot | 3.5.6 |
| 后端 | Java | 25 |
| 后端 | MyBatis-Plus | 3.5.7 |
| 后端 | Spring Security + JWT | 0.12.5 |
| 后端 | Spring AI + DeepSeek | 1.0.2 |
| 前端 | Vue 3 | 3.5.21 |
| 前端 | TypeScript | 5.9.3 |
| 前端 | Vite | 7.1.7 |
| 前端 | Element Plus | 2.11.4 |
| 前端 | Pinia | 2.2.6 |

## 开发命令

### 后端 (backend/)

```bash
cd backend
# 开发环境启动
./mvnw spring-boot:run

# 打包（跳过测试）
./mvnw clean package -DskipTests

# 运行打包后的 jar
java -jar target/Internship-0.0.1-SNAPSHOT.jar
```

### 前端 (frontend/)

```bash
cd frontend
# 安装依赖
npm install

# 开发服务器 (默认 http://localhost:5173)
npm run dev

# 生产构建
npm run build

# 预览构建结果
npm run preview
```

## 压测脚本 (k6)

```bash
# 安装 k6（首次使用）
winget install k6

# 冒烟测试（开发后验证，10秒 / 4 VU）
k6 run --env SMOKE=true --duration 10s --vus 4 load-tests/full-load-test.js

# 500 VU 摸底（上线前）
k6 run --env MEDIUM=true load-tests/full-load-test.js

# 竞态条件测试
k6 run load-tests/race-condition-test.js

# 幂等性测试
k6 run load-tests/idempotency-test.js
```

> **压测前准备**：将 `system_settings.max_login_attempts` 临时调至 50，测试账号密码设为 `123456`（测完恢复）。后端须以 `java -jar` 启动。

## 项目结构

```
internship-system/
├── backend/
│   ├── src/main/java/com/gdmu/
│   │   ├── controller/     # 60+ 个 Controller，处理 REST API
│   │   ├── service/        # 业务逻辑层
│   │   ├── mapper/        # MyBatis Mapper 接口
│   │   ├── entity/        # 实体类
│   │   ├── config/        # 配置类（AI、Redis、WebSocket、CORS等）
│   │   ├── enums/         # 枚举（状态、类型）
│   │   ├── exception/     # 异常处理
│   │   ├── interceptor/    # 拦截器
│   │   └── dto/           # 数据传输对象
│   ├── src/main/resources/
│   │   ├── mapper/*.xml   # MyBatis XML 映射文件
│   │   └── application.yml # 主配置文件
│   └── pom.xml
│
├── frontend/
│   ├── src/
│   │   ├── views/         # Vue 页面组件（按角色分目录：admin/student/teacher/company）
│   │   ├── api/           # Axios API 接口服务
│   │   ├── router/        # Vue Router 配置（各角色路由守卫）
│   │   ├── store/         # Pinia 状态管理
│   │   ├── utils/         # 工具函数（request, websocket, validation）
│   │   └── main.ts        # 前端入口
│   └── package.json
│
├── docs/                  # 文档（数据库ER图、测试报告、部署说明等）
└── nginx/                 # Nginx 配置
```

## 核心架构

### 后端分层
- **Controller 层**：接收 HTTP 请求，参数校验，调用 Service
- **Service 层**：业务逻辑处理，事务管理
- **Mapper 层**：MyBatis-Plus 操作数据库
- **配置类**：AIConfig（DeepSeek）、RedisConfig、CorsConfig、WebSocketConfig 等

### 前端架构
- **路由守卫**：authGuard（认证）、permissionGuard（权限）、logGuard（日志）、paramGuard（参数）
- **状态管理**：Pinia stores（auth、aiChat、teacherStore、position、systemSettings）
- **API 层**：按服务拆分（student.ts、teacher.ts、company.ts、adminService.ts 等）
- **四端分离**：studentRouter、teacherRouter、companyRouter、adminRouter

### 实习状态机
```
未找到岗位 → 已有Offer → 已确认 → 进行中 → 已结束
```
状态流转由 `validator/` 下的状态验证器统一管理。

## 数据库配置

```bash
# 环境变量方式（推荐）
DB_HOST=10.244.49.236
DB_PORT=3306
DB_NAME=internship
DB_USERNAME=root
DB_PASSWORD=1234
```

## AI 配置

DeepSeek API 通过 `spring.ai.deepseek` 配置，支持 DeepSeek Chat 模型。

## 端口

| 服务 | 端口 |
|------|------|
| 后端 API | 8080 |
| 前端 Dev | 5173 |
| MySQL | 3306 |
| Redis | 6379 |

## 重要约束

- **禁止使用 git**：必须先征得用户同意才能执行任何 git 命令
- **数据库修改**：必须直接连接执行，不生成脚本文件
- **敏感信息**：不要在代码或提交中包含 API keys、tokens 等

## 🚀 网站更新与项目部署规范

当用户发出"更新网站"、"部署项目"等指令时，**绝对不要尝试逐步执行编译或传输命令，也不要把文件转成 Base64**。你的标准动作是：

1. **直接执行自动化部署脚本**：在本地终端执行 `./deploy.sh`（在项目根目录下）以完成全自动打包与上传
2. **等待脚本运行成功**
3. **验证服务器状态**：使用 SSH MCP 工具连接服务器，执行 `docker ps` 和 `docker logs` 检查前后端容器是否正常运行
4. **向用户汇报部署完成及服务器运行状态**

### 部署脚本使用说明

```bash
# 项目根目录下执行
./deploy.sh
```

脚本会自动完成：
- 后端 Maven 打包
- 前端 npm 构建
- 直接 scp 传输二进制文件（不转 base64）
- 服务器端文件替换
- Docker 容器重启
- 部署结果验证