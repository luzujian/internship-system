# DeepIntern 阶段六发布与运维说明

版本状态：阶段六候选版。本文对应最终验收通过后的 Gitee commit，不得使用起始提交的构建产物代替。

## 构建与启动

```powershell
cd backend
mvn -q clean package
java -jar target/Internship-0.0.1-SNAPSHOT.jar

cd ../frontend
npm ci
npm run build
```

本地运行依赖：MySQL `127.0.0.1:3306/internship`、Redis `127.0.0.1:6379`、Java 25、Node 20+。真实 DeepSeek Key 只放在进程环境变量 `DEEPSEEK_API_KEY` 中，不写入仓库 `.env`。

## 功能开关

- `AI_ASSISTANT_ENABLED`：系统数据库总开关。关闭后 AI 对话、填表、自然语言解析停止，自动化任务保留但跳过执行并显示阻塞原因。
- `AGENT_ENABLED`：进程覆盖项。`system` 跟随数据库，`false` 强制关闭，`true` 仅供本地测试强制开启。
- `AGENT_CAPABILITIES_CONFIG`：角色默认能力和用户覆盖；自动化策略包含动作、渠道、范围和单次目标上限。
- `teacher.auto-task.cron`：后台扫描表达式，默认每分钟 `0 * * * * ?`。
- `teacher.auto-task.zone`：任务时区，默认 `Asia/Shanghai`。
- `teacher.auto-task.missed-grace-minutes`：固定时间任务停机补跑窗口，默认 60 分钟。
- `teacher.auto-task.test-mode`、`teacher.auto-task.recipient-allowlist`：本地邮件验收保护。测试模式最多只向白名单地址实际发信。

## 固定时间语义

- `WINDOW`：截止前 N 天内按后台扫描周期检查，沿用每学生最小重发间隔。
- `ONCE`：指定日期和 `HH:mm` 执行一次；执行后结束。
- `DAILY`：每天指定 `HH:mm` 检查条件。
- `WEEKLY`：每周指定星期和 `HH:mm` 检查条件。
- 固定时间任务每次执行前仍重新查询学生最新状态；已完成学生不会再收到提醒。
- 服务停机后在补跑窗口内恢复执行一次；超过窗口记录 `SKIPPED_MISSED` 并进入下一周期。
- 发送前写入 `PENDING`，异常重启后将过期记录标记为 `UNKNOWN` 而不是自动重发。

## 通知渠道

- 站内提醒：可用，写入 `student_reminder`。
- 邮件：需配置 SMTP 账号密码；发送日志只代表 SMTP 已接受，完整送达需受控邮箱实际收信或回执验证。
- 短信通用通知：未接入，当前明确拒绝，不把生成文案视为已发送。

## 日志

- 应用日志：`backend/logs/tlias-*.log`，按天和大小滚动。
- 自动化任务：`teacher_auto_task_log` 保存每轮命中、发送、跳过和失败摘要。
- 自动化明细：`teacher_auto_task_send_log` 保存学生、渠道、状态和失败原因。

## 数据库迁移

阶段六在 `teacher_auto_task` 增加可空 JSON 列：

```sql
ALTER TABLE teacher_auto_task
  ADD COLUMN schedule_config JSON NULL AFTER end_at;
```

历史记录列值为空时按 `WINDOW` 处理。迁移前必须备份 `teacher_auto_task`；未获得管理员对服务器数据库的单独确认时，不在服务器执行。

## 回退

1. 关闭 `AI_ASSISTANT_ENABLED` 或设置进程 `AGENT_ENABLED=false`，冻结所有 AI 入口和自动化执行。
2. 恢复验收前保存的 `AGENT_CAPABILITIES_CONFIG` 与全局开关值。
3. 恢复上一版后端 JAR 和前端 `dist`，重启容器/进程。
4. 验证首页 HTTPS、`/api/health/check` 的数据库查询、登录和关键业务页面。

`schedule_config` 为可空列，回退旧版 JAR 时无需删除；旧版会忽略该列，原任务仍按窗口模式运行。

## 已知限制

- 自动化 v1 仅辅导员本人负责班级；其他教师子角色不能创建自动化任务。
- 触发条件当前为心得未提交、鉴定缺交、Offer 待确认。
- 真实语音依赖目标浏览器 Web Speech API、设备和远程识别服务；不支持时必须明确标记未验证，不使用 fake-ASR 代替。
- 59 个表单注册通过不等于 59 个表单都完成真实模型和真实语音端到端验收；当前真实音频端到端覆盖企业岗位发布的多字段、多选、地址级联、连续修改和冲突保护。
- AI 是原 RBAC 之上的收窄闸门，不扩大用户原本的数据和提交权限。
