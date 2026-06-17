#!/usr/bin/env bash
set -euo pipefail

# ============================================================
# 实习管理系统 — 一键部署脚本
# 本地执行，自动完成 构建 → 传输 → 重启 → 验证
# ============================================================

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
FRONTEND_DIR="$SCRIPT_DIR/frontend"
BACKEND_DIR="$SCRIPT_DIR/backend"

# ── 服务器配置 ──
SSH_HOST="root@8.148.216.84"
SSH_PORT="22"
SSH_PASS="@Lzj58536602"
SERVER_DIR="/root/workspace/internship-system"
DIST_TARGET="$SERVER_DIR/frontend/dist/"
JAR_TARGET="$SERVER_DIR/backend.jar"
JAR_SRC="$BACKEND_DIR/target/Internship-0.0.1-SNAPSHOT.jar"

# ── 颜色输出 ──
GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m'
info()  { echo -e "${BLUE}[INFO]${NC}  $*"; }
ok()    { echo -e "${GREEN}[OK]${NC}    $*"; }
fail()  { echo -e "${RED}[FAIL]${NC}  $*"; }

# ── 步骤 1: 构建前端 ──
info "构建前端..."
cd "$FRONTEND_DIR"
npm run build 2>&1 | tail -3
ok "前端构建完成"

# ── 步骤 2: 构建后端 ──
info "构建后端..."
cd "$BACKEND_DIR"
MAVEN_CMD="$(which mvn 2>/dev/null || true)"
if [ -z "$MAVEN_CMD" ]; then
  # 尝试 mvnw，没有则使用常见安装路径
  MAVEN_CMD="$(ls mvnw 2>/dev/null || echo "/d/Summer-Program/apache-maven-3.9.4/bin/mvn")"
fi
$MAVEN_CMD clean package -DskipTests -q 2>&1 | tail -3
ok "后端构建完成"

# ── 步骤 3: 传输文件 ──
info "传输前端文件到服务器..."
sshpass -p "$SSH_PASS" scp -P "$SSH_PORT" -o StrictHostKeyChecking=no -r "$FRONTEND_DIR/dist/"* "$SSH_HOST:$DIST_TARGET" 2>&1
ok "前端文件传输完成"

info "传输后端 jar 到服务器..."
sshpass -p "$SSH_PASS" scp -P "$SSH_PORT" -o StrictHostKeyChecking=no "$JAR_SRC" "$SSH_HOST:$JAR_TARGET" 2>&1
ok "后端 jar 传输完成"

# ── 步骤 4: 重启容器 ──
info "重启 Docker 容器..."
sshpass -p "$SSH_PASS" ssh -p "$SSH_PORT" -o StrictHostKeyChecking=no "$SSH_HOST" "cd $SERVER_DIR && docker compose restart backend frontend"
ok "容器重启指令已发送"

# ── 步骤 5: 等待健康检查 ──
info "等待后端启动..."
sleep 15
HEALTH=$(sshpass -p "$SSH_PASS" ssh -p "$SSH_PORT" -o StrictHostKeyChecking=no "$SSH_HOST" "docker ps --format '{{.Names}} {{.Status}}' | grep internship")
echo "$HEALTH"
ok "部署完成！"
