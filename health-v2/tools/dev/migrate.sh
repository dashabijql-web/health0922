#!/usr/bin/env bash
# 显式执行建表脚本：mvn flyway:migrate（后端启动时不会自动迁移）。
# 用法：tools/dev/migrate.sh [migrate|info|validate]，默认 migrate。
source "$(dirname "${BASH_SOURCE[0]}")/_common.sh"
load_env
goal="${1:-migrate}"
case "$goal" in
  migrate|info|validate) ;;
  *) echo "只支持 migrate / info / validate" >&2; exit 2 ;;
esac
cd "$BACKEND_DIR"
exec mvn -B --no-transfer-progress "flyway:$goal"
