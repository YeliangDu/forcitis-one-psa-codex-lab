-- =====================================================================================================================
-- FORCITIS one · Phase 1a · PostgreSQL 18 数据库角色与 schema 草稿（DDL 提议，未执行）
-- 目标实例：pgm-bp13vak0sew3m003（cn-hangzhou，PG 18.6 高可用），数据库 **forcitis_one_dev**（同实例独立库，用户 2026-10-14：「同实例分库，生产前再定」；IT 服务门户在 forcitis_one 库，互不共享对象）
-- 会话：psa-1a-platform-bridge-reimagine · 日期：2026-10-14 · 执行属于对外变更，须经用户确认后由运维执行
-- 评审草稿，不直接执行：可执行版为 runbooks/sql/c3_dev_db.sql（dev）与 c9_prod_db.sql（生产），差异见 runbooks/C-3-dev-db.md
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
 WHERE nspname IN ('platform_identity', 'platform_approval', 'platform_integration', 'platform_audit', 'platform_numbering', 'platform_file',
                   'platform_scheduling', 'organization_workforce', 'organization_attendance', 'organization_leave', 'organization_administration', 'platform_idempotency', 'cost_timesheet', 'one_migration');
-- it_portal 现有对象一览（供核对，不修改）
SELECT nspname, pg_get_userbyid(nspowner) AS owner FROM pg_namespace WHERE nspname NOT LIKE 'pg\_%' AND nspname <> 'information_schema';

-- 扩展：法人归属期间表、角色版本与角色授予用 EXCLUDE 约束防重叠，需要 btree_gist（身份与集成的基线迁移只检查它存在）（PG 自带，属「受信任扩展」，库属主角色可建）。
-- Flyway 迁移里也有 CREATE EXTENSION IF NOT EXISTS btree_gist；若迁移角色无权建扩展，先由有权限的账号在新库执行下面一句。
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- ---------------------------------------------------------------------------------------------------------------------
-- §1 角色
-- ---------------------------------------------------------------------------------------------------------------------
-- 属主角色：拥有全部 FORCITIS one schema 与对象，不能登录
CREATE ROLE one_dev_owner NOLOGIN;

-- 迁入写入角色（ADR-0036、MIGRATION_WRITER_ROLE_DESIGN）：名字必须由属主名把 _owner 换成 _history_loader 得出（触发器精确比对）；
-- 与属主同批建好，免得 PG 16 起持 CREATEROLE 者抢建同名角色。NOLOGIN、不是属主成员、无 CREATEROLE/CREATEDB，表权限到迁入窗口再授（SELECT、INSERT，禁 UPDATE/DELETE）
CREATE ROLE one_dev_history_loader NOLOGIN;

-- 迁移角色：部署时运行 Flyway（及引擎安装 / 升级时一次性运行 Flowable 建表），继承 one_dev_owner
CREATE ROLE one_dev_migrator LOGIN INHERIT IN ROLE one_dev_owner CONNECTION LIMIT 2;
ALTER ROLE one_dev_migrator SET search_path = one_migration, pg_catalog;
ALTER ROLE one_dev_migrator SET role = one_dev_owner;          -- 迁移建出的对象属主统一为 one_dev_owner

-- 角色名带 dev：PG 角色是实例级的。生产已定沿用本实例（用户 2026-10-14：「扩容现有实例共用」），另建库 forcitis_one_prod
-- 与 one_prod_* 一套角色（本脚本把 dev 换成 prod 即可）；dev 凭据不能连生产库。连接数预算与按角色超时、work_mem 按 ADR-0025 第 2 条设置。
-- 运行角色：应用常驻连接，只有 DML，不能建表、改表
CREATE ROLE one_dev_app LOGIN NOINHERIT CONNECTION LIMIT 25;
ALTER ROLE one_dev_app SET search_path = pg_catalog, platform_approval;   -- pg_catalog 在前，防同名对象顶替内建函数；platform_approval 仍是 Flowable 的默认 schema
ALTER ROLE one_dev_app SET statement_timeout = '30s';
ALTER ROLE one_dev_app SET idle_in_transaction_session_timeout = '60s';

