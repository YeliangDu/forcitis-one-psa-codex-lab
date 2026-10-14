-- B-5 · 写入与越权读取：每一批都应被拒绝（事务内、零行语句，结束回滚）
-- Q1 读基表
SELECT TOP 1 id FROM dbo.bpm_task_ext;
GO
-- Q2 读人员表
SELECT TOP 1 id FROM dbo.system_users;
GO
-- Q3 读令牌表
SELECT TOP 1 id FROM dbo.system_oauth2_access_token;
GO
-- Q4 读另一个账号的视图
SELECT TOP 1 1 FROM dbo.v_one_legacy_org_short_name;
GO
-- Q5 写基表
UPDATE dbo.system_dept SET name = name WHERE 1 = 0;
GO
-- Q6 经本视图删除
DELETE FROM dbo.v_psa_one_legacy_todo WHERE 1 = 0;
GO
-- Q7 建表
CREATE TABLE dbo.one_ops_probe (x int);
GO
-- Q8 读其他库
SELECT TOP 1 name FROM a8_db.sys.tables;
GO
