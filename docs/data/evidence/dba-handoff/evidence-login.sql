-- ADR-0037 第 5 项第 4 条 · 取证账号登录。口令在服务器端生成，只以 one_time_secret 列输出（OneSql 用新 ECS 临时公钥加密）；须在 master 上下文执行。服务器级权限随 RDS 平台给所有登录（public 等），无法 GRANT / DENY，见 README 风险登记
-- CHECK_EXPIRATION 关闭：按 90 天计划轮换（runbooks/C-8）。回滚见 evidence_rollback.sql
DECLARE @secret nvarchar(64) = N'Ev-' + CONVERT(nvarchar(64), CRYPT_GEN_RANDOM(24), 2);
DECLARE @ddl nvarchar(max) = N'CREATE LOGIN [one_evidence_ro] WITH PASSWORD = ' + QUOTENAME(@secret, N'''')
    + N', CHECK_POLICY = ON, CHECK_EXPIRATION = OFF, DEFAULT_DATABASE = [ruoyi-vue-pro]';
EXEC (@ddl);
SELECT N'one_evidence_ro' AS login_name, @secret AS one_time_secret;
GO
