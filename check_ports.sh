#!/bin/bash

PORTS=(8125 8135 3351 6424)
HOST="127.0.0.1"

echo "=== 端口占用检测 ==="
echo ""

all_free=true

for port in "${PORTS[@]}"; do
  if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null 2>&1; then
    echo "⚠️  端口 $port 已被占用"
    all_free=false
  else
    echo "✅  端口 $port 可用"
  fi
done

echo ""
if [ "$all_free" = true ]; then
  echo "所有端口均可用，可以启动服务。"
  exit 0
else
  echo "部分端口被占用，请释放后重试。"
  exit 1
fi
