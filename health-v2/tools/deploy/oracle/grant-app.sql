-- 给应用账号授权（docs/04 第三节"操作日志"、docs/11"数据库账号"）。由建表账号 HEALTH_V2 执行：
--   sqlplus HEALTH_V2@//<主机>:1521/<服务名> @grant-app.sql HEALTH_V2_APP
-- 每次执行完迁移（hv2-ops migrate）后都要再执行一次，新表才有权限。可以重复执行，结果一样。
--   SYS_OPERATION_LOG：只有 SELECT、INSERT（先收回全部再授予，防止以前多给了）；
--   其他表：SELECT、INSERT、UPDATE、DELETE；视图：SELECT；
--   Flyway 自己的记录表 flyway_schema_history 不授权。
-- 这个脚本不是迁移脚本，不放进 db/migration。
SET VERIFY OFF
SET FEEDBACK OFF
SET SERVEROUTPUT ON
WHENEVER SQLERROR EXIT FAILURE ROLLBACK

DECLARE
  app VARCHAR2(128) := UPPER(DBMS_ASSERT.SIMPLE_SQL_NAME('&1'));
  n_tables NUMBER := 0;
  n_views  NUMBER := 0;
BEGIN
  IF app = USER THEN
    RAISE_APPLICATION_ERROR(-20010, '应用账号不能是建表账号自己');
  END IF;
  FOR t IN (SELECT TABLE_NAME FROM USER_TABLES
             WHERE TABLE_NAME NOT LIKE 'flyway%' AND TABLE_NAME NOT LIKE 'BIN$%'
               AND NESTED = 'NO' AND SECONDARY = 'N'
             ORDER BY TABLE_NAME) LOOP
    IF t.TABLE_NAME = 'SYS_OPERATION_LOG' THEN
      BEGIN
        EXECUTE IMMEDIATE 'REVOKE ALL ON SYS_OPERATION_LOG FROM ' || app;
      EXCEPTION
        WHEN OTHERS THEN
          IF SQLCODE != -1927 THEN  -- ORA-01927：本来就没授过，不用收回
            RAISE;
          END IF;
      END;
      EXECUTE IMMEDIATE 'GRANT SELECT, INSERT ON SYS_OPERATION_LOG TO ' || app;
    ELSE
      EXECUTE IMMEDIATE 'GRANT SELECT, INSERT, UPDATE, DELETE ON "' || t.TABLE_NAME || '" TO ' || app;
    END IF;
    n_tables := n_tables + 1;
  END LOOP;
  FOR v IN (SELECT VIEW_NAME FROM USER_VIEWS ORDER BY VIEW_NAME) LOOP
    EXECUTE IMMEDIATE 'GRANT SELECT ON "' || v.VIEW_NAME || '" TO ' || app;
    n_views := n_views + 1;
  END LOOP;
  DBMS_OUTPUT.PUT_LINE('已给 ' || app || ' 授权：' || n_tables || ' 张表、' || n_views || ' 个视图');
END;
/

PROMPT 操作日志表的权限（应当只有 INSERT、SELECT）：
SELECT PRIVILEGE AS 权限 FROM USER_TAB_PRIVS_MADE
 WHERE GRANTEE = UPPER('&1') AND TABLE_NAME = 'SYS_OPERATION_LOG' ORDER BY PRIVILEGE;
