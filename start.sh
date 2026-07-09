#!/bin/bash

set -e

echo "=== 冷链物流中转仓置物架管理系统启动脚本 ==="

echo ""
echo "1. 检查端口占用..."
PORTS=(8125 8135 3351 6424)
for port in "${PORTS[@]}"; do
    if lsof -Pi ":$port" -sTCP:LISTEN -t > /dev/null 2>&1; then
        echo "   ERROR: 端口 $port 已被占用"
        lsof -Pi ":$port" -sTCP:LISTEN | head -5
        exit 1
    else
        echo "   端口 $port: 可用"
    fi
done

echo ""
echo "2. 构建并启动 Docker 服务..."
docker compose up -d --build

echo ""
echo "3. 等待服务启动..."
sleep 30

echo ""
echo "4. 验证服务状态..."
echo "   MySQL: $(curl -s http://127.0.0.1:3351 > /dev/null 2>&1 && echo "运行中" || echo "异常")"
echo "   Redis: $(curl -s http://127.0.0.1:6424 > /dev/null 2>&1 && echo "运行中" || echo "异常")"
echo "   后端: $(curl -s http://127.0.0.1:8135/api/shelf > /dev/null 2>&1 && echo "运行中" || echo "异常")"
echo "   前端: $(curl -s http://127.0.0.1:8125 > /dev/null 2>&1 && echo "运行中" || echo "异常")"

echo ""
echo "=== 启动完成 ==="
echo "前端访问地址: http://localhost:8125"
echo "后端API地址: http://localhost:8135/api/shelf"
