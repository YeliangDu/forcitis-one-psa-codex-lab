-- =====================================================================================================================
-- FORCITIS one · Phase 1a · PostgreSQL 18 数据库角色与 schema 草稿（DDL 提议，未执行）
-- 目标实例：pgm-bp13vak0sew3m003（cn-hangzhou，PG 18.6 高可用），数据库 **forcitis_one_dev**（同实例独立库，用户 2026-10-14：「同实例分库，生产前再定」；IT 服务门户在 forcitis_one 库，互不共享对象）
-- 会话：psa-1a-platform-bridge-reimagine · 日期：2026-10-14 · 执行属于对外变更，须经用户确认后由运维执行
-- 约定：
--   * 本文件不含任何口令；带 LOGIN 的角色在执行时由运维用 \password 或 RDS 控制台设置，口令只存服务器受保护文件 / KMS
--   * 所有对象以 one_ 前缀命名，执行前先跑 §0 冲突检查，避免与 it_portal 的角色 / schema 冲突
--   * 只授权本系统自己的 schema；不改数据库级与 public schema 的现有授权（以免影响 it_portal_runtime）
-- =====================================================================================================================

-- ---------------------------------------------------------------------------------------------------------------------
-- §0 建库与冲突检查（建库用 RDS 高权限账号；查询只读，任一返回行即停止并报告）
-- ---------------------------------------------------------------------------------------------------------------------
-- CREATE DATABASE forcitis_one_dev ENCODING 'UTF8' LC_COLLATE 'C.UTF-8' LC_CTYPE 'C.UTF-8' TEMPLATE template0;  -- 在 forcitis_one 之外执行
-- REVOKE ALL ON DATABASE forcitis_one_dev FROM PUBLIC;   -- 新库，不影响 it_portal
-- 迁移角色在首次运行 Flyway 时需要库级 CREATE：每个模块的第一个迁移以 CREATE SCHEMA IF NOT EXISTS 开头，PostgreSQL 先查库级 CREATE 权限再判断是否已存在
-- GRANT CREATE ON DATABASE forcitis_one_dev TO one_dev_owner;
-- 以下检查连到 forcitis_one_dev 执行（角色是实例级，库内 schema 检查针对新库）
SELECT rolname FROM pg_roles WHERE rolname LIKE 'one\_dev\_%';
SELECT nspname FROM pg_namespace
 WHERE nspname IN ('platform_identity', 'platform_approval', 'platform_integration', 'platform_audit', 'platform_numbering',
                   'platform_scheduling', 'organization_workforce', 'organization_attendance', 'cost_timesheet', 'one_migration');
-- it_portal 现有对象一览（供核对，不修改）
SELECT nspname, pg_get_userbyid(nspowner) AS owner FROM pg_namespace WHERE nspname NOT LIKE 'pg\_%' AND nspname <> 'information_schema';

-- ---------------------------------------------------------------------------------------------------------------------
-- §1 角色
-- ---------------------------------------------------------------------------------------------------------------------
-- 属主角色：拥有全部 FORCITIS one schema 与对象，不能登录
CREATE ROLE one_dev_owner NOLOGIN;

-- 迁移角色：部署时运行 Flyway（及引擎安装 / 升级时一次性运行 Flowable 建表），继承 one_dev_owner
CREATE ROLE one_dev_migrator LOGIN INHERIT IN ROLE one_dev_owner CONNECTION LIMIT 2;
ALTER ROLE one_dev_migrator SET search_path = one_migration, pg_catalog;
ALTER ROLE one_dev_migrator SET role = one_dev_owner;          -- 迁移建出的对象属主统一为 one_dev_owner

-- 角色名带 dev：PG 角色是实例级的。生产已定沿用本实例（用户 2026-10-14：「扩容现有实例共用」），另建库 forcitis_one_prod
-- 与 one_prod_* 一套角色（本脚本把 dev 换成 prod 即可）；dev 凭据不能连生产库。连接数预算与按角色超时、work_mem 按 ADR-0025 第 2 条设置。
-- 运行角色：应用常驻连接，只有 DML，不能建表、改表
CREATE ROLE one_dev_app LOGIN NOINHERIT CONNECTION LIMIT 40;
ALTER ROLE one_dev_app SET search_path = platform_approval, pg_catalog;   -- Flowable 以 platform_approval 为默认 schema
ALTER ROLE one_dev_app SET statement_timeout = '30s';
ALTER ROLE one_dev_app SET idle_in_transaction_session_timeout = '60s';

