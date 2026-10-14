-- B-5 · 以部门简称账号真实登录（只读事务，结束回滚）
SELECT SUSER_SNAME() AS login_name, DB_NAME() AS db, (SELECT COUNT(*) FROM dbo.v_one_legacy_org_short_name) AS departments;
GO
SELECT DISTINCT table_schema, table_name FROM information_schema.table_privileges WHERE privilege_type = 'SELECT';
SELECT permission_name FROM fn_my_permissions(NULL, 'DATABASE');
