-- FORCITIS one · B-5 · A8 执行后校验（只读）。结果导出留档，交 one-coordinator；任一项不符即执行 a8_90_rollback.sql 或回报。
-- 用法：sqlcmd -S <A8 SQL Server> -d a8_db -E -b -i a8_20_verify.sql [-v READER_LOGIN=<登录名>]
:setvar READER_LOGIN one_a8_views_ro

-- V1 视图列与白名单完全相等：期望 33 行（v_org_unit 10、v_org_relationship 4、v_org_post 6、v_org_member 13），
--    逐列对照 backend/architecture/seeyon-view-columns.yaml
SELECT TABLE_NAME, COLUMN_NAME
  FROM INFORMATION_SCHEMA.COLUMNS
 WHERE TABLE_SCHEMA = N'forcitis_one'
 ORDER BY TABLE_NAME, ORDINAL_POSITION;
SELECT TABLE_NAME, COUNT(*) AS columns FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = N'forcitis_one' GROUP BY TABLE_NAME;

-- V2 账号不是任何数据库角色的成员：期望 0 行（public 不计）
SELECT r.name AS role_name
  FROM sys.database_role_members m
  JOIN sys.database_principals r ON r.principal_id = m.role_principal_id
  JOIN sys.database_principals u ON u.principal_id = m.member_principal_id
 WHERE u.name = N'$(READER_LOGIN)';

-- V3 以该账号身份核对
EXECUTE AS USER = N'$(READER_LOGIN)';
-- V3.1 能 SELECT 的用户对象恰好是 4 个视图
SELECT TABLE_SCHEMA, TABLE_NAME
  FROM INFORMATION_SCHEMA.TABLES
 WHERE TABLE_SCHEMA NOT IN (N'sys', N'INFORMATION_SCHEMA')
   AND HAS_PERMS_BY_NAME(QUOTENAME(TABLE_SCHEMA) + N'.' + QUOTENAME(TABLE_NAME), N'OBJECT', N'SELECT') = 1;
-- V3.2 基表不可读：期望全部为 0
SELECT HAS_PERMS_BY_NAME(N'dbo.ORG_MEMBER', N'OBJECT', N'SELECT')    AS org_member,
       HAS_PERMS_BY_NAME(N'dbo.ORG_UNIT', N'OBJECT', N'SELECT')      AS org_unit,
       HAS_PERMS_BY_NAME(N'dbo.formmain_0307', N'OBJECT', N'SELECT') AS hr_form;
-- V3.3 库级权限：只应有 CONNECT（应用启动校验拒绝 SELECT / INSERT / UPDATE / DELETE / EXECUTE / ALTER / CONTROL 等）
SELECT permission_name FROM fn_my_permissions(NULL, N'DATABASE');
-- V3.4 视图可读、行数合理（人员、部门数与 A8 界面大致一致）
SELECT (SELECT COUNT(*) FROM forcitis_one.v_org_unit)         AS org_units,
       (SELECT COUNT(*) FROM forcitis_one.v_org_member)       AS members,
       (SELECT COUNT(*) FROM forcitis_one.v_org_post)         AS posts,
       (SELECT COUNT(*) FROM forcitis_one.v_org_relationship) AS relationships;
-- V3.5 v_org_member 一人一行：期望 0 行
SELECT ID FROM forcitis_one.v_org_member GROUP BY ID HAVING COUNT(*) > 1;
REVERT;

-- V4 连通性（在 ECS 上由运维执行，不在本库）：从 172.16.89.86 用该账号连 a8_db 成功；其他来源地址被白名单拒绝
