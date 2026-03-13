#!/bin/bash
echo "=========================================="
echo "  部署脚本 - 前端/后端 更新工具"
echo "=========================================="

SERVICE=$1
BACKUP_DB=$2

if [ -z "$SERVICE" ]; then
    echo "用法：./deploy.sh <frontend|backend|all> [backup]"
    exit 1
fi

# 备份数据库
if [ "$BACKUP_DB" = "backup" ]; then
    echo "[备份] 正在备份数据库..."
    mysqldump -u root -p1234 internship > ~/backup_$(date +%Y%m%d_%H%M%S).sql
    echo "[备份] 完成"
fi

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
esac

echo ""
echo "[状态] 服务运行状态:"
docker-compose ps