-- 只读排障角色（可选）：运维排查只读，不授予审计以外的个人数据写权限
CREATE ROLE one_dev_readonly LOGIN NOINHERIT CONNECTION LIMIT 3;

-- 审计维护角色：只用来执行「删除已归档并校验的审计在线月分区」函数（O-8、ADR-0048）；应用不使用此角色，口令由运维设置，连接上限 1
CREATE ROLE one_dev_audit_maintainer LOGIN NOINHERIT CONNECTION LIMIT 1;

GRANT CONNECT ON DATABASE forcitis_one_dev TO one_dev_migrator, one_dev_app, one_dev_readonly, one_dev_audit_maintainer;

-- ---------------------------------------------------------------------------------------------------------------------
-- §2 schema（与 backend/src/main/resources/db/migration/<schema>/ 一一对应；Flyway 历史表单独放 one_migration）
-- ---------------------------------------------------------------------------------------------------------------------
CREATE SCHEMA one_migration          AUTHORIZATION one_dev_owner;  -- flyway_schema_history
CREATE SCHEMA platform_identity      AUTHORIZATION one_dev_owner;  -- 账号、角色、会话（Spring Session）、一次性凭证
CREATE SCHEMA platform_approval      AUTHORIZATION one_dev_owner;  -- 审批请求、轮次、Flowable ACT_*
CREATE SCHEMA platform_integration   AUTHORIZATION one_dev_owner;  -- 出站投递、event_publication
CREATE SCHEMA platform_audit         AUTHORIZATION one_dev_owner;  -- 审计（只追加）
CREATE SCHEMA platform_numbering     AUTHORIZATION one_dev_owner;  -- 单据编号规则与计数
CREATE SCHEMA platform_file          AUTHORIZATION one_dev_owner;  -- 文件服务
CREATE SCHEMA platform_scheduling    AUTHORIZATION one_dev_owner;  -- ShedLock
CREATE SCHEMA organization_workforce AUTHORIZATION one_dev_owner;  -- 组织快照（过渡态，来源可替换）
CREATE SCHEMA organization_attendance AUTHORIZATION one_dev_owner;  -- 工作日历（考勤与休假 BC，1a 只含工作日历）
CREATE SCHEMA organization_leave      AUTHORIZATION one_dev_owner;
CREATE SCHEMA organization_administration AUTHORIZATION one_dev_owner;
CREATE SCHEMA cost_timesheet         AUTHORIZATION one_dev_owner;  -- 工时单与幂等记录（cost.timesheet 是 1a 正式模块）
-- 注：delivery.execution（delivery_execution）已删除（D-1a-127），不建 schema
CREATE SCHEMA platform_idempotency AUTHORIZATION one_dev_owner;  -- 幂等键：写请求重复发送时的应答存储（24 小时）

-- ---------------------------------------------------------------------------------------------------------------------
-- §3 运行角色授权（对现有与将来由 one_dev_owner 建出的对象都生效）
-- ---------------------------------------------------------------------------------------------------------------------
DO $$
DECLARE s text;
BEGIN
  FOREACH s IN ARRAY ARRAY['platform_identity', 'platform_approval', 'platform_integration', 'platform_numbering', 'platform_file',
                           'platform_scheduling', 'organization_workforce', 'organization_attendance', 'organization_leave', 'organization_administration', 'platform_idempotency', 'cost_timesheet']
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
REVOKE CREATE ON SCHEMA platform_identity, platform_approval, platform_integration, platform_audit, platform_numbering, platform_file,
                        platform_scheduling, organization_workforce, organization_attendance, organization_leave, organization_administration, platform_idempotency, cost_timesheet, one_migration FROM one_dev_app, one_dev_readonly;