-- 只读排障角色（可选）：运维排查只读，不授予审计以外的个人数据写权限
CREATE ROLE one_dev_readonly LOGIN NOINHERIT CONNECTION LIMIT 3;

GRANT CONNECT ON DATABASE forcitis_one_dev TO one_dev_migrator, one_dev_app, one_dev_readonly;

-- ---------------------------------------------------------------------------------------------------------------------
-- §2 schema（与 backend/src/main/resources/db/migration/<schema>/ 一一对应；Flyway 历史表单独放 one_migration）
-- ---------------------------------------------------------------------------------------------------------------------
CREATE SCHEMA one_migration          AUTHORIZATION one_dev_owner;  -- flyway_schema_history
CREATE SCHEMA platform_identity      AUTHORIZATION one_dev_owner;  -- 账号、角色、会话（Spring Session）、一次性凭证
CREATE SCHEMA platform_approval      AUTHORIZATION one_dev_owner;  -- 审批请求、轮次、Flowable ACT_*
CREATE SCHEMA platform_integration   AUTHORIZATION one_dev_owner;  -- 出站投递、event_publication
CREATE SCHEMA platform_audit         AUTHORIZATION one_dev_owner;  -- 审计（只追加）
CREATE SCHEMA platform_numbering     AUTHORIZATION one_dev_owner;  -- 单据编号规则与计数
CREATE SCHEMA platform_scheduling    AUTHORIZATION one_dev_owner;  -- ShedLock
CREATE SCHEMA organization_workforce AUTHORIZATION one_dev_owner;  -- 组织快照（过渡态，来源可替换）
CREATE SCHEMA organization_attendance AUTHORIZATION one_dev_owner;  -- 工作日历（考勤与休假 BC，1a 只含工作日历）
CREATE SCHEMA cost_timesheet         AUTHORIZATION one_dev_owner;  -- 工时单与幂等记录（cost.timesheet 是 1a 正式模块）
-- 注：delivery.execution（delivery_execution）暂时保留（工时单经它的 api 取工作包与剩余工时），去留待调度确认；脚本不为它建 schema，其迁移自行建

-- ---------------------------------------------------------------------------------------------------------------------
-- §3 运行角色授权（对现有与将来由 one_dev_owner 建出的对象都生效）
-- ---------------------------------------------------------------------------------------------------------------------
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

-- 审计只追加：运行角色只有 INSERT / SELECT，没有 UPDATE / DELETE（另有表上触发器兜底，G34）
GRANT USAGE ON SCHEMA platform_audit TO one_dev_app, one_dev_readonly;
ALTER DEFAULT PRIVILEGES FOR ROLE one_dev_owner IN SCHEMA platform_audit GRANT SELECT, INSERT ON TABLES TO one_dev_app;
ALTER DEFAULT PRIVILEGES FOR ROLE one_dev_owner IN SCHEMA platform_audit GRANT USAGE, SELECT ON SEQUENCES TO one_dev_app;
ALTER DEFAULT PRIVILEGES FOR ROLE one_dev_owner IN SCHEMA platform_audit GRANT SELECT ON TABLES TO one_dev_readonly;

-- 运行角色不得建对象
REVOKE CREATE ON SCHEMA platform_identity, platform_approval, platform_integration, platform_audit, platform_numbering,
                        platform_scheduling, organization_workforce, organization_attendance, cost_timesheet, one_migration FROM one_dev_app, one_dev_readonly;

-- ---------------------------------------------------------------------------------------------------------------------
-- §4 Flowable 引擎表（ACT_*）
-- ---------------------------------------------------------------------------------------------------------------------
-- 生产运行时 flowable.database-schema-update=false（REIMAGINED_ARCHITECTURE §3.4）。引擎安装 / 升级时，
-- 运维用 one_dev_migrator 身份、FLOWABLE_SCHEMA_UPDATE=true 启动一次应用，ACT_* 建于 platform_approval，属主 one_dev_owner；
-- 上面的默认授权自动覆盖到 one_dev_app。

-- ---------------------------------------------------------------------------------------------------------------------
-- §5 撤销（回滚本文件；仅在尚无数据时使用）
-- ---------------------------------------------------------------------------------------------------------------------
-- DROP SCHEMA platform_identity, platform_approval, platform_integration, platform_audit, platform_numbering,
--             platform_scheduling, organization_workforce, organization_attendance, cost_timesheet, one_migration CASCADE;
-- DROP ROLE one_dev_app; DROP ROLE one_dev_readonly; DROP ROLE one_dev_migrator; DROP ROLE one_dev_owner;
