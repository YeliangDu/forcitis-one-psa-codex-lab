-- =====================================================================================================================
-- FORCITIS one · Phase 1a · PostgreSQL 18 数据库角色与 schema 草稿（DDL 提议，未执行）
-- 目标实例：pgm-bp13vak0sew3m003（cn-hangzhou，PG 18.6 高可用），数据库 forcitis_one（与 IT 服务门户同库）
-- 会话：psa-1a-platform-bridge-reimagine · 日期：2026-10-14 · 执行属于对外变更，须经用户确认后由运维执行
-- 约定：
--   * 本文件不含任何口令；带 LOGIN 的角色在执行时由运维用 \password 或 RDS 控制台设置，口令只存服务器受保护文件 / KMS
--   * 所有对象以 one_ 前缀命名，执行前先跑 §0 冲突检查，避免与 it_portal 的角色 / schema 冲突
--   * 只授权本系统自己的 schema；不改数据库级与 public schema 的现有授权（以免影响 it_portal_runtime）
-- =====================================================================================================================

-- ---------------------------------------------------------------------------------------------------------------------
-- §0 冲突检查（只读，执行前先看结果；任一返回行即停止并报告）
-- ---------------------------------------------------------------------------------------------------------------------
SELECT rolname FROM pg_roles WHERE rolname LIKE 'one\_%';
SELECT nspname FROM pg_namespace
 WHERE nspname IN ('platform_identity', 'platform_approval', 'platform_integration', 'platform_audit', 'platform_numbering',
                   'platform_scheduling', 'organization_workforce', 'one_migration');
-- it_portal 现有对象一览（供核对，不修改）
SELECT nspname, pg_get_userbyid(nspowner) AS owner FROM pg_namespace WHERE nspname NOT LIKE 'pg\_%' AND nspname <> 'information_schema';

-- ---------------------------------------------------------------------------------------------------------------------
-- §1 角色
-- ---------------------------------------------------------------------------------------------------------------------
-- 属主角色：拥有全部 FORCITIS one schema 与对象，不能登录
CREATE ROLE one_owner NOLOGIN;

-- 迁移角色：部署时运行 Flyway（及引擎安装 / 升级时一次性运行 Flowable 建表），继承 one_owner
CREATE ROLE one_migrator LOGIN INHERIT IN ROLE one_owner CONNECTION LIMIT 2;
ALTER ROLE one_migrator SET search_path = one_migration, pg_catalog;
ALTER ROLE one_migrator SET role = one_owner;          -- 迁移建出的对象属主统一为 one_owner

-- 运行角色：应用常驻连接，只有 DML，不能建表、改表
CREATE ROLE one_app LOGIN NOINHERIT CONNECTION LIMIT 40;
ALTER ROLE one_app SET search_path = platform_approval, pg_catalog;   -- Flowable 以 platform_approval 为默认 schema
ALTER ROLE one_app SET statement_timeout = '30s';
ALTER ROLE one_app SET idle_in_transaction_session_timeout = '60s';

-- 只读排障角色（可选）：运维排查只读，不授予审计以外的个人数据写权限
CREATE ROLE one_readonly LOGIN NOINHERIT CONNECTION LIMIT 3;

GRANT CONNECT ON DATABASE forcitis_one TO one_migrator, one_app, one_readonly;

