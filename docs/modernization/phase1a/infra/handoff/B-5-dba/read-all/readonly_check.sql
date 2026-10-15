-- ADR-0037 第 4 项 (a)–(f) · 以被检账号真实登录、连到它自己的业务库执行（只读，只出名称与计数）。
-- 与应用启动校验同一组查询（backend: platform/integration/infrastructure/connection/JdbcReadOnlyCatalog.java）；
-- 期望值写死在 ReadOnlyBaseline.java（RDS 平台基线，B-5 README「有效权限平台基线」）。任一项不符，应用拒绝启动。
-- (a) 库级有效权限：期望恰好 CONNECT、KILL DATABASE CONNECTION、SELECT、SHOWPLAN、VIEW ANY COLUMN ENCRYPTION KEY DEFINITION、
--     VIEW ANY COLUMN MASTER KEY DEFINITION、VIEW DATABASE STATE
SELECT permission_name FROM fn_my_permissions(NULL, 'DATABASE') ORDER BY permission_name;
GO
-- (b) 库级授权：DENY（D）至少含 INSERT、UPDATE、DELETE、ALTER、EXECUTE、REFERENCES、CREATE TABLE / VIEW / PROCEDURE / FUNCTION / SCHEMA；
--     GRANT（G）恰好 CONNECT、SELECT；不得有 W（可转授）
SELECT state, permission_name FROM sys.database_permissions
 WHERE class = 0 AND grantee_principal_id = DATABASE_PRINCIPAL_ID() ORDER BY state, permission_name;
GO
-- (c) 角色：期望只有 public
SELECT name FROM sys.user_token WHERE principal_id <> DATABASE_PRINCIPAL_ID() ORDER BY name;
GO
-- (d) 服务器级有效权限：期望恰好 ALTER ANY CONNECTION、ALTER ANY EVENT SESSION、ALTER ANY LINKED SERVER、ALTER ANY LOGIN、
--     ALTER SERVER STATE、ALTER TRACE、CONNECT SQL、VIEW ANY DATABASE、VIEW SERVER STATE；固定服务器角色成员全为 0
SELECT permission_name FROM fn_my_permissions(NULL, 'SERVER') ORDER BY permission_name;
GO
SELECT name, IS_SRVROLEMEMBER(name) AS member FROM sys.server_principals WHERE type = 'R' AND is_fixed_role = 1 ORDER BY name;
GO
-- (e) 不是 dbo，不拥有 schema、对象、角色，无主体类（class 4）授权：期望 0、0、0、0、0
SELECT CASE WHEN DATABASE_PRINCIPAL_ID() = 1 THEN 1 ELSE 0 END AS is_dbo,
       (SELECT COUNT(*) FROM sys.schemas WHERE principal_id = DATABASE_PRINCIPAL_ID()) AS owned_schemas,
       (SELECT COUNT(*) FROM sys.objects WHERE principal_id = DATABASE_PRINCIPAL_ID()) AS owned_objects,
       (SELECT COUNT(*) FROM sys.database_principals WHERE owning_principal_id = DATABASE_PRINCIPAL_ID()) AS owned_roles,
       (SELECT COUNT(*) FROM sys.database_permissions WHERE class = 4 AND grantee_principal_id = DATABASE_PRINCIPAL_ID()) AS principal_grants;
GO
-- (f) 可进入的库：期望本库，另外只允许 master、msdb、rdscore、tempdb
SELECT DB_NAME() AS own_database;
SELECT name FROM sys.databases WHERE HAS_DBACCESS(name) = 1 ORDER BY name;
GO
