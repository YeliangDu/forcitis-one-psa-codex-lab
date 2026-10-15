-- ADR-0037 复审 H-1 / M-3 取证 · 以取证账号执行，只出授权元数据（不出数据）。目录视图按调用者权限过滤，看不到的行不会报错，结果须结合 V0 判断可见范围
-- V0 本账号能看到的主体数（可见范围）
SELECT COUNT(*) AS principals_visible FROM sys.database_principals;
GO
-- H1a 四个只读账号在本库 class<>0 的授权
SELECT pr.name AS account, p.class_desc, COALESCE(OBJECT_SCHEMA_NAME(p.major_id) + '.' + OBJECT_NAME(p.major_id), SCHEMA_NAME(p.major_id), USER_NAME(p.major_id), '') AS target,
       p.permission_name, p.state_desc
  FROM sys.database_permissions p JOIN sys.database_principals pr ON pr.principal_id = p.grantee_principal_id
 WHERE p.class <> 0 AND pr.name IN ('one_a8_views_ro', 'psa_one_legacy_ro', 'one_legacy_names_ro', 'one_evidence_ro')
 ORDER BY account, p.class_desc, target, p.permission_name;
GO
-- H1b 这些登录的服务器级授权
SELECT pr.name AS login_name, p.class_desc, p.permission_name, p.state_desc
  FROM sys.server_permissions p JOIN sys.server_principals pr ON pr.principal_id = p.grantee_principal_id
 WHERE pr.name IN ('one_a8_views_ro', 'psa_one_legacy_ro', 'one_legacy_names_ro', 'one_evidence_ro')
 ORDER BY login_name, p.permission_name;
GO
-- M3a 服务器级触发器（只列名称与是否启用）
SELECT name, is_disabled FROM sys.server_triggers ORDER BY name;
GO
-- M3b 老系统应用的可写登录是否为 sysadmin（NULL 表示无权判断）；以及它所属的固定服务器角色
SELECT IS_SRVROLEMEMBER('sysadmin', 'u8c') AS u8c_is_sysadmin;
GO
SELECT r.name AS role_name FROM sys.server_role_members m JOIN sys.server_principals r ON r.principal_id = m.role_principal_id
  JOIN sys.server_principals u ON u.principal_id = m.member_principal_id WHERE u.name = 'u8c' ORDER BY r.name;
GO
