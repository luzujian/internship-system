# MySQL Docker 迁移完成总结

## 迁移结果

✅ **迁移已成功完成！**

本地实习管理系统数据库已完整迁移到 Ubuntu 服务器的 Docker 容器中。

---

## 部署信息

### 服务器配置
- **ZeroTier IP**: 10.147.17.67
- **SSH 端口**: 1958
- **MySQL 端口**: 3306（仅 ZeroTier 内网访问）
- **部署方式**: Docker Compose

### Docker 配置
- **镜像源**: `docker.1ms.run/library/mysql:8.0`（代理镜像）
- **容器名称**: mysql-server
- **数据卷**: mysql-data（持久化）
- **日志目录**: /opt/logs/mysql

### 数据库配置
- **数据库名**: internship
- **用户名**: root
- **密码**: 1234
- **远程访问**: 已允许 10.147.17.0/24 网段访问
- **表数量**: 43 张表

### 防火墙规则
已添加 MySQL 端口（3306）到防火墙，仅允许 ZeroTier 内网（10.147.17.0/24）访问。

---

## docker-compose.yml 配置

```yaml
version: '3.8'
services:
  mysql:
    image: docker.1ms.run/library/mysql:8.0
    container_name: mysql-server
    restart: always
    environment:
      MYSQL_ROOT_PASSWORD: YourStrongRootPassword123!
    ports:
      - "10.147.17.67:3306:3306"
    volumes:
      - mysql-data:/var/lib/mysql
      - /opt/logs/mysql:/var/log/mysql
    command:
      - --character-set-server=utf8mb4
      - --collation-server=utf8mb4_unicode_ci
      - --bind-address=0.0.0.0
    networks:
      - mysql-net

volumes:
  mysql-data:
    driver: local

networks:
  mysql-net:
    driver: bridge
```

---

## 验证方式

### 1. 本地连接测试
```bash
mysql -h 10.147.17.67 -P 3306 -u root -p1234 internship
```

### 2. 查看数据库表
```sql
USE internship;
SHOW TABLES;
```

### 3. 服务器查看容器状态
```bash
ssh -p 1958 -i C:/Users/24096/.ssh/id_rsa gdmu@10.147.17.67
docker-compose ps
```

---

## 项目配置

### 后端配置（已更新）
文件：`D:\Web-work\Internship\backend\.env`

```env
# 数据库配置 - 远程服务器
DB_HOST=10.147.17.67
DB_PORT=3306
DB_NAME=internship
DB_USERNAME=root
DB_PASSWORD=1234
```

---

## 服务器管理命令

### SSH 登录
```bash
ssh -p 1958 -i C:/Users/24096/.ssh/id_rsa gdmu@10.147.17.67
```

### 查看容器状态
```bash
docker-compose ps
```

### 查看 MySQL 日志
```bash
docker-compose logs -f mysql
```

### 重启 MySQL
```bash
docker-compose restart
```

### 停止 MySQL
```bash
docker-compose down
```

### 启动 MySQL
```bash
docker-compose up -d
```

### 登录 MySQL 容器
```bash
docker exec -it mysql-server mysql -u root -pYourStrongRootPassword123!
```

### 备份数据库
```bash
docker exec mysql-server mysqldump -u root -pYourStrongRootPassword123! internship > backup.sql
```

### 还原数据库
```bash
docker exec -i mysql-server mysql -u root -pYourStrongRootPassword123! internship < backup.sql
```

---

## 安全注意事项

1. **网络隔离**: MySQL 绑定 ZeroTier IP，不暴露公网
2. **防火墙限制**: 仅允许来源 IP 10.147.17.0/24 访问 3306 端口
3. **用户权限**: 已创建远程访问用户 root@% 并授权
4. **密码管理**:
   - 远程访问密码：1234（建议修改为强密码）
   - Root 本地密码：YourStrongRootPassword123!

---

## Docker 镜像源说明

使用 `docker.1ms.run` 代理镜像源解决国内拉取失败问题：

```bash
# 拉取镜像
docker pull docker.1ms.run/library/mysql:8.0

# 或者使用一键拉取脚本
bash -c "$(curl -fsSL https://cn.bbc.us.kg/docker_pull.sh)" -- mysql:8.0
```

---

## 迁移时间
- 完成时间：2026-03-09
- MySQL 版本：8.0
- 数据量：约 613KB（43 张表）

---

## 故障排除

### 无法远程连接
1. 检查防火墙：`sudo ufw status`
2. 检查容器状态：`docker-compose ps`
3. 检查用户权限：`docker exec -i mysql-server mysql -u root -p -e "SELECT user, host FROM mysql.user;"`

### 容器异常
```bash
docker-compose logs mysql
```

### ZeroTier 连接问题
```bash
sudo zerotier-cli info
sudo zerotier-cli listnetworks
```
