-- B-5 · 写入与越权：每一批都应被拒绝（事务内、零行语句，结束回滚）
-- Q1–Q4（读基表、人员表、令牌表、另一视图）已被 ADR-0037 取代：账号全库只读，读取以 read-all/legacy_login_read.sql 为准，
-- 账号形状以 read-all/readonly_check.sql 核对 (a)–(g)
-- Q5 写基表
UPDATE dbo.system_dept SET name = name WHERE 1 = 0;
GO
-- Q6 经本视图删除
DELETE FROM dbo.v_one_legacy_org_short_name WHERE 1 = 0;
GO
-- Q7 建表
CREATE TABLE dbo.one_ops_probe (x int);
GO
-- Q8 读其他库
SELECT TOP 1 name FROM a8_db.sys.tables;
GO
