-- B-5 全库只读 · 以老系统库只读账号登录：基表、元数据与原有视图都能读（只出计数）
-- R1 人员表
SELECT COUNT(*) AS n FROM dbo.system_users;
GO
-- R2 部门表
SELECT COUNT(*) AS n FROM dbo.system_dept;
GO
-- R3 令牌表（用户「全部可读，不排除」）
SELECT COUNT(*) AS n FROM dbo.system_oauth2_access_token;
GO
-- R4 元数据
SELECT COUNT(*) AS n FROM sys.tables WHERE schema_id = SCHEMA_ID('dbo');
GO
