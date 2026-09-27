# 统一 Agent 设计（阶段二交付）

> 版本：2026-09-16 ｜ 适用范围：DeepIntern 智能体化改造阶段二～四
> 状态：已实现（代码为准），本文固化总体架构、交互原型、能力授权方案、实施顺序与验收标准。

## 1. 总体架构

- 同一助手、不同能力：四端共用 AI 侧栏（`AgentWorkspace.vue`）与后端 `/api/agent`，按角色返回不同工具与能力，不拆四套 Agent。
- 职责划分（与 Spring AI 工具调用一致）：模型只负责理解与提出工具调用请求；应用负责真实执行并回传结果。
  - 只读：`AgentReadTools` 白名单（按角色注册工具）+ `validateObject` 对象鉴权 + `scopeFingerprint` 数据范围三层约束。
  - 写入（仅两处）：`JobApplyExecuteService.apply/withdraw`（需前端确认弹窗 `confirmed=true`）；`FillIntentService` 只产字段赋值意图，真实写入由前端写 Vue 响应式 model（不触发提交）。
- 会话与任务：`AgentStore` 保存会话/任务/幂等键；`AgentTaskService` 负责排队、执行、快照 SSE（可断线续传）、预算（工具调用≤12、模型轮次≤6）。
- 知识：`agent/knowledge.json` 按角色过滤，`knowledge_lookup` 工具查询；回答必须结合真实查询，不得以规则解释代替业务状态。

## 2. 交互原型（首版冻结）

- 入口：各端布局右侧 AI 侧栏（`AgentEntry` 打开，`AgentWorkspace` 承载）；管理员只读身份仅可问答。
- 输入路由（文字与语音共用同一条链路）：
  1. 若已关联表单且开通 `form_fill` → 先走 `/api/agent/forms/fill-intent`；
  2. 无赋值意图或表单已关闭 → 自动回落为顾问问答 `POST /api/agent/tasks`。
- 表单上下文：页面/弹窗打开表单时 `useFormContext.register` 注册 `formId`、字段结构、响应式 model 引用；赋值前保存快照，支持逐批撤销（最多 10 批）；`formId` 不匹配直接拒填，防止页面切换误填。
- 语音：`asr.ts` 封装浏览器 Web Speech API（zh-CN），录音必须由用户主动点击启动，打开表单不会自动开麦；ASR 与填表逻辑分离，后续可替换识别服务；无麦克风/无权限按 fail-closed 不渲染入口。
- 结果展示：任务卡片（`AgentResultCard`）展示证据、链接与下一步；投递执行有独立确认弹窗回显岗位。

## 3. 能力授权方案

- 能力清单（`AgentCapabilityService.KNOWN`，8 项）：
  `agent_chat`、`voice_input`、`form_fill`、`internship_plan`、`job_apply`、`auto_task`、`auto_task_notify`、`teacher_internship_summary`。
  （权威集合以代码为准；`AgentCapabilityParseService.CAPABILITIES` 必须与之保持一致，有单测守护。）
- 配置：`system_config.AGENT_CAPABILITIES_CONFIG`（JSON：`roleDefaults` + `userOverrides`），管理员在「权限管理」页可视化维护；`userOverrides` 键为 `角色:用户ID`。
- 默认集（代码内，配置缺失时生效）：学生=chat+voice+form_fill+plan；教师=chat+voice+form_fill+teacher_internship_summary；企业=chat+voice+form_fill；管理员=chat+voice+form_fill；`job_apply`、`auto_task`、`auto_task_notify` 全默认关闭，需单独授权。
- 叠加原则：AI 能力只是闸门，叠加在原 RBAC 之上，只收窄不放大；未登记能力一律视为关闭；解析失败 fail-open 到最小集。管理员只读（impersonate）身份永远只保留 `agent_chat`。

## 4. 实施顺序

1. 阶段一：系统盘点与基线（完成）。
2. 阶段二：统一 Agent 架构 + 交互 + 授权方案冻结（本文）。
3. 阶段三：顾问知识、表单识别、语音填表、能力开关与任务记录（完成）。
4. 阶段四：学生全流程陪跑、企业语音办理、全系统表单覆盖收口、教师条件触发自动化（完成，见 §5）。
5. 回归：`mvn test` + `npm run build` + 覆盖门禁 + E2E。

## 5. 验收标准

| 编号 | 场景 | 通过标准 |
|---|---|---|
| A1 | 顾问回答有依据 | 回答包含入口/规则来源或真实状态；缺信息先澄清；无权限不猜测 |
| A2 | 顾问拒绝越权 | 教师空范围、企业他司数据、管理员只读身份均被拒绝 |
| B1 | 文字填表 | 文字指令回填多字段表单，页面显示正确且参与原校验 |
| B2 | 连续修改与撤销 | "人数改成五个，薪资不变"仅改目标字段；撤销恢复本批填写前值 |
| B3 | 语音链路 | 主动点击才开麦；实时识别→分配字段→回填；有停止入口；不支持浏览器 fail-closed |
| B4 | 防误填 | 页面切换后 formId 不匹配拒绝写入；upload 字段不代填 |
| C1 | 能力开关 | 管理员关闭 `form_fill/voice_input` 后对应入口立即不可用；`job_apply` 默认关闭 |
| C2 | 只读身份 | impersonate 只读身份不可发起对话/填表/投递 |
| D1 | 学生陪跑 | 计划按真实阶段生成，已完成不重复安排；重登后计划一致；自主实习/换岗/延迟分支正确 |
| E1 | 企业语音办理 | 岗位发布/企业资料多字段语音填写可连续修改并核对提交 |
| F1 | 全表单覆盖 | `formCoverage.ts` 为唯一真相源，已接 59 表单 + 豁免清单闭环，并门禁所有渲染 el-form 的文件必须登记，门禁测试通过 |
| G1 | 教师自动化 | 自然语言→预览→确认创建→启用→条件触发→执行→记录可查；`auto_task`/`auto_task_notify` 关闭时跳过并记日志；短信通道未接入明确拒绝 |
| G2 | 自动化范围 | 仅辅导员本班学生，不扩大范围；单次执行上限 200 人 |

## 6. 已知边界

- 2026-09-17 已用真实音频轨道（Chrome MediaStreamTrackWebSpeech + 在线 ASR）通过企业岗位多字段、多选、地址级联、连续修改、撤销与冲突保护自动化验收；真人实体麦克风、口音和设备差异仍需人工验收。
- 教师自动化 v1 仅辅导员；触发条件 3 类（心得未提交、鉴定缺交、Offer 待确认），复用学生首页待办口径。
- 通知渠道：站内提醒与邮件均可用（邮件配置见 `application.yml` 的 `spring.mail`，账号 `luzujian@deepintern.wecom.work`，密码走 `.env` 的 `MAIL_PASSWORD`；2026-09-17 实测 `isConfigured()=true`、SMTP 登录成功）。短信通用通知未接入，配置时明确拒绝而非静默失败。
- 学生计划为"按真实状态重算 + 快照持久化"，不做后台常驻推进。
- 教师自动化支持截止窗口、一次性日期时间、每日钟点、每周星期四种调度；默认每分钟扫描，固定时间补跑窗口默认60分钟。
- 自动化发送以持久化 `PENDING`/发送明细/最近成功时间共同去重，Redis 锁只承担并发互斥。
- AI 总开关统一控制对话、填表、自然语言解析和自动化执行；撤销教师自动化能力只暂停该教师任务，不自动恢复。
