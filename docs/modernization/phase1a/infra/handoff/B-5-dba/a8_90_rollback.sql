-- FORCITIS one · B-5 · A8 回滚：删除专用账号、4 个视图与 forcitis_one schema。不触碰任何 A8 基表。
-- 先让运维在 one.env 中关闭 INTEGRATION_SEEYON_VIEWS_ENABLED 与 WORKFORCE_SYNC_ENABLED 并重启应用，再执行。
-- 用法：sqlcmd -S <A8 SQL Server> -d a8_db -E -b -i a8_90_rollback.sql [-v READER_LOGIN=<登录名>]
:setvar READER_LOGIN one_a8_views_ro

IF EXISTS (SELECT 1 FROM sys.database_principals WHERE name = N'$(READER_LOGIN)') DROP USER [$(READER_LOGIN)];
GO
IF EXISTS (SELECT 1 FROM sys.server_principals WHERE name = N'$(READER_LOGIN)') DROP LOGIN [$(READER_LOGIN)];
GO
DROP VIEW IF EXISTS forcitis_one.v_org_member;
DROP VIEW IF EXISTS forcitis_one.v_org_post;
DROP VIEW IF EXISTS forcitis_one.v_org_relationship;
DROP VIEW IF EXISTS forcitis_one.v_org_unit;
GO
IF EXISTS (SELECT 1 FROM sys.schemas WHERE name = N'forcitis_one') DROP SCHEMA forcitis_one;
GO

-- 期望 0 行
SELECT name FROM sys.schemas WHERE name = N'forcitis_one';
SELECT name FROM sys.server_principals WHERE name = N'$(READER_LOGIN)';
