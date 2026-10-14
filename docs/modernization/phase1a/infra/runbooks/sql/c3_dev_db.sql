-- FORCITIS one · C-3 · forcitis_one_dev 建库、角色、schema 与授权（可执行版，取代 ../../one_db_roles_schemas.sql 草稿）
-- 实例：pgm-bp13vak0sew3m003（PG 18）。执行人：运维，用 RDS 高权限账号；执行前须用户确认（对外变更）。
-- 用法：psql -v ON_ERROR_STOP=1 -h <RDS 内网地址> -U <高权限账号> -d forcitis_one -f c3_dev_db.sql
--      （或在 DMS 中按 STEP 顺序分段执行，STEP 2 起须切到 forcitis_one_dev 库）
-- 本文件不含口令；带 LOGIN 的角色口令在执行后用 RDS 控制台或 \password 设置。
-- 生产版 c9_prod_db.sql 由本文件经 render_prod_sql.sh 生成，不手改。

-- ===== STEP 0 冲突检查（只读；任一查询返回行即停止，结果报 one-coordinator）=====
SELECT rolname FROM pg_roles WHERE rolname LIKE 'one\_dev\_%';
SELECT datname FROM pg_database WHERE datname = 'forcitis_one_dev';
SHOW max_connections;
SELECT rolname, rolconnlimit FROM pg_roles WHERE rolconnlimit <> -1 ORDER BY rolname;

-- ===== STEP 1 建库（连 forcitis_one 执行）=====
-- 排序规则须先确认实例支持：SELECT collname FROM pg_collation WHERE collname IN ('C.utf8', 'C.UTF-8', 'ucs_basic');
CREATE DATABASE forcitis_one_dev ENCODING 'UTF8' LC_COLLATE 'C.UTF-8' LC_CTYPE 'C.UTF-8' TEMPLATE template0;
REVOKE ALL ON DATABASE forcitis_one_dev FROM PUBLIC;

\connect forcitis_one_dev

-- ===== STEP 2 schema 冲突检查（新库内，应为 0 行）=====
SELECT nspname FROM pg_namespace
 WHERE nspname IN ('platform_identity', 'platform_approval', 'platform_integration', 'platform_audit', 'platform_numbering',
                   'platform_scheduling', 'organization_workforce', 'organization_attendance', 'cost_timesheet', 'one_migration');

-- ===== STEP 3 角色 =====
CREATE ROLE one_dev_owner NOLOGIN;
-- PG 16 起 CREATEROLE 建角色只得到 ADMIN 选项；下面的 AUTHORIZATION 与 ALTER DEFAULT PRIVILEGES FOR ROLE 需要执行人能 SET ROLE
GRANT one_dev_owner TO CURRENT_USER WITH INHERIT FALSE, SET TRUE;
-- 迁移脚本含 CREATE SCHEMA IF NOT EXISTS，PG 在判断 IF NOT EXISTS 之前先检查库级 CREATE 权限
GRANT CREATE ON DATABASE forcitis_one_dev TO one_dev_owner;

CREATE ROLE one_dev_migrator LOGIN INHERIT IN ROLE one_dev_owner CONNECTION LIMIT 2;
ALTER ROLE one_dev_migrator SET search_path = one_migration, pg_catalog;
ALTER ROLE one_dev_migrator SET role = one_dev_owner;
ALTER ROLE one_dev_migrator SET lock_timeout = '10s';

CREATE ROLE one_dev_app LOGIN NOINHERIT CONNECTION LIMIT 40;
ALTER ROLE one_dev_app SET search_path = platform_approval, pg_catalog;
ALTER ROLE one_dev_app SET statement_timeout = '30s';
ALTER ROLE one_dev_app SET idle_in_transaction_session_timeout = '60s';
ALTER ROLE one_dev_app SET work_mem = '16MB';

CREATE ROLE one_dev_readonly LOGIN NOINHERIT CONNECTION LIMIT 3;
ALTER ROLE one_dev_readonly SET default_transaction_read_only = on;
ALTER ROLE one_dev_readonly SET statement_timeout = '60s';
ALTER ROLE one_dev_readonly SET idle_in_transaction_session_timeout = '60s';
ALTER ROLE one_dev_readonly SET work_mem = '8MB';

GRANT CONNECT ON DATABASE forcitis_one_dev TO one_dev_migrator, one_dev_app, one_dev_readonly;

-- ===== STEP 4 schema =====
CREATE SCHEMA one_migration           AUTHORIZATION one_dev_owner;
CREATE SCHEMA platform_identity       AUTHORIZATION one_dev_owner;
CREATE SCHEMA platform_approval       AUTHORIZATION one_dev_owner;
CREATE SCHEMA platform_integration    AUTHORIZATION one_dev_owner;
CREATE SCHEMA platform_audit          AUTHORIZATION one_dev_owner;
CREATE SCHEMA platform_numbering      AUTHORIZATION one_dev_owner;
CREATE SCHEMA platform_scheduling     AUTHORIZATION one_dev_owner;
CREATE SCHEMA organization_workforce  AUTHORIZATION one_dev_owner;
CREATE SCHEMA organization_attendance AUTHORIZATION one_dev_owner;
CREATE SCHEMA cost_timesheet          AUTHORIZATION one_dev_owner;

-- ===== STEP 5 授权（默认权限覆盖 one_dev_owner 以后建出的对象）=====
DO $$
DECLARE s text;
BEGIN
  FOREACH s IN ARRAY ARRAY['platform_identity', 'platform_approval', 'platform_integration', 'platform_numbering',
                           'platform_scheduling', 'organization_workforce', 'organization_attendance', 'cost_timesheet']
  LOOP
    EXECUTE format('GRANT USAGE ON SCHEMA %I TO one_dev_app, one_dev_readonly', s);
    EXECUTE format('ALTER DEFAULT PRIVILEGES FOR ROLE one_dev_owner IN SCHEMA %I GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO one_dev_app', s);
    EXECUTE format('ALTER DEFAULT PRIVILEGES FOR ROLE one_dev_owner IN SCHEMA %I GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO one_dev_app', s);
    EXECUTE format('ALTER DEFAULT PRIVILEGES FOR ROLE one_dev_owner IN SCHEMA %I GRANT SELECT ON TABLES TO one_dev_readonly', s);
  END LOOP;
END $$;

GRANT USAGE ON SCHEMA platform_audit TO one_dev_app, one_dev_readonly;
ALTER DEFAULT PRIVILEGES FOR ROLE one_dev_owner IN SCHEMA platform_audit GRANT SELECT, INSERT ON TABLES TO one_dev_app;
ALTER DEFAULT PRIVILEGES FOR ROLE one_dev_owner IN SCHEMA platform_audit GRANT USAGE, SELECT ON SEQUENCES TO one_dev_app;
ALTER DEFAULT PRIVILEGES FOR ROLE one_dev_owner IN SCHEMA platform_audit GRANT SELECT ON TABLES TO one_dev_readonly;

REVOKE CREATE ON SCHEMA platform_identity, platform_approval, platform_integration, platform_audit, platform_numbering,
                        platform_scheduling, organization_workforce, organization_attendance, cost_timesheet, one_migration
  FROM one_dev_app, one_dev_readonly;

-- 下一步：c3_dev_db_verify.sql；之后在 RDS 控制台为 one_dev_migrator、one_dev_app、one_dev_readonly 设口令（≥24 位随机）。
