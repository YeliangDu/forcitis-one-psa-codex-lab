-- C-14 (f) 定位：RDS 给每个登录在 msdb、rdscore 自动建的用户带哪些角色与权限（ADR-0037 第 7 项，取证账号执行，只出角色名、权限名与计数）。
-- 取证账号只看得到自己的用户，结果作为平台默认用户的样本；各批只在 a8_db 上下文出结果。

IF DB_NAME() = N'a8_db' AND HAS_DBACCESS(N'msdb') = 1
    SELECT N'msdb' AS system_database, u.type_desc, u.authentication_type_desc, u.default_schema_name,
           (SELECT COUNT(*) FROM msdb.sys.database_role_members rm WHERE rm.member_principal_id = u.principal_id) AS roles,
           STUFF((SELECT N',' + r.name FROM msdb.sys.database_role_members rm JOIN msdb.sys.database_principals r ON r.principal_id = rm.role_principal_id
                   WHERE rm.member_principal_id = u.principal_id ORDER BY r.name FOR XML PATH(N'')), 1, 1, N'') AS role_names,
           (SELECT COUNT(*) FROM msdb.sys.database_permissions p WHERE p.grantee_principal_id = u.principal_id) AS own_grants,
           (SELECT COUNT(*) FROM msdb.sys.schemas s WHERE s.principal_id = u.principal_id) AS owned_schemas
      FROM msdb.sys.database_principals u WHERE u.sid = SUSER_SID()
ELSE IF DB_NAME() = N'a8_db'
    SELECT N'msdb' AS system_database, N'(no access)' AS type_desc;
GO
IF DB_NAME() = N'a8_db' AND HAS_DBACCESS(N'msdb') = 1
    SELECT N'msdb' AS system_database, p.class_desc, p.permission_name, p.state_desc, COUNT(*) AS grants
      FROM msdb.sys.database_permissions p JOIN msdb.sys.database_principals u ON u.principal_id = p.grantee_principal_id
     WHERE u.sid = SUSER_SID()
     GROUP BY p.class_desc, p.permission_name, p.state_desc ORDER BY p.class_desc, p.permission_name;
GO
IF DB_NAME() = N'a8_db' AND HAS_DBACCESS(N'msdb') = 1
    SELECT N'msdb' AS system_database, r.name AS role_name, p.class_desc, p.permission_name, p.state_desc, COUNT(*) AS grants
      FROM msdb.sys.database_role_members rm
      JOIN msdb.sys.database_principals u ON u.principal_id = rm.member_principal_id AND u.sid = SUSER_SID()
      JOIN msdb.sys.database_principals r ON r.principal_id = rm.role_principal_id
      JOIN msdb.sys.database_permissions p ON p.grantee_principal_id = r.principal_id
     GROUP BY r.name, p.class_desc, p.permission_name, p.state_desc ORDER BY r.name, p.permission_name;
GO
IF DB_NAME() = N'a8_db' AND HAS_DBACCESS(N'rdscore') = 1
    SELECT N'rdscore' AS system_database, u.type_desc, u.authentication_type_desc, u.default_schema_name,
           (SELECT COUNT(*) FROM rdscore.sys.database_role_members rm WHERE rm.member_principal_id = u.principal_id) AS roles,
           STUFF((SELECT N',' + r.name FROM rdscore.sys.database_role_members rm JOIN rdscore.sys.database_principals r ON r.principal_id = rm.role_principal_id
                   WHERE rm.member_principal_id = u.principal_id ORDER BY r.name FOR XML PATH(N'')), 1, 1, N'') AS role_names,
           (SELECT COUNT(*) FROM rdscore.sys.database_permissions p WHERE p.grantee_principal_id = u.principal_id) AS own_grants,
           (SELECT COUNT(*) FROM rdscore.sys.schemas s WHERE s.principal_id = u.principal_id) AS owned_schemas
      FROM rdscore.sys.database_principals u WHERE u.sid = SUSER_SID()
ELSE IF DB_NAME() = N'a8_db'
    SELECT N'rdscore' AS system_database, N'(no access)' AS type_desc;
GO
IF DB_NAME() = N'a8_db' AND HAS_DBACCESS(N'rdscore') = 1
    SELECT N'rdscore' AS system_database, p.class_desc, p.permission_name, p.state_desc, COUNT(*) AS grants
      FROM rdscore.sys.database_permissions p JOIN rdscore.sys.database_principals u ON u.principal_id = p.grantee_principal_id
     WHERE u.sid = SUSER_SID()
     GROUP BY p.class_desc, p.permission_name, p.state_desc ORDER BY p.class_desc, p.permission_name;
GO
IF DB_NAME() = N'a8_db' AND HAS_DBACCESS(N'rdscore') = 1
    SELECT N'rdscore' AS system_database, r.name AS role_name, p.class_desc, p.permission_name, p.state_desc, COUNT(*) AS grants
      FROM rdscore.sys.database_role_members rm
      JOIN rdscore.sys.database_principals u ON u.principal_id = rm.member_principal_id AND u.sid = SUSER_SID()
      JOIN rdscore.sys.database_principals r ON r.principal_id = rm.role_principal_id
      JOIN rdscore.sys.database_permissions p ON p.grantee_principal_id = r.principal_id
     GROUP BY r.name, p.class_desc, p.permission_name, p.state_desc ORDER BY r.name, p.permission_name;
GO
