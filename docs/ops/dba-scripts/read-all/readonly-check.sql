-- ADR-0037 第 4 项 (a)–(g)（(g) 与 (d)(f) 的补充见 PR #163 复审 H-1、H-2，RULING-ADR37-10）· 以被检账号真实登录、连到它自己的业务库执行（只读，只出名称与计数）。
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
--     本登录在 sys.server_permissions 的行：期望恰好一行 100 SERVER G CONNECT SQL（RULING-ADR37-11）；多出任何一行（含 DENY、
--     基线内的重复 GRANT、101 SERVER_PRINCIPAL 的 IMPERSONATE ON LOGIN::sa 等 fn_my_permissions 不列的授权）或缺少这一行都不符
SELECT class, class_desc, state, permission_name FROM sys.server_permissions
 WHERE grantee_principal_id = (SELECT principal_id FROM sys.server_principals WHERE sid = SUSER_SID()) ORDER BY class, permission_name;
GO
-- (e) 不是 dbo，不拥有 schema、对象、角色，无主体类（class 4）授权：期望 0、0、0、0、0
SELECT CASE WHEN DATABASE_PRINCIPAL_ID() = 1 THEN 1 ELSE 0 END AS is_dbo,
       (SELECT COUNT(*) FROM sys.schemas WHERE principal_id = DATABASE_PRINCIPAL_ID()) AS owned_schemas,
       (SELECT COUNT(*) FROM sys.objects WHERE principal_id = DATABASE_PRINCIPAL_ID()) AS owned_objects,
       (SELECT COUNT(*) FROM sys.database_principals WHERE owning_principal_id = DATABASE_PRINCIPAL_ID()) AS owned_roles,
       (SELECT COUNT(*) FROM sys.database_permissions WHERE class = 4 AND grantee_principal_id = DATABASE_PRINCIPAL_ID()) AS principal_grants;
GO
-- (f) 可进入的库：期望本库，另外只允许系统库（database_id <= 4）与名称恰为 rdscore 的库
SELECT DB_NAME() AS own_database;
SELECT database_id, name FROM sys.databases WHERE HAS_DBACCESS(name) = 1 ORDER BY database_id;
GO
--     系统库只能经 guest 进入：对上面列出的每个系统库，本登录自己的用户数期望为 0（下面四行按实际列出的库执行）
SELECT COUNT(*) AS master_own_users FROM master.sys.database_principals WHERE sid = SUSER_SID();
SELECT COUNT(*) AS msdb_own_users FROM msdb.sys.database_principals WHERE sid = SUSER_SID();
SELECT COUNT(*) AS rdscore_own_users FROM rdscore.sys.database_principals WHERE sid = SUSER_SID();
SELECT COUNT(*) AS tempdb_own_users FROM tempdb.sys.database_principals WHERE sid = SUSER_SID();
GO
-- (g) 库级以下（class <> 0）的授权：本登录的行，以及 public 在用户对象（major_id > 0）上的行，只允许 state = 'D'，
--     或 state = 'G' 且 permission_name = 'SELECT'；看不到 public 的行时记下来报告（应用在 SQL Server 2022 容器中能看到）
SELECT class_desc, major_id, state, permission_name, CASE WHEN grantee_principal_id = 0 THEN 'public' ELSE 'self' END AS grantee
  FROM sys.database_permissions
 WHERE class <> 0 AND (grantee_principal_id = DATABASE_PRINCIPAL_ID() OR (grantee_principal_id = 0 AND major_id > 0))
 ORDER BY grantee, class_desc, major_id, permission_name;
GO
