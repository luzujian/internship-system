# AGENTS.md — 项目规范

## 🔐 数据库权限红线

**业务账号** **`user`** **只有基础 CRUD 权限（SELECT/INSERT/UPDATE/DELETE），不得突破。**

- 日常操作一律用 `user` 账号，密码 `1234`，连接 `8.148.216.84:3306/internship`

- **遇到 DDL（CREATE TABLE、ALTER TABLE、DROP 等）、修改表结构、创建索引、调整权限时，必须停下来告诉用户：**

  > ⚠️ 此操作需要 root 权限。请联系管理员「辣子鸡」确认。

- 只有用户明确回复"辣子鸡同意了"或"管理员已确认"后，才能切换到 root 账号继续操作

- AI 绝对不得在未确认的情况下擅自使用 root 权限

## 🚫 Gitee 提交红线

**永远只提交系统运行必需的文件，其他一切禁止推送到 Gitee。**

### ✅ 允许提交

- `backend/` 目录下所有源码、配置、pom.xml

- `frontend/` 目录下所有源码、配置、package.json

- `.gitignore`

- `README.md`

- 本文件 `AGENTS.md`

### ❌ 禁止提交

- 日志文件（\*.log）

- 编译产物（target/、dist/、dist.tar.gz）

- IDE 配置（.idea/、.vscode/）

- 一次性脚本

- Docker 配置文件

- 开发文档、图片

- 根目录下任何其他新建文件

### 📋 提交流程

```
git add backend/ frontend/ .gitignore README.md AGENTS.md
git commit -m "..."
git push gitee master
```

> `.gitignore` 已设为白名单模式（`/*` 忽略一切，仅放行上述目录/文件），
> 即使误用 `git add .` 也不会把垃圾文件加进去。

## 生产发布与长期磁盘维护

- 发布前运行服务器 `/usr/local/sbin/internship-maintenance.py preflight`。空闲不足 5 GiB、使用率达到 85% 或发布目录超过 10 GiB 时停止上传，先处理容量。
- `/root/workspace/internship-system/releases` 仅保存发布和回滚资料。当前版本、至少 3 个近期版本、7 天内的资料，以及仍被容器、配置或进程引用的依赖必须保留；额外保留项见 `/etc/internship-ops/policy.json`。
- 旧发布只能在有完整校验的本机归档，或经过文件恢复验证的云备份覆盖后清理。自动清理要求文件清单稳定、冻结至少 48 小时、后续云备份成功且不超过 36 小时；备份失败、来源变动或目录含挂载时停止删除。
- 禁止用 `docker system prune --volumes` 或按“未使用”批量删除数据卷。业务数据库、上传材料和 AI 私有数据不属于发布清理范围。
- 新建容器必须配置日志轮转，沿用每份 10 MB、保留 3 至 5 份的上限。修改后检查实际容器的日志配置，不能仅修改配置文件。
- 数据库日常备份继续使用业务账号 `user`；不允许为备份擅自提升数据库权限。每日 00:45 的一致性导出不包含触发器、存储过程和事件，不能据此声称完成这些对象的恢复验证。
- 定时任务、状态和运行手册保存在服务器 `/etc/internship-ops`、`/var/lib/internship-ops` 及本次运维归档中；运维脚本、归档和 Docker 配置仍遵守上述 Gitee 禁止提交规则。

