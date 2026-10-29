-- C-14 预检诊断：取证账号自己在 msdb、rdscore 的用户是否存在（只出个数，不出名称）。只在 a8_db 上下文出结果。
SELECT N'self in msdb' AS k, (SELECT COUNT(*) FROM msdb.sys.database_principals WHERE name = SUSER_SNAME()) AS n WHERE DB_NAME() = N'a8_db'
UNION ALL SELECT N'self in rdscore', (SELECT COUNT(*) FROM rdscore.sys.database_principals WHERE name = SUSER_SNAME()) WHERE DB_NAME() = N'a8_db'
UNION ALL SELECT N'all principals visible in msdb', (SELECT COUNT(*) FROM msdb.sys.database_principals) WHERE DB_NAME() = N'a8_db';
GO
