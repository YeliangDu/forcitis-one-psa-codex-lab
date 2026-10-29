-- FORCITIS one · B-5 · A8 执行后校验（只读）。结果导出留档，交 one-coordinator；任一项不符即执行 a8_90_rollback.sql 或回报。
-- 用法：sqlcmd -S <A8 SQL Server> -d a8_db -E -b -i a8_20_verify.sql [-v READER_LOGIN=<登录名>]
:setvar READER_LOGIN one_a8_views_ro

-- V1 视图列与白名单完全相等：期望 33 行（v_org_unit 10、v_org_relationship 4、v_org_post 6、v_org_member 13），
--    逐列对照 backend/architecture/external-read-registry.yaml 中 a8_db 视图列
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
-- V3.1–V3.3（恰好 4 个视图、基表不可读、库级只有 CONNECT）已被 ADR-0037 取代：账号全库只读，形状以该账号真实登录执行
-- read-all/readonly-check.sql 核对 (a)–(g)，与应用启动校验同一组查询。
EXECUTE AS USER = N'$(READER_LOGIN)';
-- V3.4 视图可读、行数合理（人员、部门数与 A8 界面大致一致）
SELECT (SELECT COUNT(*) FROM forcitis_one.v_org_unit)         AS org_units,
       (SELECT COUNT(*) FROM forcitis_one.v_org_member)       AS members,
       (SELECT COUNT(*) FROM forcitis_one.v_org_post)         AS posts,
       (SELECT COUNT(*) FROM forcitis_one.v_org_relationship) AS relationships;
-- V3.5 v_org_member 一人一行：期望 0 行
SELECT ID FROM forcitis_one.v_org_member GROUP BY ID HAVING COUNT(*) > 1;
REVERT;

-- V4 连通性（在 ECS 上由运维执行，不在本库）：从 172.16.89.86 用该账号连 a8_db 成功；其他来源地址被白名单拒绝
