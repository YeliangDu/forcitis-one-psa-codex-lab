-- OPS-1022-OPS-01 撤号前后回读（只出有无与个数）：两个账号的库内用户与登录、两个视图、psa_one_legacy_ro 的活动连接数。one_evidence_ro（ADR-0037 第 7 项）。仅 ruoyi-vue-pro 出结果。
SET LOCK_TIMEOUT 1000;
IF DB_NAME() = N'ruoyi-vue-pro'
BEGIN
SELECT name AS database_user FROM sys.database_principals WHERE name IN (N'psa_one_legacy_ro', N'one_legacy_names_ro') ORDER BY name;
SELECT name AS login_name FROM sys.server_principals WHERE name IN (N'psa_one_legacy_ro', N'one_legacy_names_ro') ORDER BY name;
SELECT name AS view_name FROM sys.views WHERE name IN (N'v_psa_one_legacy_todo', N'v_one_legacy_org_short_name') ORDER BY name;
SELECT COUNT(*) AS active_sessions_psa_one_legacy_ro FROM sys.dm_exec_sessions WHERE login_name = N'psa_one_legacy_ro';
END
