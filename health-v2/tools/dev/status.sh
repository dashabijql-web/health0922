#!/usr/bin/env bash
# 查看本项目后端、前端和依赖（Oracle、Redis）的状态。
source "$(dirname "${BASH_SOURCE[0]}")/_common.sh"

line() { printf '%-10s %-6s %s\n' "$1" "$2" "$3"; }

show_proc() {
  local name="$1" port="$2" url="$3" pid listen code
  pid="$(read_pid "$name")"
  listen="$(port_pids "$port" | tr '\n' ' ')"
  code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "$url" 2>/dev/null || true)"
  if pid_alive "$pid"; then
    line "$name" "运行" "PID ${pid}，端口 $port 监听进程：${listen:-无}，$url → HTTP $code"
  elif [[ -n "$listen" ]]; then
    line "$name" "占用" "端口 $port 被其他进程占用：${listen}（不是 start.sh 起的）"
  else
    line "$name" "停止" "端口 $port 空闲"
  fi
}

show_proc backend "$BACKEND_PORT" "http://127.0.0.1:$BACKEND_PORT/actuator/health"
show_proc frontend "$FRONTEND_PORT" "http://127.0.0.1:$FRONTEND_PORT/"

if pid_alive "$(read_pid backend)"; then
  echo "后端健康：$(curl -s --max-time 3 "http://127.0.0.1:$BACKEND_PORT/actuator/health" || echo 无响应)"
fi
if [[ -n "$(port_pids 1521)" ]]; then line oracle "监听" "1521"; else line oracle "未监听" "1521"; fi
# Redis 是 Docker 容器 hv2-redis（本机没有装 redis-cli），在容器里执行 ping
if docker exec hv2-redis redis-cli ping >/dev/null 2>&1; then line redis "正常" "${REDIS_PORT:-6380} PONG（容器 hv2-redis）"; else line redis "异常" "${REDIS_PORT:-6380}（容器 hv2-redis 没有运行？docker start hv2-redis）"; fi
