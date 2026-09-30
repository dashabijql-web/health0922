#!/usr/bin/env bash
# tools/dev 下各脚本共用：路径、端口、读取 .env.local。只操作本项目自己起的进程。
set -euo pipefail

HV2_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BACKEND_DIR="$HV2_ROOT/backend"
FRONTEND_DIR="$HV2_ROOT/frontend"
RUN_DIR="$HV2_ROOT/runtime/dev"
LOG_DIR="$RUN_DIR/logs"
PID_DIR="$RUN_DIR/pids"

BACKEND_PORT="${SERVER_PORT:-8081}"
FRONTEND_PORT="${FRONTEND_PORT:-9529}"
# 老项目的端口，绝不占用或停止
OLD_PROJECT_PORTS="8080 9000 9528"

mkdir -p "$LOG_DIR" "$PID_DIR"

# 读取 .env.local（Oracle 连接信息）。只导出变量，不打印内容。
load_env() {
  local env_file="$HV2_ROOT/.env.local"
  if [[ ! -f "$env_file" ]]; then
    echo "缺少 ${env_file}（Oracle 连接信息）" >&2
    exit 1
  fi
  set -a
  # shellcheck disable=SC1090
  . "$env_file"
  set +a
}

# 端口上正在监听的进程号（没有则为空）
port_pids() {
  lsof -nP -t -iTCP:"$1" -sTCP:LISTEN 2>/dev/null || true
}

# 某进程及其所有子进程
pid_tree() {
  local pid="$1" child
  echo "$pid"
  for child in $(pgrep -P "$pid" 2>/dev/null || true); do
    pid_tree "$child"
  done
}

pid_alive() {
  [[ -n "${1:-}" ]] && kill -0 "$1" 2>/dev/null
}

read_pid() {
  local file="$PID_DIR/$1.pid"
  [[ -f "$file" ]] && cat "$file" || true
}
