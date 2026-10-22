-- FORCITIS one · B-5 · 老系统库回滚：删除两个专用账号与两个视图。不触碰任何老表。
-- 警告：本脚本一次删除两个专用账号（psa_one_legacy_ro、one_legacy_names_ro）和两个视图，不能用于单账号撤号；单账号撤号见 phase1a/cards/OPS-1022-OPS-01。
-- 先让运维清空 one.env 中的 FORCITIS_PLATFORM_INTEGRATION_LEGACY_INBOX_URL（合并待办只显示新待办）并关闭
-- INTEGRATION_LEGACY_ENABLED、WORKFORCE_SHORT_NAME_ENABLED，重启应用后再执行。
-- 用法：sqlcmd -S <老系统 RDS 内网地址> -d ruoyi-vue-pro -U <高权限账号> -b -i legacy_90_rollback.sql
:setvar INBOX_LOGIN psa_one_legacy_ro
:setvar NAMES_LOGIN one_legacy_names_ro

IF EXISTS (SELECT 1 FROM sys.database_principals WHERE name = N'$(INBOX_LOGIN)') DROP USER [$(INBOX_LOGIN)];
IF EXISTS (SELECT 1 FROM sys.database_principals WHERE name = N'$(NAMES_LOGIN)') DROP USER [$(NAMES_LOGIN)];
GO
IF EXISTS (SELECT 1 FROM sys.server_principals WHERE name = N'$(INBOX_LOGIN)') DROP LOGIN [$(INBOX_LOGIN)];
IF EXISTS (SELECT 1 FROM sys.server_principals WHERE name = N'$(NAMES_LOGIN)') DROP LOGIN [$(NAMES_LOGIN)];
GO
DROP VIEW IF EXISTS dbo.v_psa_one_legacy_todo;
DROP VIEW IF EXISTS dbo.v_one_legacy_org_short_name;
GO

-- 期望 0 行
SELECT name FROM sys.views WHERE name IN (N'v_psa_one_legacy_todo', N'v_one_legacy_org_short_name');
SELECT name FROM sys.server_principals WHERE name IN (N'$(INBOX_LOGIN)', N'$(NAMES_LOGIN)');