-- ---------------------------------------------------------------------------------------------------------------------
-- §4 Flowable 引擎表（ACT_*）
-- ---------------------------------------------------------------------------------------------------------------------
-- 生产运行时 flowable.database-schema-update=false（REIMAGINED_ARCHITECTURE §3.4）。引擎安装 / 升级时，
-- 运维用 one_dev_migrator 身份、FLOWABLE_SCHEMA_UPDATE=true 启动一次应用，ACT_* 建于 platform_approval，属主 one_dev_owner；
-- 上面的默认授权自动覆盖到 one_dev_app。

-- ---------------------------------------------------------------------------------------------------------------------
-- §4b 迁移之后：版本表只追加（B2-M5；每次发布迁移后再执行一遍，幂等）
-- ---------------------------------------------------------------------------------------------------------------------
-- 本节只在 Flyway 迁移成功之后执行：表要迁移后才存在，首次执行本文件（建库、角色、schema）时跳过本节。
-- 版本表上的触发器已拒绝删除、清空和改写已关闭的行；运行角色再不持有 DELETE 与 TRUNCATE（只留 SELECT、INSERT、UPDATE，UPDATE 用来关闭开放版本）。
-- 新增版本表（数据访问注册表中角色为 DIMENSION_VERSIONED 的表）时，在这里补一项；DataArchitectureTest 核对此清单与注册表一致。
DO $$
DECLARE t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['organization_workforce.org_unit_versions', 'organization_workforce.position_versions', 'organization_workforce.assignment_versions',
                             'organization_workforce.legal_entity_versions', 'organization_workforce.employment_relationship_versions',
                             'organization_workforce.org_unit_legal_entities', 'organization_workforce.employee_legal_entities',
                             'platform_identity.role_versions', 'platform_identity.account_role_grants',
                             'organization_attendance.standard_hours_versions'] LOOP
        EXECUTE format('REVOKE DELETE, TRUNCATE ON %s FROM one_dev_app', t);
    END LOOP;
END $$;

-- 单据类型登记（ADR-0066 决定 4）：行只由迁移写（数据访问注册表 MIGRATION_ONLY），运行角色只读；表上的触发器另拒删除、清空与改写已登记的列。
REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON platform_numbering.document_types FROM one_dev_app;

-- 人事命令只追加（ADR-0068 第 1 条 7）：触发器之外，运行角色也不持有 DELETE 与 TRUNCATE；离职满 10 年的清除经属主的 SECURITY DEFINER 函数。
REVOKE DELETE, TRUNCATE ON organization_workforce.personnel_commands FROM one_dev_app;
REVOKE DELETE, TRUNCATE ON organization_workforce.org_structure_commands FROM one_dev_app;
REVOKE DELETE, TRUNCATE ON organization_workforce.attribution_countersigns FROM one_dev_app;
REVOKE UPDATE, DELETE, TRUNCATE ON organization_workforce.attribution_countersign_bases FROM one_dev_app;
REVOKE DELETE, TRUNCATE ON organization_workforce.employee_profile_change_requests FROM one_dev_app;
-- 待放行离职与人事状态转换（IMP-1030-PAR-B1）：待放行离职同人事命令，状态转换只追加，运行角色只留 SELECT、INSERT。
REVOKE DELETE, TRUNCATE ON organization_workforce.separation_holds FROM one_dev_app;
REVOKE UPDATE, DELETE, TRUNCATE ON organization_workforce.state_transitions FROM one_dev_app;

-- 角色版本与授权申请的自定义部门（F-1 #728；数据访问注册表 AGGREGATE_CHILD / APPEND_ONLY）：明细随父行同事务插入，触发器拒改删；
-- 运行角色也不持有 DELETE 与 TRUNCATE。不是版本表，不进上面的版本表清单（DataArchitectureTest 核对该清单只含 DIMENSION_VERSIONED）。
REVOKE DELETE, TRUNCATE ON platform_identity.role_version_custom_org_units FROM one_dev_app;
REVOKE DELETE, TRUNCATE ON platform_identity.authorization_request_custom_org_units FROM one_dev_app;

-- 权限调整条目（F-1 #729；数据访问注册表 AGGREGATE_ROOT / MUTABLE）：状态与关闭列由聚合改写，行本身永久保留；
-- 运行角色不持有 DELETE 与 TRUNCATE（卡 F-1 注册清单第 6 项）。
REVOKE DELETE, TRUNCATE ON platform_identity.permission_adjustments FROM one_dev_app;

