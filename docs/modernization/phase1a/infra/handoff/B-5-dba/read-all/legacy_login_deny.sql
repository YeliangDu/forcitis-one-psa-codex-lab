-- B-5 全库只读 · 以老系统库只读账号登录：写、DDL 都应被拒（事务内、零行语句，结束回滚）
-- W1 改基表
UPDATE dbo.system_dept SET name = name WHERE 1 = 0;
GO
-- W2 删基表
DELETE FROM dbo.system_users WHERE 1 = 0;
GO
-- W3 建表
CREATE TABLE dbo.one_ops_probe (x int);
GO
