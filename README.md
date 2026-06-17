# DeepIntern — 高校实习全流程管理系统

## 📖 项目简介

DeepIntern 是一套面向高等院校的**实习全生命周期管理平台**，覆盖学生从找实习、投递申请、实习过程记录、反思报告提交，到教师审核评分、企业招聘管理、数据统计分析等全部环节。系统支持四种角色协同作业，并通过 **DeepSeek 大模型** 深度集成 AI 能力，实现智能评分、实习报告分析、岗位描述生成等特色功能。

**产品名称：** DeepIntern  
**当前版本：** 0.0.1-SNAPSHOT  

---

## 🏗️ 技术架构

### 整体架构

```
┌─────────────────────────────────────────────────────┐
│                    前端 (Vue 3)                       │
│  Element Plus / ECharts / Pinia / Axios / Tiptap     │
├─────────────────────────────────────────────────────┤
│                  REST API + WebSocket                 │
├─────────────────────────────────────────────────────┤
│               后端 (Spring Boot 3.5.6)               │
│  Spring Security / JWT / MyBatis / Redis / Netty      │
│  Spring AI (DeepSeek) / Aliyun OSS / SMS / Mail      │
├─────────────────────────────────────────────────────┤
│                    数据层                             │
│              MySQL 8.0 / Redis 7.x                    │
│              阿里云 OSS（文件存储）                    │
└─────────────────────────────────────────────────────┘
```

### 技术栈明细

| 层级 | 技术 | 版本 |
|------|------|------|
| **后端框架** | Spring Boot | 3.5.6 |
| **JDK** | Java | 25 |
| **ORM** | MyBatis + MyBatis-Plus | 3.0.4 / 3.5.7 |
| **数据库** | MySQL | 8.0 |
| **缓存** | Redis（Lettuce 客户端） | — |
| **安全** | Spring Security + JWT | jjwt 0.12.5 |
| **AI** | Spring AI + DeepSeek | 1.0.2 |
| **API 文档** | SpringDoc OpenAPI (Swagger 3) | 2.7.0 |
| **前端框架** | Vue 3 + TypeScript | 3.5 |
| **构建工具** | Vite | 7.1 |
| **UI 组件库** | Element Plus | 2.14 |
| **可视化** | ECharts | 5.4 |
| **状态管理** | Pinia | 2.2 |
| **富文本编辑** | Tiptap | 2.4 |
| **文件处理** | Apache POI / PDFBox / iTextPDF | 5.4 / 3.0 / 5.5 |
| **消息推送** | WebSocket (Netty 4.1.115) | — |
| **云服务** | 阿里云 OSS / 短信服务 | — |

---

## 👥 用户角色体系

系统支持 **四种角色**，每种角色拥有独立的操作界面和权限体系：

### 1. 管理员（ROLE_ADMIN）
- 访问路径：`/admin/*`
- 系统全局管理：用户管理、院系专业管理、班级管理、企业管理
- 岗位类别管理、招聘管理、公告管理
- AI 模型配置、关键词库管理、评分规则配置
- 数据统计看板、备份管理、操作日志审计
- 系统设置与权限管理

### 2. 教师（多级子角色）
- 访问路径：`/teacher/*`
- 子角色：`ROLE_TEACHER`、`ROLE_TEACHER_COLLEGE`（院级）、`ROLE_TEACHER_DEPARTMENT`（系级）、`ROLE_TEACHER_COUNSELOR`（辅导员）
- 实习状态看板：实时监控学生实习进度
- 审核管理：企业注册审核、学生实习申请审核
- 智慧评分：AI 辅助评分 + 评分规则自定义
- 公告发布、资源管理、统计报表
- AI 分析配置、关键词库配置、评分规则管理
- 班级管理、学生管理、企业列表查看

### 3. 学生（ROLE_STUDENT）
- 访问路径：`/student/*`
- 浏览招聘岗位、投递简历
- 查看面试邀请、确认实习
- 实习进度记录、实习反思提交
- 个人档案维护
- 接收系统公告和提醒

