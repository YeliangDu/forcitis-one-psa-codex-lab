-- C-14 正式账号核实未通过后的定位取证（ADR-0037 第 7 项，取证账号执行，只出库名、用户类型、角色名与计数，不读业务数据）。
-- 批 1–4 只在 a8_db 上下文出结果：四个登录（含取证账号本身作对照）在系统库中的用户；取证账号的目录可见性受限，另给可见主体总数。
-- 批 5 在两库各出一次：public 在本库 class<>0、非 SELECT 的授权按类别、权限、状态、对象类型计数（只读规则 (g) 只允许 DENY 或 SELECT 的 GRANT）。

IF DB_NAME() = N'a8_db' AND HAS_DBACCESS(N'master') = 1
    SELECT N'master' AS system_database, COALESCE(SUSER_SNAME(dp.sid), dp.name) AS login_name, dp.type_desc,
           (SELECT COUNT(*) FROM master.sys.database_role_members rm JOIN master.sys.database_principals r ON r.principal_id = rm.role_principal_id
             WHERE rm.member_principal_id = dp.principal_id AND r.name NOT IN (N'public', N'guest')) AS other_roles,
           STUFF((SELECT N',' + r.name FROM master.sys.database_role_members rm JOIN master.sys.database_principals r ON r.principal_id = rm.role_principal_id
                   WHERE rm.member_principal_id = dp.principal_id AND r.name NOT IN (N'public', N'guest') FOR XML PATH(N'')), 1, 1, N'') AS role_names,
           (SELECT COUNT(*) FROM master.sys.database_principals) AS visible_principals
      FROM master.sys.database_principals dp
     WHERE dp.name IN (N'one_a8_views_ro', N'psa_one_legacy_ro', N'one_legacy_names_ro', N'one_evidence_ro') OR SUSER_SNAME(dp.sid) IN (N'one_a8_views_ro', N'psa_one_legacy_ro', N'one_legacy_names_ro', N'one_evidence_ro')
ELSE IF DB_NAME() = N'a8_db'
    SELECT N'master' AS system_database, N'(no access)' AS login_name;
GO
IF DB_NAME() = N'a8_db' AND HAS_DBACCESS(N'msdb') = 1
    SELECT N'msdb' AS system_database, COALESCE(SUSER_SNAME(dp.sid), dp.name) AS login_name, dp.type_desc,
           (SELECT COUNT(*) FROM msdb.sys.database_role_members rm JOIN msdb.sys.database_principals r ON r.principal_id = rm.role_principal_id
             WHERE rm.member_principal_id = dp.principal_id AND r.name NOT IN (N'public', N'guest')) AS other_roles,
           STUFF((SELECT N',' + r.name FROM msdb.sys.database_role_members rm JOIN msdb.sys.database_principals r ON r.principal_id = rm.role_principal_id
                   WHERE rm.member_principal_id = dp.principal_id AND r.name NOT IN (N'public', N'guest') FOR XML PATH(N'')), 1, 1, N'') AS role_names,
           (SELECT COUNT(*) FROM msdb.sys.database_principals) AS visible_principals
      FROM msdb.sys.database_principals dp
     WHERE dp.name IN (N'one_a8_views_ro', N'psa_one_legacy_ro', N'one_legacy_names_ro', N'one_evidence_ro') OR SUSER_SNAME(dp.sid) IN (N'one_a8_views_ro', N'psa_one_legacy_ro', N'one_legacy_names_ro', N'one_evidence_ro')
ELSE IF DB_NAME() = N'a8_db'
    SELECT N'msdb' AS system_database, N'(no access)' AS login_name;
GO
IF DB_NAME() = N'a8_db' AND HAS_DBACCESS(N'model') = 1
    SELECT N'model' AS system_database, COALESCE(SUSER_SNAME(dp.sid), dp.name) AS login_name, dp.type_desc,
           (SELECT COUNT(*) FROM model.sys.database_role_members rm JOIN model.sys.database_principals r ON r.principal_id = rm.role_principal_id
             WHERE rm.member_principal_id = dp.principal_id AND r.name NOT IN (N'public', N'guest')) AS other_roles,
           STUFF((SELECT N',' + r.name FROM model.sys.database_role_members rm JOIN model.sys.database_principals r ON r.principal_id = rm.role_principal_id
                   WHERE rm.member_principal_id = dp.principal_id AND r.name NOT IN (N'public', N'guest') FOR XML PATH(N'')), 1, 1, N'') AS role_names,
           (SELECT COUNT(*) FROM model.sys.database_principals) AS visible_principals
      FROM model.sys.database_principals dp
     WHERE dp.name IN (N'one_a8_views_ro', N'psa_one_legacy_ro', N'one_legacy_names_ro', N'one_evidence_ro') OR SUSER_SNAME(dp.sid) IN (N'one_a8_views_ro', N'psa_one_legacy_ro', N'one_legacy_names_ro', N'one_evidence_ro')
ELSE IF DB_NAME() = N'a8_db'
    SELECT N'model' AS system_database, N'(no access)' AS login_name;
GO
IF DB_NAME() = N'a8_db' AND HAS_DBACCESS(N'tempdb') = 1
    SELECT N'tempdb' AS system_database, COALESCE(SUSER_SNAME(dp.sid), dp.name) AS login_name, dp.type_desc,
           (SELECT COUNT(*) FROM tempdb.sys.database_role_members rm JOIN tempdb.sys.database_principals r ON r.principal_id = rm.role_principal_id
             WHERE rm.member_principal_id = dp.principal_id AND r.name NOT IN (N'public', N'guest')) AS other_roles,
           STUFF((SELECT N',' + r.name FROM tempdb.sys.database_role_members rm JOIN tempdb.sys.database_principals r ON r.principal_id = rm.role_principal_id
                   WHERE rm.member_principal_id = dp.principal_id AND r.name NOT IN (N'public', N'guest') FOR XML PATH(N'')), 1, 1, N'') AS role_names,
           (SELECT COUNT(*) FROM tempdb.sys.database_principals) AS visible_principals
      FROM tempdb.sys.database_principals dp
     WHERE dp.name IN (N'one_a8_views_ro', N'psa_one_legacy_ro', N'one_legacy_names_ro', N'one_evidence_ro') OR SUSER_SNAME(dp.sid) IN (N'one_a8_views_ro', N'psa_one_legacy_ro', N'one_legacy_names_ro', N'one_evidence_ro')
ELSE IF DB_NAME() = N'a8_db'
    SELECT N'tempdb' AS system_database, N'(no access)' AS login_name;
GO
SELECT DB_NAME() AS database_name, p.class_desc, p.permission_name, p.state_desc, COALESCE(o.type_desc, N'-') AS object_type, COUNT(*) AS grants
  FROM sys.database_permissions p
  LEFT JOIN sys.all_objects o ON p.class = 1 AND o.object_id = p.major_id
 WHERE p.grantee_principal_id = 0 AND p.class <> 0 AND NOT (p.state IN ('G', 'W') AND p.permission_name = N'SELECT') AND p.state <> 'D'
 GROUP BY p.class_desc, p.permission_name, p.state_desc, o.type_desc
 ORDER BY p.class_desc, p.permission_name, o.type_desc;
GO
