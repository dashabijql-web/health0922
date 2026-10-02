-- 建应用账号（docs/04 第三节"操作日志"、docs/11"数据库账号"）。由数据库管理员（如 SYSTEM）执行：
--   sqlplus system@//<主机>:1521/<服务名> @create-app-user.sql HEALTH_V2_APP
-- 运行中提示输入应用账号的密码（不回显，不要含双引号）。可以重复执行：账号已存在时只改密码、解锁。
-- 应用账号只有登录权限（CREATE SESSION），不能建表、删表、清空表、停用触发器；
-- 对 HEALTH_V2 里各表的权限由建表账号执行 grant-app.sql 授予。这个脚本不是迁移脚本，不放进 db/migration。
SET VERIFY OFF
SET FEEDBACK OFF
SET SERVEROUTPUT ON
WHENEVER SQLERROR EXIT FAILURE ROLLBACK

DEFINE app_user = &1
ACCEPT app_password CHAR PROMPT '应用账号 &app_user 的密码：' HIDE

DECLARE
  app VARCHAR2(128) := UPPER(DBMS_ASSERT.SIMPLE_SQL_NAME('&app_user'));
  n   NUMBER;
BEGIN
  SELECT COUNT(*) INTO n FROM DBA_USERS WHERE USERNAME = app;
  IF n = 0 THEN
    EXECUTE IMMEDIATE 'CREATE USER ' || app || ' IDENTIFIED BY "&app_password"';
    DBMS_OUTPUT.PUT_LINE('已创建账号 ' || app);
  ELSE
    EXECUTE IMMEDIATE 'ALTER USER ' || app || ' IDENTIFIED BY "&app_password" ACCOUNT UNLOCK';
    DBMS_OUTPUT.PUT_LINE('账号 ' || app || ' 已存在，已改密码并解锁');
  END IF;
  EXECUTE IMMEDIATE 'GRANT CREATE SESSION TO ' || app;
END;
/

UNDEFINE app_password

PROMPT 这个账号的系统权限和角色（应当只有 CREATE SESSION）：
SELECT PRIVILEGE AS 系统权限 FROM DBA_SYS_PRIVS WHERE GRANTEE = UPPER('&app_user')
UNION ALL
SELECT 'ROLE ' || GRANTED_ROLE FROM DBA_ROLE_PRIVS WHERE GRANTEE = UPPER('&app_user');