### 4. 企业（ROLE_COMPANY）
- 访问路径：`/company/*`
- 企业注册与信息维护
- 发布招聘岗位、管理招聘流程
- 查看学生申请、发放面试邀请
- 实习确认

---

## 📁 项目结构

```
internship-system/
├── backend/                         # Spring Boot 后端
│   ├── src/main/java/com/internship/
│   │   ├── InternshipApplication.java   # 应用入口
│   │   ├── agent/                       # Netty 安全代理
│   │   ├── anno/                        # 自定义注解（@Log, @RequireTeacherPermission）
│   │   ├── aop/                         # AOP 切面（操作日志）
│   │   ├── auth/                        # Spring Security 安全配置
│   │   │   └── config/                  # JWT、密码加密、权限控制
│   │   ├── config/                      # 全局配置（AI、CORS、文件上传、Redis 等）
│   │   ├── controller/                  # 控制器层（50+ 控制器）
│   │   ├── entity/                      # 数据实体 + DTO
│   │   ├── mapper/                      # MyBatis Mapper 接口 + XML
│   │   ├── service/                     # 业务逻辑层
│   │   └── util/                        # 工具类
│   ├── src/main/resources/
│   │   ├── application.yml              # 主配置文件
│   │   └── com/internship/mapper/       # MyBatis XML 映射文件（50+）
│   ├── .env.example                     # 环境变量模板
│   ├── Dockerfile                       # Docker 容器化
│   ├── pom.xml                          # Maven 依赖管理
│   └── package.json                     # Node 脚本辅助
│
├── frontend/                        # Vue 3 前端
│   ├── src/
│   │   ├── views/                      # 页面视图
│   │   │   ├── admin/                  # 管理端页面（20+ 页面）
│   │   │   ├── teacher/                # 教师端页面（20+ 页面）
│   │   │   ├── student/                # 学生端页面（10+ 页面）
│   │   │   ├── company/                # 企业端页面
│   │   │   ├── Login.vue               # 登录页
│   │   │   ├── CompanyRegister.vue     # 企业注册
│   │   │   └── NotFound.vue            # 404 页面
│   │   ├── router/                     # 路由配置（含权限守卫）
│   │   ├── store/                      # Pinia 状态管理（auth、aiChat）
│   │   ├── api/                        # API 服务层（40+ 服务模块）
│   │   ├── components/                 # 通用组件
│   │   ├── composables/                # 组合式函数
│   │   ├── directives/                 # 自定义指令（懒加载、权限）
│   │   └── main.ts                     # 应用入口
│   ├── public/                         # 静态资源
│   ├── scripts/                        # 构建辅助脚本
│   ├── Dockerfile                      # Docker 容器化
│   ├── vite.config.js                  # Vite 构建配置
│   └── package.json                    # 前端依赖管理
│
├── docs/                            # 项目文档
├── load-tests/                      # 负载测试（k6 脚本）
└── README.md                        # 本文件
```

---

## 🚀 快速启动

### 前置环境

- JDK 25+
- Maven 3.8+
- Node.js 20+
- MySQL 8.0+
- Redis 7.x+

### 1. 数据库初始化

```sql
CREATE DATABASE internship DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

执行 `docs/` 或 `migration_temp/` 目录下的数据库初始化脚本（如有）。

### 2. 后端启动

```bash
cd backend/

# 复制环境变量模板并填写实际值
cp .env.example .env

# 编译并启动（开发环境）
mvn spring-boot:run
```

后端默认启动端口：`8080`  
Swagger 文档地址：`http://localhost:8080/swagger-ui.html`

### 3. 前端启动

```bash
cd frontend/

# 安装依赖
npm install

# 启动开发服务器（Vite HMR）
npm run dev
```

前端默认启动端口：`5173`  
访问地址：`http://localhost:5173`

