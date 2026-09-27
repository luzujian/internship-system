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
│   ├── pom.xml                          # Maven 依赖管理
│   └── .env                             # 零配置开发环境
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
│   ├── vite.config.js                  # Vite 构建配置
│   └── package.json                    # 前端依赖管理
│
└── README.md                        # 本文件
```

---

## 🚀 快速启动

### 前置环境

- JDK 25+
- Maven 3.8+
- Node.js 20+

> **本地开发**：先准备本地 MySQL 和 Redis，核对并迁移数据库，再显式启用下方的 `local` profile。不要直接使用共享服务器的连接配置。

### 1. 克隆项目

```powershell
git clone <repo-url>
cd internship-system
```

### 2. 后端启动

```powershell
cd backend/
# 先通过本机环境设置 LOCAL_DB_PASSWORD 与 JWT_SECRET，密钥不要写入仓库。
$env:SPRING_PROFILES_ACTIVE = 'local'
mvn spring-boot:run
```

后端默认启动端口：`8080`  
Swagger 文档地址：`http://localhost:8080/swagger-ui.html`

### 3. 前端启动

```powershell
cd frontend/
npm ci
npm run dev
```

前端默认启动端口：`5173`  
访问地址：`http://localhost:5173`

### 关键环境变量

本地开发请显式启用 `local` profile，并通过进程环境提供 `LOCAL_DB_PASSWORD`、`JWT_SECRET`。该 profile 固定连接 `127.0.0.1:3306/internship`（业务账号 `user`）和本地 Redis 的 13 号库，不会回退到远程数据库。启动时不会自动迁移数据库；后台定时任务、启动实习状态同步、外部邮件与 OSS 操作在该 profile 下禁用。原业务服务和手动页面继续可用。

| 变量名 | 说明 | 当前值 |
|--------|------|--------|
| `DB_HOST` | 数据库地址 | `local` profile 固定 `127.0.0.1`；其他环境必须核对服务器配置 |
| `DB_PORT` | 数据库端口 | `3306` |
| `DB_NAME` | 数据库名 | `internship` |
| `REDIS_HOST` | Redis 地址 | `local` profile 固定 `127.0.0.1:6379`，数据库 13 |
| `JWT_SECRET` | JWT 签名密钥 | 通过本机环境设置，至少 32 字节 |
| `DEEPSEEK_API_KEY` | DeepSeek API Key | 本地通过进程环境变量注入，不写入仓库 |
| `FILE_UPLOAD_PATH` | 文件上传路径（自动创建） | `./uploads` |

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
| **统一 AI 侧栏与系统顾问** | 四端共用侧栏，按角色提供系统功能、流程、入口和真实业务状态问答 |
| **语音/文字智能填表** | 感知当前页面或弹窗表单，将口述或文字内容写回 Vue 响应式字段，支持连续修改、撤销和冲突保护 |
| **学生实习规划与陪跑** | 按真实进度持续更新阶段、缺项和下一步，计划持久化并处理换岗、延期等分支 |
| **教师条件触发自动化** | 自然语言生成任务预览，获授权的辅导员、系教师、学院教师分别在本班、本系、本学院范围确认启用，按条件执行站内/邮件提醒，并保留执行记录 |
| **管理员 AI 能力治理** | 按角色或用户配置对话、语音、填表、规划、投递、自动任务和通知渠道，权限只收窄不放大 |

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

统一 Agent 架构、交互、能力授权与验收标准见 `backend/src/main/resources/docs/AGENT_DESIGN.md`。阶段六发布、功能开关、固定时间调度语义和回退步骤见
`backend/src/main/resources/docs/PHASE6_RELEASE.md`。

AI 页面操作直接读取当前身份的菜单入口、路由关联的表单清单及已打开表单的字段结构。新增页面使用 `agentPage` 关联原组件与 `SUBMIT_FORMS`，菜单保留 `data-agent-route`；只为打开表单的按钮添加 `data-agent-open`，不要给保存、删除或提交按钮添加此标记。AI 只选择当前可用入口，原页面权限及校验继续生效，写入表单后仍由用户保存或提交。

企业基础信息页面与 AI 查询共用 `backend/src/main/resources/agent/company-info-schema.json` 中的字段、选项和标签定义；调整这些定义时修改同一来源，不另维护 AI 专用解释副本。查询工具按当前登录企业读取最新已保存资料，不将历史回答当作当前数据，未登记的标签含义保持未知。

AI 执行边界：`my_permissions.availableQueries` 从当前角色的实际工具定义生成，包含参数结构，不维护第二份能力列表。每次查询（含重复查询缓存命中）重新核对工具授权；分页结果提供 `returnedCount`、`hasMore` 和有后续页时的 `nextPage`，本页条数不代替总数。模型返回的页面目标必须在当次候选中，填表另验 `form_fill` 权限；同一账号同时只处理一个页面意图请求，当前进程最多并发八个，多实例部署还需网关限流。

页面执行等待实际入口稳定，检查页面、表单实例、身份与会话变化，取消后不继续操作；循环选择和未完成填表不能回报为成功。新增安全入口必须只打开页面或表单，不得绑定业务提交；正式写操作沿用服务端确认、对象归属与幂等校验。浏览器故障回归：设置本地测试账号 `E2E_PW_COMPANY`（可选 `E2E_COMPANY`、`E2E_BASE_URL`），在 `frontend` 执行 `npx playwright test --config=playwright.harness.config.ts`。这些测试使用受控模型响应验证取消、切页、关闭、循环与输入竞态，不能替代真实模型验收。

---

## 📈 测试

### 前端测试

