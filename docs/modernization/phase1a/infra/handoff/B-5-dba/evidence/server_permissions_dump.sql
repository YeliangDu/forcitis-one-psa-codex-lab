-- ADR-0037 · 新系统登录与 public 的服务器级授权快照（执行前后各一次，比对）
SELECT pr.name AS principal_name, p.state_desc, p.permission_name
  FROM sys.server_permissions p
  JOIN sys.server_principals pr ON pr.principal_id = p.grantee_principal_id
 WHERE pr.name IN ('one_a8_views_ro', 'psa_one_legacy_ro', 'one_legacy_names_ro', 'one_evidence_ro', 'public')
 ORDER BY principal_name, p.permission_name, p.state_desc;
GO
