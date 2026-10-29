-- FORCITIS one · C-9 回滚：删除 forcitis_one_prod 与 one_prod_* 角色。只在库中尚无须保留的数据时使用；执行前须用户确认。
-- 不触碰 forcitis_one（IT 服务门户）库与其角色。
-- 用法：psql -v ON_ERROR_STOP=1 -h <RDS 内网地址> -U <高权限账号> -d forcitis_one -f c9_prod_db_rollback.sql

-- 先停应用（systemctl stop forcitis-one），再确认无残留连接：期望 0 行
SELECT pid, usename, application_name FROM pg_stat_activity WHERE datname = 'forcitis_one_prod';

DROP DATABASE forcitis_one_prod;

-- 角色是实例级；库已删除，角色不再拥有对象。用 IF EXISTS：按旧脚本建的库没有 history_loader（OPS-007）
DROP ROLE IF EXISTS one_prod_audit_maintainer;
DROP ROLE IF EXISTS one_prod_app;
DROP ROLE IF EXISTS one_prod_readonly;
DROP ROLE IF EXISTS one_prod_migrator;
DROP ROLE IF EXISTS one_prod_history_loader;
DROP ROLE IF EXISTS one_prod_owner;

-- 期望 0 行
SELECT rolname FROM pg_roles WHERE rolname LIKE 'one\_prod\_%';
