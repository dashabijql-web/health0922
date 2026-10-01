#!/usr/bin/env bash
# 在本机 Oracle 容器里执行 SQL（查数、验收用）。连接信息来自 .env.local，密码经标准输入传给 sqlplus，不出现在命令行上。
# 用法：tools/dev/sql.sh [--test] "SELECT ...;"      也可以从标准输入读：tools/dev/sql.sh < a.sql
# --test 连测试用户 ORACLE_TEST_USER；默认连业务用户 ORACLE_USER。
source "$(dirname "${BASH_SOURCE[0]}")/_common.sh"
load_env
user="$ORACLE_USER"
password="$ORACLE_PASSWORD"
if [[ "${1:-}" == "--test" ]]; then
  user="$ORACLE_TEST_USER"
  password="$ORACLE_TEST_PASSWORD"
  shift
fi
if [[ $# -gt 0 ]]; then
  sql="$1"
else
  sql="$(cat)"
fi
docker exec -i local-oracle-free sqlplus -s /nolog <<SQL
CONNECT ${user}/"${password}"@//localhost:1521/${ORACLE_SERVICE:-FREEPDB1}
WHENEVER SQLERROR EXIT FAILURE ROLLBACK
SET PAGESIZE 200 LINESIZE 200 FEEDBACK OFF TRIMSPOOL ON
-- 和后端一样按北京时间：LOCALTIMESTAMP、CURRENT_DATE 都是北京时间（容器本身是 UTC）
ALTER SESSION SET TIME_ZONE = 'Asia/Shanghai';
ALTER SESSION SET NLS_TIMESTAMP_FORMAT = 'YYYY-MM-DD HH24:MI:SS';
ALTER SESSION SET NLS_DATE_FORMAT = 'YYYY-MM-DD';
${sql}
EXIT
SQL
