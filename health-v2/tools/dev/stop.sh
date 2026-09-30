#!/usr/bin/env bash
# 停止本项目用 start.sh 起的进程（只按 PID 文件停，不按端口乱杀），并确认端口已释放。
# 用法：tools/dev/stop.sh [all|backend|frontend]，默认 all。
source "$(dirname "${BASH_SOURCE[0]}")/_common.sh"
target="${1:-all}"

stop_one() {
  local name="$1" port="$2" pid
  pid="$(read_pid "$name")"
  if ! pid_alive "$pid"; then
    echo "$name 没有在运行"
    rm -f "$PID_DIR/$name.pid"
  else
    local pids
    pids="$(pid_tree "$pid" | tr '\n' ' ')"
    echo "停止 ${name}（PID ${pids}）……"
    # shellcheck disable=SC2086
    kill $pids 2>/dev/null || true
    for ((i = 0; i < 30; i++)); do
      pid_alive "$pid" || break
      sleep 1
    done
    if pid_alive "$pid"; then
      echo "$name 30 秒内没有退出，强制结束"
      # shellcheck disable=SC2086
      kill -9 $pids 2>/dev/null || true
    fi
    rm -f "$PID_DIR/$name.pid"
  fi
  if [[ -n "$(port_pids "$port")" ]]; then
    echo "警告：端口 $port 仍被占用（不是本脚本起的进程，未处理）：" >&2
    lsof -nP -iTCP:"$port" -sTCP:LISTEN >&2 || true
    return 1
  fi
  echo "端口 $port 已释放"
}

case "$target" in
  all) rc=0; stop_one frontend "$FRONTEND_PORT" || rc=1; stop_one backend "$BACKEND_PORT" || rc=1; exit $rc ;;
  backend) stop_one backend "$BACKEND_PORT" ;;
  frontend) stop_one frontend "$FRONTEND_PORT" ;;
  *) echo "用法：$0 [all|backend|frontend]" >&2; exit 2 ;;
esac
