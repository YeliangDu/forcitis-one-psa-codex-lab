-- C-14 (f)：直接读 msdb.dbo.sysjobs 的实际结果（不依赖 HAS_PERMS_BY_NAME），取证账号，只出计数；被拒时 OneSql 打印错误号。只在 a8_db 上下文执行。
SELECT COUNT(*) AS sysjobs_rows_total FROM msdb.dbo.sysjobs WHERE DB_NAME() = N'a8_db';
GO
SELECT COUNT(*) AS sysjobs_owned_by_me FROM msdb.dbo.sysjobs WHERE DB_NAME() = N'a8_db' AND owner_sid = SUSER_SID();
GO