-- ---------------------------------------------------------------------------------------------------------------------
-- §2 schema（与 backend/src/main/resources/db/migration/<schema>/ 一一对应；Flyway 历史表单独放 one_migration）
-- ---------------------------------------------------------------------------------------------------------------------
CREATE SCHEMA one_migration          AUTHORIZATION one_owner;  -- flyway_schema_history
CREATE SCHEMA platform_identity      AUTHORIZATION one_owner;  -- 账号、角色、会话（Spring Session）、一次性凭证
CREATE SCHEMA platform_approval      AUTHORIZATION one_owner;  -- 审批请求、轮次、Flowable ACT_*
CREATE SCHEMA platform_integration   AUTHORIZATION one_owner;  -- 出站投递、event_publication
CREATE SCHEMA platform_audit         AUTHORIZATION one_owner;  -- 审计（只追加）
CREATE SCHEMA platform_numbering     AUTHORIZATION one_owner;  -- 单据编号规则与计数
CREATE SCHEMA platform_scheduling    AUTHORIZATION one_owner;  -- ShedLock
CREATE SCHEMA organization_workforce AUTHORIZATION one_owner;  -- 组织快照（过渡态，来源可替换）
-- 注：骨架中的样例模块 cost.timesheet / delivery.execution（delivery_execution）不在 1a 上线范围，生产前移除，不建 schema

-- ---------------------------------------------------------------------------------------------------------------------
-- §3 运行角色授权（对现有与将来由 one_owner 建出的对象都生效）
-- ---------------------------------------------------------------------------------------------------------------------
DO $$
DECLARE s text;
BEGIN
  FOREACH s IN ARRAY ARRAY['platform_identity', 'platform_approval', 'platform_integration', 'platform_numbering',
                           'platform_scheduling', 'organization_workforce']
  LOOP
    EXECUTE format('GRANT USAGE ON SCHEMA %I TO one_app, one_readonly', s);
    EXECUTE format('ALTER DEFAULT PRIVILEGES FOR ROLE one_owner IN SCHEMA %I GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO one_app', s);
    EXECUTE format('ALTER DEFAULT PRIVILEGES FOR ROLE one_owner IN SCHEMA %I GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO one_app', s);
    EXECUTE format('ALTER DEFAULT PRIVILEGES FOR ROLE one_owner IN SCHEMA %I GRANT SELECT ON TABLES TO one_readonly', s);
  END LOOP;
END $$;

-- 审计只追加：运行角色只有 INSERT / SELECT，没有 UPDATE / DELETE（另有表上触发器兜底，G34）
GRANT USAGE ON SCHEMA platform_audit TO one_app, one_readonly;
ALTER DEFAULT PRIVILEGES FOR ROLE one_owner IN SCHEMA platform_audit GRANT SELECT, INSERT ON TABLES TO one_app;
ALTER DEFAULT PRIVILEGES FOR ROLE one_owner IN SCHEMA platform_audit GRANT USAGE, SELECT ON SEQUENCES TO one_app;
ALTER DEFAULT PRIVILEGES FOR ROLE one_owner IN SCHEMA platform_audit GRANT SELECT ON TABLES TO one_readonly;

-- 运行角色不得建对象
REVOKE CREATE ON SCHEMA platform_identity, platform_approval, platform_integration, platform_audit, platform_numbering,
                        platform_scheduling, organization_workforce, one_migration FROM one_app, one_readonly;

-- ---------------------------------------------------------------------------------------------------------------------
-- §4 Flowable 引擎表（ACT_*）
-- ---------------------------------------------------------------------------------------------------------------------
-- 生产运行时 flowable.database-schema-update=false（REIMAGINED_ARCHITECTURE §3.4）。引擎安装 / 升级时，
-- 运维用 one_migrator 身份、FLOWABLE_SCHEMA_UPDATE=true 启动一次应用，ACT_* 建于 platform_approval，属主 one_owner；
-- 上面的默认授权自动覆盖到 one_app。

-- ---------------------------------------------------------------------------------------------------------------------
-- §5 撤销（回滚本文件；仅在尚无数据时使用）
-- ---------------------------------------------------------------------------------------------------------------------
-- DROP SCHEMA platform_identity, platform_approval, platform_integration, platform_audit, platform_numbering,
--             platform_scheduling, organization_workforce, one_migration CASCADE;
-- DROP ROLE one_app; DROP ROLE one_readonly; DROP ROLE one_migrator; DROP ROLE one_owner;
