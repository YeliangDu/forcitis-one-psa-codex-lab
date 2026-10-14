-- FORCITIS one · B-5 · 老系统库执行后校验（只读）。结果导出留档；任一项不符即执行 legacy_90_rollback.sql 或回报。
-- 用法：sqlcmd -S <老系统 RDS 内网地址> -d ruoyi-vue-pro -U <高权限账号> -b -i legacy_20_verify.sql
:setvar INBOX_LOGIN psa_one_legacy_ro
:setvar NAMES_LOGIN one_legacy_names_ro

-- V1 视图列与应用读取的列一致
--    v_psa_one_legacy_todo：assignee_user_id, task_id, process_instance_id, task_def_key, task_name, process_title, created_at
--    v_one_legacy_org_short_name：id, short_name
SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE
  FROM INFORMATION_SCHEMA.COLUMNS
 WHERE TABLE_NAME IN (N'v_psa_one_legacy_todo', N'v_one_legacy_org_short_name')
 ORDER BY TABLE_NAME, ORDINAL_POSITION;

-- V2 两个账号都不是任何数据库角色的成员：期望 0 行
SELECT u.name AS user_name, r.name AS role_name
  FROM sys.database_role_members m
  JOIN sys.database_principals r ON r.principal_id = m.role_principal_id
  JOIN sys.database_principals u ON u.principal_id = m.member_principal_id
 WHERE u.name IN (N'$(INBOX_LOGIN)', N'$(NAMES_LOGIN)');

-- V3 合并待办账号：能 SELECT 的对象恰好 1 行 dbo.v_psa_one_legacy_todo；令牌表为 0
EXECUTE AS USER = N'$(INBOX_LOGIN)';
SELECT TABLE_SCHEMA, TABLE_NAME
  FROM INFORMATION_SCHEMA.TABLES
 WHERE TABLE_SCHEMA NOT IN (N'sys', N'INFORMATION_SCHEMA')
   AND HAS_PERMS_BY_NAME(QUOTENAME(TABLE_SCHEMA) + N'.' + QUOTENAME(TABLE_NAME), N'OBJECT', N'SELECT') = 1;
SELECT HAS_PERMS_BY_NAME(N'dbo.system_oauth2_access_token', N'OBJECT', N'SELECT') AS access_token,
       HAS_PERMS_BY_NAME(N'dbo.system_oauth2_refresh_token', N'OBJECT', N'SELECT') AS refresh_token;
-- 与应用启动校验同一查询：期望恰好 1 行
SELECT DISTINCT table_schema, table_name FROM information_schema.table_privileges
 WHERE privilege_type = 'SELECT' AND lower(table_schema) NOT IN ('pg_catalog', 'information_schema', 'sys');
SELECT COUNT(*) AS open_todos FROM dbo.v_psa_one_legacy_todo;
REVERT;

-- V4 部门简称账号：能 SELECT 的对象恰好 1 行 dbo.v_one_legacy_org_short_name；令牌表为 0
EXECUTE AS USER = N'$(NAMES_LOGIN)';
SELECT TABLE_SCHEMA, TABLE_NAME
  FROM INFORMATION_SCHEMA.TABLES
 WHERE TABLE_SCHEMA NOT IN (N'sys', N'INFORMATION_SCHEMA')
   AND HAS_PERMS_BY_NAME(QUOTENAME(TABLE_SCHEMA) + N'.' + QUOTENAME(TABLE_NAME), N'OBJECT', N'SELECT') = 1;
SELECT HAS_PERMS_BY_NAME(N'dbo.system_oauth2_access_token', N'OBJECT', N'SELECT') AS access_token,
       HAS_PERMS_BY_NAME(N'dbo.system_oauth2_refresh_token', N'OBJECT', N'SELECT') AS refresh_token;
SELECT DISTINCT table_schema, table_name FROM information_schema.table_privileges
 WHERE privilege_type = 'SELECT' AND lower(table_schema) NOT IN ('pg_catalog', 'information_schema', 'sys');
SELECT COUNT(*) AS departments FROM dbo.v_one_legacy_org_short_name;
REVERT;

-- V5 连通性（在 ECS 上由运维执行）：从 172.16.89.86 用两个账号分别连通；其他来源被白名单拒绝
