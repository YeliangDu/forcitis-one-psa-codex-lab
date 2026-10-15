-- 取证账号 · 老系统库：基表与元数据可读（只出计数）
SELECT COUNT(*) AS n FROM dbo.system_users;
GO
SELECT COUNT(*) AS n FROM INFORMATION_SCHEMA.COLUMNS;
GO
