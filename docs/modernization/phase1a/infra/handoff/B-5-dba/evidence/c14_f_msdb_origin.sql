-- C-14 (f) H-2：msdb / rdscore 里本人用户是不是建登录时由平台自动建的、msdb 里有没有会自动补回角色的 DDL 触发器（取证账号，只出时间差、布尔与计数）。只在 a8_db 上下文出结果。
IF DB_NAME() = N'a8_db'
    SELECT N'login' AS what, SUSER_SNAME() AS ignore_name_not_printed, DATEDIFF(SECOND, l.create_date, l.create_date) AS seconds_from_login,
           l.type_desc, l.is_disabled
      FROM sys.server_principals l WHERE l.sid = SUSER_SID();
GO
IF DB_NAME() = N'a8_db'
    SELECT N'msdb user' AS what, DATEDIFF(SECOND, l.create_date, u.create_date) AS seconds_after_login, DATEDIFF(SECOND, u.create_date, u.modify_date) AS seconds_to_modify
      FROM msdb.sys.database_principals u CROSS JOIN sys.server_principals l WHERE u.sid = SUSER_SID() AND l.sid = SUSER_SID();
GO
IF DB_NAME() = N'a8_db'
    SELECT N'rdscore user' AS what, DATEDIFF(SECOND, l.create_date, u.create_date) AS seconds_after_login, DATEDIFF(SECOND, u.create_date, u.modify_date) AS seconds_to_modify
      FROM rdscore.sys.database_principals u CROSS JOIN sys.server_principals l WHERE u.sid = SUSER_SID() AND l.sid = SUSER_SID();
GO
IF DB_NAME() = N'a8_db'
    SELECT N'msdb database DDL triggers' AS what, COUNT(*) AS n FROM msdb.sys.triggers WHERE parent_class = 0;
GO
IF DB_NAME() = N'a8_db'
    SELECT N'server DDL triggers visible' AS what, COUNT(*) AS n FROM sys.server_triggers;
GO
IF DB_NAME() = N'a8_db'
    SELECT N'own msdb user can alter SQLAgentOperatorRole' AS what, HAS_PERMS_BY_NAME(N'msdb.dbo', N'SCHEMA', N'ALTER') AS can_alter_schema_in_msdb, IS_SRVROLEMEMBER(N'securityadmin') AS securityadmin;
GO
