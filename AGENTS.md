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

