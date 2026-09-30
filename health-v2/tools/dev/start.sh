#!/usr/bin/env bash
# 启动本项目的后端（8081）和/或前端（9529）。
# 用法：tools/dev/start.sh [all|backend|frontend]，默认 all。
# 额外的后端参数可放在环境变量 BACKEND_ARGS 里，例如 BACKEND_ARGS='--hv2.auth.lock-duration=30s'。
source "$(dirname "${BASH_SOURCE[0]}")/_common.sh"
target="${1:-all}"

ensure_port_free() {
  local port="$1" name="$2"
  for old in $OLD_PROJECT_PORTS; do
    if [[ "$port" == "$old" ]]; then
      echo "$name 端口 $port 是老项目的端口，拒绝使用" >&2
      exit 1
    fi
  done
  if [[ -n "$(port_pids "$port")" ]]; then
    echo "$name 端口 $port 已被占用：" >&2
    lsof -nP -iTCP:"$port" -sTCP:LISTEN >&2 || true
    exit 1
  fi
}

wait_http() {
  local url="$1" name="$2" seconds="$3" pid="$4"
  for ((i = 0; i < seconds; i++)); do
    if curl -fsS -o /dev/null "$url" 2>/dev/null; then
      echo "$name 已就绪：$url"
      return 0
    fi
    if ! pid_alive "$pid"; then
      echo "$name 进程已退出，看日志：$LOG_DIR/$name.log" >&2
      tail -n 30 "$LOG_DIR/$name.log" >&2 || true
      return 1
    fi
    sleep 1
  done
  echo "$name 在 ${seconds}s 内没有就绪，看日志：$LOG_DIR/$name.log" >&2
  return 1
}

start_backend() {
  if pid_alive "$(read_pid backend)"; then
    echo "后端已在运行（PID $(read_pid backend)）"
    return 0
  fi
  ensure_port_free "$BACKEND_PORT" backend
  load_env
  echo "打包后端……"
  (cd "$BACKEND_DIR" && mvn -q -B -DskipTests package)
  echo "启动后端（端口 ${BACKEND_PORT}）……"
  # shellcheck disable=SC2086
  nohup java -jar "$BACKEND_DIR/target/hv2-backend.jar" --server.port="$BACKEND_PORT" ${BACKEND_ARGS:-} \
    >"$LOG_DIR/backend.log" 2>&1 </dev/null &
  echo $! >"$PID_DIR/backend.pid"
  wait_http "http://127.0.0.1:$BACKEND_PORT/actuator/health" backend 90 "$!"
}

start_frontend() {
  if pid_alive "$(read_pid frontend)"; then
    echo "前端已在运行（PID $(read_pid frontend)）"
    return 0
  fi
  ensure_port_free "$FRONTEND_PORT" frontend
  if [[ ! -d "$FRONTEND_DIR/node_modules" ]]; then
    echo "安装前端依赖……"
    (cd "$FRONTEND_DIR" && pnpm install --frozen-lockfile)
  fi
  echo "启动前端（端口 ${FRONTEND_PORT}）……"
  # 直接用 node 跑 vite，只有一个进程，停止时不会留下孤儿进程
  # 先 cd 再单独把 node 放到后台，$! 才是 node 本身的进程号
  (
    cd "$FRONTEND_DIR"
    nohup node node_modules/vite/bin/vite.js --port "$FRONTEND_PORT" --strictPort \
      >"$LOG_DIR/frontend.log" 2>&1 </dev/null &
    echo $! >"$PID_DIR/frontend.pid"
  )
  wait_http "http://127.0.0.1:$FRONTEND_PORT/" frontend 60 "$(read_pid frontend)"
}

case "$target" in
  all) start_backend; start_frontend ;;
  backend) start_backend ;;
  frontend) start_frontend ;;
  *) echo "用法：$0 [all|backend|frontend]" >&2; exit 2 ;;
esac