### 4. 关键环境变量

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| `DB_HOST` | 数据库地址 | `127.0.0.1` |
| `DB_PORT` | 数据库端口 | `3306` |
| `DB_NAME` | 数据库名 | `internship` |
| `DB_USERNAME` | 数据库用户 | `user` |
| `DB_PASSWORD` | 数据库密码 | — |
| `REDIS_HOST` | Redis 地址 | `127.0.0.1` |
| `REDIS_PORT` | Redis 端口 | `6379` |
| `JWT_SECRET` | JWT 签名密钥 | — |
| `DEEPSEEK_API_KEY` | DeepSeek API Key | — |
| `ALIYUN_OSS_ACCESS_KEY_ID` | 阿里云 OSS AccessKey | — |
| `ALIYUN_OSS_BUCKET_NAME` | OSS Bucket 名称 | `your_bucket_name` |
| `MAIL_USERNAME` | 邮件服务账号 | — |
| `FILE_UPLOAD_PATH` | 文件上传本地路径 | `/path/to/uploads` |

---

## 🤖 AI 功能概览

系统通过 **Spring AI** 集成 **DeepSeek** 大模型，提供以下智能功能：

| 功能模块 | 说明 |
|----------|------|
| **实习报告 AI 分析** | 自动分析学生实习反思报告内容，提取关键信息 |
| **AI 辅助评分** | 基于评分规则 + AI 分析结果，生成评分建议 |
| **AI 岗位描述生成** | 自动生成招聘岗位描述文案 |
| **AI 鼓励语** | 根据学生实习状态生成个性化鼓励消息 |
| **AI 聊天** | 内嵌 AI 对话助手，支持实时问答 |
| **AI 审核记录** | 记录和追踪 AI 辅助审核的操作日志 |
| **辅导员 AI 分析** | 面向辅导员的专项 AI 分析工具 |

---

## 📊 核心业务流程

```
学生投递 → 企业面试 → 实习确认 → 实习过程记录
                                        ↓
                              实习反思报告提交
                                        ↓
                           AI 分析 + 教师评分
                                        ↓
                              成绩汇总 / 数据统计
```

---

## 🔐 安全机制

- **JWT 认证**：无状态 Token 认证，支持 Access Token + Refresh Token 双令牌机制
- **角色权限**：基于 RBAC 的细粒度权限控制，路由级 + 按钮级双重校验
- **Spring Security**：全链路安全拦截，自定义认证入口和权限拒绝处理
- **密码加密**：BCrypt 哈希存储
- **CORS 跨域**：白名单策略
- **操作日志**：AOP 切面自动记录关键操作，管理员可审计
- **数据库账号分离**：业务账号无 DDL 权限，物理隔离

---

## 🐳 Docker 部署

前后端均提供 `Dockerfile`，支持容器化部署：

```bash
# 后端构建
cd backend/
docker build -t deepintern-backend .

# 前端构建
cd frontend/
docker build -t deepintern-frontend .
```

---

## 📈 测试

### E2E 测试
项目集成了 Playwright 端到端测试框架：

```bash
cd frontend/
npx playwright test
```

### 负载测试
`load-tests/` 目录下包含 k6 负载测试脚本。

---

## 🔧 开发说明

- **代码风格**：后端遵循 Spring Boot 标准分层架构（Controller → Service → Mapper），前端采用 Vue 3 Composition API
- **前端 HMR**：Vite 热更新自动生效，改完前端代码无需重启 dev server
- **API 文档**：开发环境启动后访问 `/swagger-ui.html` 查看在线接口文档
- **数据库操作**：直接连接 MySQL 执行 DML，不生成脚本文件
- **远程仓库**：Gitee（主）+ GitHub（镜像）

---

## 📝 版本记录

| 版本 | 日期 | 说明 |
|------|------|------|
| 0.0.1-SNAPSHOT | — | 全功能开发版本 |

---

## 👨‍💻 贡献者

- 项目作者：luzujian
- 协作者：见 Git 提交记录
