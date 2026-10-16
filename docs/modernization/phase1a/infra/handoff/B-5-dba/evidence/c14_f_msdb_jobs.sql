-- C-14 (f) 基线：msdb 里本人用户能否读 sysjobs_view、拥有的 Agent 作业数、自身授权落在用户还是角色（取证账号，只出计数与布尔）。只在 a8_db 上下文出结果。
IF DB_NAME() = N'a8_db'
    SELECT N'msdb' AS system_database,
           HAS_PERMS_BY_NAME(N'msdb.dbo.sysjobs_view', N'OBJECT', N'SELECT') AS can_select_sysjobs_view,
           HAS_PERMS_BY_NAME(N'msdb.dbo.sysjobs', N'OBJECT', N'SELECT') AS can_select_sysjobs,
           IS_MEMBER(N'SQLAgentUserRole') AS in_sqlagentuserrole_in_current_db;
GO
IF DB_NAME() = N'a8_db'
    SELECT N'msdb' AS system_database, COUNT(*) AS owned_jobs FROM msdb.dbo.sysjobs_view WHERE owner_sid = SUSER_SID();
GO
IF DB_NAME() = N'a8_db'
    SELECT N'msdb' AS system_database, p.class_desc, p.permission_name, p.state_desc, COUNT(*) AS grants_directly_on_user
      FROM msdb.sys.database_permissions p JOIN msdb.sys.database_principals u ON u.principal_id = p.grantee_principal_id AND u.sid = SUSER_SID()
     GROUP BY p.class_desc, p.permission_name, p.state_desc ORDER BY p.class_desc, p.permission_name;
GO
IF DB_NAME() = N'a8_db'
    SELECT N'msdb' AS system_database, r.name AS role_name, COUNT(*) AS grants_on_role
      FROM msdb.sys.database_role_members rm
      JOIN msdb.sys.database_principals u ON u.principal_id = rm.member_principal_id AND u.sid = SUSER_SID()
      JOIN msdb.sys.database_principals r ON r.principal_id = rm.role_principal_id
      JOIN msdb.sys.database_permissions p ON p.grantee_principal_id = r.principal_id
     GROUP BY r.name ORDER BY r.name;
GO
IF DB_NAME() = N'a8_db'
    SELECT N'msdb' AS system_database, COUNT(*) AS role_memberships_of_own_user, SUM(CASE WHEN r.name = N'db_owner' THEN 1 ELSE 0 END) AS db_owner
      FROM msdb.sys.database_role_members rm
      JOIN msdb.sys.database_principals u ON u.principal_id = rm.member_principal_id AND u.sid = SUSER_SID()
      JOIN msdb.sys.database_principals r ON r.principal_id = rm.role_principal_id;
GO