-- 文件服务（ADR-0060、0061、0070；one-data 签发 IMP-1021-COO-02 单元 3）：只追加表与状态转换表运行角色只留 SELECT、INSERT；
-- 其余表运行角色不持有 DELETE 与 TRUNCATE（保存类别根表可改名称，其余由触发器限定只改状态或确认列）；视图只读。
REVOKE UPDATE, DELETE, TRUNCATE ON platform_file.file_bindings, platform_file.file_holds, platform_file.file_preservations,
    platform_file.scan_exceptions, platform_file.scan_exception_revocations, platform_file.state_transitions FROM one_dev_app;
REVOKE DELETE, TRUNCATE ON platform_file.stored_files, platform_file.stored_file_names, platform_file.preservation_categories,
    platform_file.preservation_category_versions, platform_file.false_positive_judgments, platform_file.quarantine_read_requests FROM one_dev_app;
REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON platform_file.current_file_preservations FROM one_dev_app;

-- 行政事务卡 A（R6）：配置集只推进乐观锁，版本头允许确认与撤回，条目与状态转换只追加。
REVOKE INSERT, DELETE, TRUNCATE ON organization_administration.config_sets FROM one_dev_app;
REVOKE DELETE, TRUNCATE ON organization_administration.config_set_versions FROM one_dev_app;
REVOKE UPDATE, DELETE, TRUNCATE ON organization_administration.employee_activity_type_items, organization_administration.state_transitions FROM one_dev_app;
-- 工作日历（IMP-1021-PAR-02，one-data 签发更正十）：根表与版本日期表只追加，运行角色只留 SELECT、INSERT；
-- 版本表运行角色只留 SELECT、INSERT、UPDATE（UPDATE 用于确认、撤回，关闭上一版本由触发器完成），不持有 DELETE 与 TRUNCATE。
REVOKE UPDATE, DELETE, TRUNCATE ON organization_attendance.work_calendars, organization_attendance.work_calendar_version_days FROM one_dev_app;
REVOKE DELETE, TRUNCATE ON organization_attendance.work_calendar_versions FROM one_dev_app;
REVOKE UPDATE, DELETE, TRUNCATE ON organization_attendance.standard_hours_date_overrides FROM one_dev_app;

-- 审计对象类别登记（ADR-0069 第 14 条）：行只由迁移写，运行角色只读。
REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON platform_audit.audit_subject_kinds FROM one_dev_app;

-- 审计月分区（ADR-0048）：两个函数是 SECURITY DEFINER、属主 one_dev_owner，基线已对 PUBLIC 撤销 EXECUTE。
-- 运行角色只能预建分区（应用的预建作业调用），仍无 CREATE，也不能 DETACH / DROP；删除已归档分区只有维护角色能执行，应用代码不调用。
GRANT EXECUTE ON FUNCTION platform_audit.create_audit_entry_partitions(date, date) TO one_dev_app;
GRANT USAGE ON SCHEMA platform_audit TO one_dev_audit_maintainer;
GRANT EXECUTE ON FUNCTION platform_audit.drop_archived_audit_entry_partition(date) TO one_dev_audit_maintainer;

-- ---------------------------------------------------------------------------------------------------------------------
-- §5 撤销（回滚本文件；仅在尚无数据时使用）
-- ---------------------------------------------------------------------------------------------------------------------
-- DROP SCHEMA platform_identity, platform_approval, platform_integration, platform_audit, platform_numbering, platform_file,
--             platform_scheduling, organization_workforce, organization_attendance, organization_leave, organization_administration, platform_idempotency, cost_timesheet, one_migration CASCADE;
-- DROP ROLE one_dev_audit_maintainer; DROP ROLE one_dev_app; DROP ROLE one_dev_readonly; DROP ROLE one_dev_migrator; DROP ROLE one_dev_history_loader; DROP ROLE one_dev_owner;
