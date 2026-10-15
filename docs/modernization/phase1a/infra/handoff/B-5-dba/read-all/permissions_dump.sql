-- B-5 全库只读 · 新系统只读账号与取证账号在当前库的授权快照（执行前后各一次，比对）
SELECT pr.name AS account, p.class_desc, ISNULL(s.name, '') AS schema_name, CASE WHEN p.class = 1 THEN OBJECT_SCHEMA_NAME(p.major_id) + '.' + OBJECT_NAME(p.major_id) ELSE '' END AS object_name,
       p.state_desc, p.permission_name
  FROM sys.database_permissions p
  JOIN sys.database_principals pr ON pr.principal_id = p.grantee_principal_id
  LEFT JOIN sys.schemas s ON p.class = 3 AND s.schema_id = p.major_id
 WHERE pr.name IN ('one_a8_views_ro', 'psa_one_legacy_ro', 'one_legacy_names_ro', 'one_evidence_ro')
 ORDER BY account, p.class_desc, schema_name, object_name, p.state_desc, p.permission_name;
GO
SELECT m.name AS account, r.name AS role_name
  FROM sys.database_role_members rm
  JOIN sys.database_principals m ON m.principal_id = rm.member_principal_id
  JOIN sys.database_principals r ON r.principal_id = rm.role_principal_id
 WHERE m.name IN ('one_a8_views_ro', 'psa_one_legacy_ro', 'one_legacy_names_ro', 'one_evidence_ro');
GO
