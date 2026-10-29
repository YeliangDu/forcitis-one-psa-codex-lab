-- B-5 全库只读 · 以 one_a8_views_ro 登录：写、DDL 都应被拒（事务内、零行语句，结束回滚）
-- W1 改基表
UPDATE dbo.ORG_UNIT SET NAME = NAME WHERE 1 = 0;
GO
-- W2 删基表
DELETE FROM dbo.ORG_MEMBER WHERE 1 = 0;
GO
-- W3 建表
CREATE TABLE dbo.one_ops_probe (x int);
GO
-- W4 在视图 schema 建表
CREATE TABLE forcitis_one.one_ops_probe (x int);
GO
