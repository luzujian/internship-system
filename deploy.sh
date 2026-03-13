#!/bin/bash
echo "=========================================="
echo "  部署脚本 - 前端/后端 更新工具"
echo "=========================================="

SERVICE=$1
SKIP_PULL=$2

if [ -z "$SERVICE" ]; then
    echo "用法：./deploy.sh <frontend|backend|all> [--no-pull]"
    echo "  frontend  - 仅更新前端"
    echo "  backend   - 仅更新后端"
    echo "  all       - 更新所有服务"
    echo "  --no-pull - 跳过 Git pull（本地已更新时）"
    exit 1
fi

cd ~/internship-system

# 拉取最新代码（除非指定跳过）
if [ "$SKIP_PULL" != "--no-pull" ]; then
    echo "[Git] 拉取最新代码..."
    git pull origin main
fi

# 备份数据库
echo "[备份] 备份数据库..."
mysqldump -u root -p1234 internship > ~/backup_$(date +%Y%m%d_%H%M%S).sql

case $SERVICE in
    frontend)
        echo "[部署] 更新前端..."
        docker-compose restart frontend
        ;;
    backend)
        echo "[部署] 更新后端..."
        docker-compose restart backend
        ;;
    all)
        echo "[部署] 更新所有服务..."
        docker-compose down
        docker-compose up -d
        ;;
    *)
        echo "错误：未知服务，请使用 frontend|backend|all"
        exit 1
        ;;
esac

echo ""
echo "[状态] 服务运行状态:"
docker-compose ps

echo ""
echo "[完成] 部署完成!"