```bash
cd frontend/
npm test
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


## DeepIntern 侧栏本地迁移与验收

按 `AGENTS.md` 获取管理员 DDL 授权后，先用业务账号只读检查实际结构，备份受影响的本地表，再用管理员账号依次执行：

1. `backend/src/main/resources/db/migration/V20260920_01__agent_history_actions.sql`
2. `backend/src/main/resources/db/migration/V20260920_02__agent_notification_guard.sql`
3. `backend/src/main/resources/db/migration/V20260920_03__teacher_task_runs.sql`

迁移新增会话、草稿、消息版本对应的生成任务、时间线事件、请求幂等记录、动作回执、通知占用和调度运行记录。第二个迁移的 `ALTER` 语句须逐列核对后执行，只补缺失列；配置值改为 `TEXT` 以容纳完整角色及通知策略。业务账号仍仅需 SELECT/INSERT/UPDATE/DELETE，不授予 DDL 权限。Redis 只用于限流和短时协调，不保存唯一历史或成功凭证。

回退时先关闭 AI 能力入口并暂停教师自动跟进，再恢复兼容的旧代码。保留新增表、列、聊天、动作回执及通知占用，尤其不能清空 `PENDING` / `UNKNOWN` 记录来重试通知。确认结果需核对真实业务/邮件服务记录；回退不撤销已提交业务或已发送通知。需要恢复备份时应另行制定并确认数据恢复范围，不直接覆盖迁移后的业务数据。

百炼实时语音使用浏览器 AudioWorklet 采集单声道 16 kHz PCM，经鉴权 WebSocket 转接；服务端环境变量为 `BAILIAN_ASR_API_KEY`，`agent.voice.allowed-origins` 必须配置实际前端来源。默认不保存原始音频。未配置密钥时明确提示不可用，文字输入保留。停止录音会等待尾句转写并保留可编辑文字，用户明确发送后才解析填表。

登记的 59 个表单使用统一的实例、字段版本、原表单校验与批次撤销机制。保存范围分别为：57 个接入原服务端业务结果，面试信息模板保存到当前浏览器，实习去向登记目前仅为本页草稿（原页面没有后端提交接口）。回执必须来自对应请求明确成功的分支，填写记录本身不能证明已保存；修改过的旧值、否定修改指令和未明确授权的覆盖会保留。清空也须明确指定字段。

本地验证命令（数据库集成测试使用已迁移的本地库，只操作隔离的测试记录，不执行 DDL、不发送邮件）：

```powershell
cd backend
$env:DB_HOST = '127.0.0.1'
$env:REDIS_HOST = '127.0.0.1'
$env:AGENT_LOCAL_DB_TESTS = 'true'
mvn '-Dtest=AgentLocalPersistenceTest' test
cd ../frontend
npm test
npm run build
npx playwright test --config=playwright.batch4.config.ts
```

行为单测、模拟接口的真实浏览器测试、本地 MySQL 事务/并发测试分别记录。百炼真实模型、真实麦克风、真实邮件及 Android Chrome / iPhone Safari 实机验收必须独立完成；没有这些证据，不能标记完整上线验收通过。开发环境不开自动调度，启用任务也不会在该 profile 下自行发送。


### AI 业务查询与权限边界

AI 查询由服务端登录身份、角色工具白名单和记录归属共同限制；页面上下文、模型参数、用户自称身份均不能授予权限。每次工具调用前后重新检查账号、对话能力与教师范围，撤权或负责范围改变即中止当前任务。

| 查询 | 数据来源与边界 |
| --- | --- |
| 统一实习时间 | 教师统一日历，起止日期、鉴定截止和心得周期；不从个人记录推断 |
| 学生岗位申请 | 登录学生的岗位申请记录单独计数，不混入普通申请、面试、确认 |
| 学生基本资料 | 本人姓名、学号、年级、学院、系、专业、班级、联系方式是否已填写；缺项明确标记，不用旧文本推测组织归属 |
| 学生当前实习与历史 | 复用业务状态派生服务，按 currentFlag 区分经历；不从历史 Offer 推测正在实习，不使用个人起止日期替代统一安排 |
| 综合学生顾问背景 | 按需合并本人资料、负责辅导员、当前实习、待办及统一日历；不自动逐项复述背景 |
| 学生负责老师 | 本人班级实际关联的有效辅导员，只返回当前姓名和身份；未关联班级与无有效关联分别说明 |
| 学生心得 | 本人期数、截止和真实提交状态；不输出全文、联系方式或其他学生数据 |
| 学生鉴定 | 本人已有鉴定状态、附件数量、退回意见；只读查询不创建草稿，不暴露附件地址 |
| 学生进度、待办与计划 | 本人真实业务记录，普通申请、Offer、确认、当前经历分别表达 |
| 教师待办、心得与简报 | 辅导员负责班级、系教师本系、学院教师本学院；空范围不扩大；历史范围再次校验 |
| 企业岗位、申请与确认 | 企业 ID 取登录身份；指定岗位必须属于本企业 |
| 管理员概览与企业审核队列 | 现有管理端只读投影，不开放任意 SQL 或任意个人资料查询 |

知识解释不能作为个人状态证据。关键本人提交状态、岗位申请数量和统一日期需要对应的本轮成功查询；查询异常终止回答，不转换成零条记录。只有成功查询确无记录才可说当前范围未查到，配置字段确实为空才可说尚未设置。未接入查询必须明确说明暂不支持，不能声称系统没有该功能。本清单是已接入边界，不代表所有业务页面的所有字段都已接入 AI。

本地只读数据范围集成测试：设置 `DEEPINTERN_LOCAL_READ_QA=true`、`DEEPINTERN_DB_URL` 为本机 MySQL URL、`DEEPINTERN_DB_PASSWORD` 为业务账号密码后运行 `mvn -Dtest=AgentReadScopeLocalTest test`。测试只允许 `127.0.0.1:3306/internship`，不执行 DDL 或业务写入。
