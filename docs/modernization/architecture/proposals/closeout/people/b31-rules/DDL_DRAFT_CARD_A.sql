-- ═══════════════════════════════════════════════════════════════════════════════
-- 更正（2026-10-22，卡 A 验收测试发现）：status_of_aggregate 的 CHECK 在 from_status 为空时非空分支求值为 NULL，CHECK 对 NULL 放行，∅→CONCLUDED 等非法行被接受；已用 coalesce(…, false) 包住。
-- DDL 草案（供 one-data 签发）——行政事务（organization.administration）卡 A：基线、配置集、状态转换、员工活动类型
-- 草案状态：未签发，不是迁移文件。签发并改好后落成
--   backend/src/main/resources/db/migration/organization_administration/V<yyyyMMddHHmmss>__organization_administration_baseline.sql
-- （NAMING_STANDARD §2.3 文件名规则；时间戳在落迁移时取，不在本草案里占号）。
--
-- 依据
--   * B31_ADM02_BLUEPRINT.md §1、§2、§5、§6 卡 A；
--   * B31_PHYSICAL_MODEL_RULINGS.md（claude/one-data-handoff-1）§2（config_sets / config_set_versions / 条目表）、§4（子实体转换）、
--     §5（迁入历史状态）、§6（决定出处列名）、§7、§14（第 4 点：state_transitions 随基线按 AGG-116 登记，列名用 transition_sequence_number）；
--   * ADR-0058（state_transitions 模板，STATE_TRANSITIONS_DESIGN v2 第 1 节）；ADR-0075（已采纳，文件在 claude/one-architecture-adr0075，PR #552 合入 main 前以该分支为准：
--     决定 1 表形状与列名不变，仅 aggregate_id 扩为「状态持有者 ID」，transition_sequence_number 取该行自己的 version；决定 2 适用的子行须各自带 version；
--     决定 3 乐观锁以根为准；决定 4、5 同行多次变化序号依次递增、允许跳号）；ADR-0072 决定 1（版本行上的四眼、禁改触发器只放行 PENDING → CONFIRMED、decision_audit_entry_id）；
--   * one-architecture 裁定 B31_ADM02_RULINGS_2026-10-22.md G-6（配置集版本记转换、带 version 列，已并入下文）；
--   * 先例：platform_file 基线 V20261021120000 第 0 节（state_transitions）、第 5 节（preservation_categories / preservation_category_versions
--     及其三个触发器）；organization_workforce 的 *_versions（daterange + EXCLUDE 写法）；NAMING_STANDARD §2.4、§2.4a；SESSION_PLAYBOOK §14、§14.1 D1–D19。
--
-- 基线开放期（W-12）：本文件是新 schema 的基线，合入 main 之前可改；合入之后，后续卡（其余四张条目表、卡 C 的表、SEAL_APPROVERS 完整性分支）一律追加 V 文件，不再改基线文件。
-- 范围（只含这些）：schema、btree_gist 检查、state_transitions、config_sets、config_set_versions、assert_config_version_complete、employee_activity_type_items。
-- 不含：employee_activity_requests / employee_activity_participants（卡 C）、其余四张条目表（seal_approver_rule_items、it_service_category_items、
--       position_asset_standard_items、reminder_rule_items，各卡自带）、任何 Java、注册表与角色脚本（见文末「随附改动」，仅列清单）。
--
-- 设计要点（对照 one-data 裁定 §2）
--   1. 配置不挂聚合：config_sets（根，五行种子）+ config_set_versions（版本头）+ 各配置一张条目表，条目表经复合外键固定 config_set_code。
--   2. 版本头：只在 PENDING 插入（version 从 1 起）；PENDING 可确认（CONFIRMED）或撤回（WITHDRAWN，终态，版本号不回收）；已确认版本生效区间排他（EXCLUDE，仅 CONFIRMED 行）；
--      每个配置集至多一个 PENDING（部分唯一索引）；四眼 CHECK；已有前序已确认版本时，生效日不早于确认日（确认时查，确认日取库的事务时间）；
--      确认时空版本拒收（assert_config_version_complete）；关闭前一已确认版本只在后继版本的确认触发器内发生，应用不单独关闭；
--      版本行是状态持有者（G-6、ADR-0075）：带 version 列，PENDING → CONFIRMED / WITHDRAWN 各记一条 state_transitions，关闭不记转换。
--   3. 条目表：追加表；只在所属版本 PENDING 时可插入（FOR SHARE 锁住版本行，与并发确认串行）；确认后拒绝；UPDATE / DELETE / TRUNCATE 一律拒。
--   4. 时间列一律由应用注入，库内无 now() 默认；业务值无数据库默认；主键 UUIDv7 由应用生成；无 jsonb、无软删除、无 created_by / updated_by（裁定 §14 第 3 点）。
--
-- ═══ one-data 审（B31-CARD-A-DDL.md，2026-10-22）结论与本稿处理 ═══════════════════════════════════
-- 审结论「需修改」，七项必改（R1–R7）已并入下文；W-1…W-13 的裁定如下。草案仍未在数据库上执行，迁移出来后另复核实际表结构 diff。
-- R1  【已改】config_set_versions 加终态 WITHDRAWN（PENDING → WITHDRAWN）：列 withdrawn_by / withdrawn_at / withdraw_reason_text 与具名联动 CHECK，版本号不回收；
--     state_transitions 的 to_status 取值并集与 status_of_aggregate（ConfigSetVersion 分支）加 WITHDRAWN；撤回同事务写转换并推进版本行 version 与根 version（见第 2 节说明）。
-- R2  【已改】确认日取 (transaction_timestamp() AT TIME ZONE 'Asia/Shanghai')::date，不取应用写入的 confirmed_at（后者只作记录）。
-- R3  【已改】关闭前一已确认版本只在后继版本的确认触发器内发生：pg_trigger_depth() > 1、存在待确认后继且其生效日等于被写入的关闭日、被关闭的是该配置集最大号的已确认版本；
--     零长区间（同日修订）保持 >=；确认时「上一版」取同配置集版本号最大的已确认版本；应用不再单独发关闭 UPDATE。
-- R4  【已改】确认触发器调用 assert_config_version_complete(版本 ID, 配置集代码)：本卡放函数框架与通用检查（所有配置集至少一条条目），
--     SEAL_APPROVERS 的兜底检查分支留注释，由 7.4 后续卡以追加迁移 CREATE OR REPLACE 追加（W-12）。
-- R5  【已改】删 decision_ref_key（决定出处只用 decision_audit_entry_id）；author_id 保持 NOT NULL；不做种子版本（初始版本由负责角色经界面起草、另一人确认，
--     上线出口条件是两张表定版）。将来若出现 MIGRATION 来源的配置版本，再按 SEC-1021-COO-01 的做法追加 source_kind 与属主写入检查。
-- R6  【已改】权限回收写在文末「随附改动」：config_sets 回收 INSERT、DELETE、TRUNCATE（保留 SELECT、UPDATE）；config_set_versions 回收 DELETE、TRUNCATE（保留 SELECT、INSERT、UPDATE）；
--     条目表与 state_transitions 只留 SELECT、INSERT。三处脚本须同改且一致（scripts/ci/db_scripts_aligned.py 核对），见文末。
-- R7  【已改】种子角色代码未定名：已查 platform_identity 迁移（只有 super_admin、employee、process_administrator、org_data_admin、business_admin、security_auditor 六个系统角色）
--     与 docs（roles.yaml 只有 ROLE-56 行政职能人员等岗位，无角色代码；glossary 无「行政负责人 / IT 负责人 / 员工关系负责人」词条），三者都没有现成角色代码。
--     故五行种子不进本草案的可执行部分，也不把注释块带进迁移正文；见下方「待定名清单」。归属读法按 one-data 认可：
--     SEAL_APPROVERS、POSITION_ASSET_STANDARDS、REMINDER_RULES = 行政负责人，IT_SERVICE_CATEGORIES = IT 负责人，EMPLOYEE_ACTIVITY_TYPES = 员工关系负责人。
--
-- 待定名清单（须在落迁移前由 one-glossary / platform_identity 定名并建角色；迁移里的种子只写定名后的代码，DB 只做格式 CHECK，不验角色存在，跨 schema）：
--   * 行政负责人角色代码        —— 待定名（owner_role_code：SEAL_APPROVERS、POSITION_ASSET_STANDARDS、REMINDER_RULES）
--   * IT 负责人角色代码         —— 待定名（owner_role_code：IT_SERVICE_CATEGORIES）
--   * 员工关系负责人角色代码     —— 待定名（owner_role_code：EMPLOYEE_ACTIVITY_TYPES）
--   三个角色须先在 platform_identity 以追加迁移建立（种子 roles 行），本 schema 的种子迁移排在其后；在此之前 config_sets 无种子行，空库验收「五行」场景挂起。
--
-- W-1  【已裁，认可】配置集版本写转换、带 version 列（G-6、ADR-0075）。
-- W-2  【已裁】state_transitions 的 CHECK 写法以 platform_file 实装为准：_status 列有取值 CHECK（DdlNamingTest 要求）加 ck_state_transitions_status_of_aggregate 按聚合成对限定。
--      每加聚合类型，aggregate_type_code、from/to 并集与 status_of_aggregate 三处 CHECK 走 DROP + ADD ... NOT VALID + VALIDATE（NAMING_STANDARD §2.4a 第 6 点）。
-- W-3  【已裁，待卡 C】AGG-116 状态集合：DRAFT 是否落库由卡 C 与 one-rules 定（数据侧原则不留死取值；若「提交即创建为已批准」则 DRAFT 不落库，初始转换只有 ∅ → APPROVED）。
--      本卡 state_transitions 并集里的 DRAFT、CONCLUDED、CANCELLED 按审查意见先保留，卡 C 的 DDL 再审后若有死取值，以追加迁移收窄。DRAFT / APPROVED / CONCLUDED / CANCELLED
--      均为提议术语（administration_status__*，英文值未批准）。ConfigSetVersion 自己的取值不留死值：不含迁入（MIGRATION）初始转换，见 R5。
-- W-4  【已裁】见 R1。
-- W-5  【已裁】见 R5。
-- W-6  【已裁】见 R3。
-- W-7  【已裁】见 R7。
-- W-8  【已裁】见 R6。
-- W-9  【已裁】见 R4。
-- W-10 【已裁，同意】config_set_code 的五个取值写在具名 CHECK 里，新增配置集追加迁移改 CHECK。
-- W-11 【已裁，同意】命名沿用先例（tg_<表>_insert_guard / _guard / _append_only 加 _truncate；排他约束 ex_ 前缀；fk_config_set_versions_config_set）；
--      排他约束 ex_ 前缀由 one-architecture 在 NAMING_STANDARD §2.4 补一行。
-- W-12 【已裁】基线开放期：本稿建新 schema 基线（尚未合入 main，可自由改）；基线合入 main 之后，后续卡（各条目表、卡 C 的表、SEAL_APPROVERS 的完整性分支）
--      一律追加 V 文件（日历 #547 同做法），不再改基线文件。
-- W-13 提议术语（词表无）：交 one-glossary 登记，登记前列注释开头用同名中文，登记后核对——失效日（effective_to_date）、版本号（version_number）、负责角色代码（owner_role_code）、
--      配置集代码（config_set_code）、活动类型代码（activity_type_code）、活动类型名称（activity_type_name）、是否启用（is_enabled）、撤回人 / 撤回时间 / 撤回原因
--      （withdrawn_by / withdrawn_at / withdraw_reason_text，日历 #547 已用同名）、转换序号 / 起因 / 原状态 / 新状态等 state_transitions 列用语（bc 为 platform.file 的提议须按 bc 另登或改共享内核）。
--      config_set / config_set_version 现为 entity，若代码里要有聚合类须改 aggregate（G40）。
-- btree_gist：只检查存在（DO 块），由建库脚本预建，不用 CREATE EXTENSION（one-data 同意）。
-- D19：ex_config_set_versions_overlap 的 gist 索引支撑「取某日有效版本」查询，读取 SQL 须与索引表达式一致
--      （daterange(effective_from_date, effective_to_date) @> 日期 且带 confirmation_status = 'CONFIRMED'），随实现用 IT 验证走索引。
-- ═══════════════════════════════════════════════════════════════════════════════

CREATE SCHEMA IF NOT EXISTS organization_administration;
COMMENT ON SCHEMA organization_administration IS '行政事务（组织与人员 BC · 行政事务）：用印、信息技术服务、资产使用、员工活动及其版本化配置';

-- 排他约束需要 btree_gist，由建库脚本预建，迁移只检查存在（同 platform_identity、platform_integration 基线，DDL-28；one-data 同意，不用 CREATE EXTENSION）。
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'btree_gist') THEN
        RAISE EXCEPTION 'extension btree_gist is missing: the database setup script (one_db_roles_schemas.sql) creates it before the migrations run';
    END IF;
END $$;

-- ═══ 0. 状态转换（ADR-0058、ADR-0075；STATE_TRANSITIONS_DESIGN v2 第 1 节模板，<schema> = organization_administration）════════
CREATE TABLE organization_administration.state_transitions (
    id                      uuid        NOT NULL,
    aggregate_type_code     text        NOT NULL CONSTRAINT ck_state_transitions_aggregate_type_code CHECK (aggregate_type_code IN ('EmployeeActivityRequest', 'ConfigSetVersion')),
    aggregate_id            uuid        NOT NULL,
    transition_sequence_number bigint   NOT NULL,
    from_status             text,
    to_status               text        NOT NULL,
    occurred_on_date        date        NOT NULL,
    recorded_at             timestamptz NOT NULL,
    legal_entity_id         uuid,
    actor_type              text        NOT NULL,
    actor_id                uuid        NOT NULL,
    cause_kind              text        NOT NULL,
    command_id              uuid,
    event_id                uuid,
    correlation_id          uuid        NOT NULL,
    reverses_transition_id  uuid,
    created_at              timestamptz NOT NULL,
    updated_at              timestamptz NOT NULL,
    CONSTRAINT pk_state_transitions PRIMARY KEY (id),
    CONSTRAINT uk_state_transitions_aggregate_id_transition_sequence_number UNIQUE (aggregate_id, transition_sequence_number),
    CONSTRAINT uk_state_transitions_id_aggregate_id UNIQUE (id, aggregate_id),
    CONSTRAINT fk_state_transitions_reverses_transition_id FOREIGN KEY (reverses_transition_id, aggregate_id)
        REFERENCES organization_administration.state_transitions (id, aggregate_id) ON DELETE RESTRICT,
    CONSTRAINT ck_state_transitions_from_status CHECK (from_status IN ('DRAFT', 'APPROVED', 'CONCLUDED', 'PENDING')),
    CONSTRAINT ck_state_transitions_to_status CHECK (to_status IN ('DRAFT', 'APPROVED', 'CONCLUDED', 'CANCELLED', 'PENDING', 'CONFIRMED', 'WITHDRAWN')),
    CONSTRAINT ck_state_transitions_transition_sequence_number CHECK (transition_sequence_number >= 0),
    CONSTRAINT ck_state_transitions_statuses_differ CHECK (from_status IS DISTINCT FROM to_status),
    CONSTRAINT ck_state_transitions_first CHECK (from_status IS NOT NULL OR cause_kind = 'MIGRATION' OR transition_sequence_number <= 1),
    CONSTRAINT ck_state_transitions_actor_type CHECK (actor_type IN ('USER', 'SYSTEM')),
    CONSTRAINT ck_state_transitions_cause_kind CHECK (cause_kind IN ('COMMAND', 'REVERSAL', 'CORRECTION', 'MIGRATION', 'SYSTEM_JOB')),
    CONSTRAINT ck_state_transitions_trigger CHECK (num_nonnulls(command_id, event_id) <= 1
        AND (cause_kind <> 'COMMAND' OR command_id IS NOT NULL)
        AND (cause_kind <> 'SYSTEM_JOB' OR command_id IS NULL)
        AND (cause_kind <> 'MIGRATION' OR num_nonnulls(command_id, event_id) = 0)),
    CONSTRAINT ck_state_transitions_reversal CHECK ((cause_kind IN ('REVERSAL', 'CORRECTION')) = (reverses_transition_id IS NOT NULL)),
    CONSTRAINT ck_state_transitions_migration CHECK (cause_kind <> 'MIGRATION' OR from_status IS NULL),
    CONSTRAINT ck_state_transitions_status_of_aggregate CHECK (coalesce(
        (aggregate_type_code = 'EmployeeActivityRequest'
            AND ((from_status IS NULL AND (to_status IN ('DRAFT', 'APPROVED')
                                           OR (cause_kind = 'MIGRATION' AND to_status IN ('DRAFT', 'APPROVED', 'CONCLUDED', 'CANCELLED'))))
                 OR (from_status = 'DRAFT' AND to_status = 'APPROVED')
                 OR (from_status = 'APPROVED' AND to_status IN ('CONCLUDED', 'CANCELLED'))
                 OR (from_status = 'CONCLUDED' AND to_status = 'CANCELLED')))
        OR (aggregate_type_code = 'ConfigSetVersion'
            AND ((from_status IS NULL AND to_status = 'PENDING')
                 OR (from_status = 'PENDING' AND to_status IN ('CONFIRMED', 'WITHDRAWN')))), false)),
    CONSTRAINT ck_state_transitions_times CHECK (created_at = recorded_at AND updated_at = recorded_at)
);
CREATE INDEX ix_state_transitions_legal_entity_id_occurred_on_date ON organization_administration.state_transitions (legal_entity_id, occurred_on_date) WHERE legal_entity_id IS NOT NULL;
CREATE INDEX ix_state_transitions_recorded_at ON organization_administration.state_transitions (recorded_at, id);
CREATE FUNCTION organization_administration.refuse_transition_rewrite() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    -- 清理只有一条通道：schema 属主角色经 SECURITY DEFINER 函数 organization_administration.purge_expired_state_transitions（随保留期清理卡建）删除到期的整个聚合；其他角色都被拒。
    IF TG_OP = 'DELETE' AND current_user = (SELECT pg_get_userbyid(c.relowner) FROM pg_class c WHERE c.oid = TG_RELID) THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'state transitions are append-only: % on %.% is refused', lower(TG_OP), TG_TABLE_SCHEMA, TG_TABLE_NAME
        USING ERRCODE = 'restrict_violation';
END $$;
CREATE TRIGGER tg_state_transitions_append_only BEFORE UPDATE OR DELETE ON organization_administration.state_transitions
    FOR EACH ROW EXECUTE FUNCTION organization_administration.refuse_transition_rewrite();
CREATE TRIGGER tg_state_transitions_append_only_truncate BEFORE TRUNCATE ON organization_administration.state_transitions
    FOR EACH STATEMENT EXECUTE FUNCTION organization_administration.refuse_transition_rewrite();
COMMENT ON TABLE organization_administration.state_transitions IS '状态转换记录：本 schema 各状态持有者每次状态变更一行，只追加，回退也正向记（ADR-0058、ADR-0075）。粒度：每次状态变更一行';
COMMENT ON COLUMN organization_administration.state_transitions.id IS '状态转换 ID（UUIDv7）';
COMMENT ON COLUMN organization_administration.state_transitions.aggregate_type_code IS '聚合类型代码：状态持有者的类型，本基线有 EmployeeActivityRequest（聚合根）与 ConfigSetVersion（配置集版本行，带自己状态机的子行，G-6；状态 PENDING / CONFIRMED / WITHDRAWN）；后续卡随各自状态机追加';
COMMENT ON COLUMN organization_administration.state_transitions.aggregate_id IS '聚合 ID：状态持有者（聚合根，或带自己状态机的子行，ADR-0075 决定 1）的 ID；ConfigSetVersion 取 config_set_version_id，无外键（多态主体）';
COMMENT ON COLUMN organization_administration.state_transitions.transition_sequence_number IS '转换序号：本次变更之后该状态持有者行自己的 version，与该行同事务递增；严格递增、唯一、允许跳号（ADR-0075 决定 5）；ConfigSetVersion 取 config_set_versions.version';
COMMENT ON COLUMN organization_administration.state_transitions.from_status IS '原状态：为空表示新建或迁入的初始状态；取值为本 schema 各状态机的状态并集，合法流转见下方清单与 ck_state_transitions_status_of_aggregate';
COMMENT ON COLUMN organization_administration.state_transitions.to_status IS '新状态';
COMMENT ON COLUMN organization_administration.state_transitions.occurred_on_date IS '发生日期：业务日期（Asia/Shanghai）';
COMMENT ON COLUMN organization_administration.state_transitions.recorded_at IS '记录时间：注入时钟';
COMMENT ON COLUMN organization_administration.state_transitions.legal_entity_id IS '法人主体 ID：为空表示该状态持有者无法人归属；EmployeeActivityRequest 必填，取申请人在业务日期当天的任职法人（裁定 §14 第 1 点），由注册表 state_transitions_legal_entity: required 与行为守护保证；ConfigSetVersion 恒为空（配置不属于任何法人）';
COMMENT ON COLUMN organization_administration.state_transitions.actor_type IS '操作者类型：USER 用户 / SYSTEM 系统操作者（作业、事件监听按 system-actors.txt 登记的系统操作者）';
COMMENT ON COLUMN organization_administration.state_transitions.actor_id IS '操作人 ID：用户 ID 或系统操作者 UUIDv5';
COMMENT ON COLUMN organization_administration.state_transitions.cause_kind IS '起因：COMMAND / REVERSAL / CORRECTION / MIGRATION / SYSTEM_JOB';
COMMENT ON COLUMN organization_administration.state_transitions.command_id IS '命令 ID：为空表示不是命令触发';
COMMENT ON COLUMN organization_administration.state_transitions.event_id IS '事件 ID：为空表示不是事件触发';
COMMENT ON COLUMN organization_administration.state_transitions.correlation_id IS '关联号：同一请求或作业的一串变更共用';
COMMENT ON COLUMN organization_administration.state_transitions.reverses_transition_id IS '抵消的转换 ID：为空表示不是冲销或更正';
COMMENT ON COLUMN organization_administration.state_transitions.created_at IS '创建时间（恒等于记录时间）';
COMMENT ON COLUMN organization_administration.state_transitions.updated_at IS '更新时间（恒等于记录时间）';

-- 合法流转清单（应用写转换行；根表触发器按同一清单拒绝非法流转，随卡 C 的表建；终态后状态不可改）：
--   聚合类型                状态列                                 from → to
--   EmployeeActivityRequest employee_activity_requests.status      ∅ → DRAFT；∅ → APPROVED（DRAFT 是否落库见 W-3）；DRAFT → APPROVED；APPROVED → CONCLUDED；
--                                                                  APPROVED → CANCELLED；CONCLUDED → CANCELLED（终态 CANCELLED）；迁入 ∅ → 上述四个状态之一
--   ConfigSetVersion        config_set_versions.confirmation_status ∅ → PENDING（起草，序号 1）；PENDING → CONFIRMED（确认）；PENDING → WITHDRAWN（撤回，终态）
--                                                                  （本卡无迁入来源，不留 MIGRATION 初始转换，R5）
--   关闭已确认版本（写 effective_to_date）不是状态变化，不写转换，version 照常推进，序号因此跳号（ADR-0075 决定 5）；关闭由后继版本的确认触发器在库内完成。
--   ConfigSetVersion 的转换行由应用在同一事务里写：主体＝版本行 ID，序号＝该行写入后的 version（见下文第 2 节触发器说明）。
-- 系统操作者见 backend/architecture/system-actors.txt（organization.administration.*，随卡 C / 卡 D 登记）。

-- ═══ 1. 配置集（根）════════════════════════════════════════════════
-- 配置不挂聚合（D17 细则 4：一张版本表一个来源、一个负责人）。五个配置集共用一对根表与版本头，各配置的内容行各一张条目表。
CREATE TABLE organization_administration.config_sets (
    config_set_id    uuid        NOT NULL,
    config_set_code  text        NOT NULL,
    owner_role_code  text        NOT NULL,
    created_at       timestamptz NOT NULL,
    updated_at       timestamptz NOT NULL,
    version          bigint      NOT NULL DEFAULT 0,
    CONSTRAINT pk_config_sets PRIMARY KEY (config_set_id),
    CONSTRAINT uk_config_sets_config_set_code UNIQUE (config_set_code),
    CONSTRAINT uk_config_sets_config_set_id_config_set_code UNIQUE (config_set_id, config_set_code),
    CONSTRAINT ck_config_sets_config_set_code CHECK (config_set_code IN ('SEAL_APPROVERS', 'IT_SERVICE_CATEGORIES', 'POSITION_ASSET_STANDARDS', 'EMPLOYEE_ACTIVITY_TYPES', 'REMINDER_RULES')),
    CONSTRAINT ck_config_sets_owner_role_code CHECK (owner_role_code ~ '^[a-z][a-z0-9_]{1,59}$'),
    CONSTRAINT ck_config_sets_version CHECK (version >= 0)
);
COMMENT ON TABLE organization_administration.config_sets IS '配置集：一类版本化配置的根，以配置集代码区分用印审批人、信息技术服务类别、岗位资产配置标准、员工活动类型、提醒规则，并记负责该配置的角色；内容在 config_set_versions 与各条目表，不挂在任何聚合下（B31 裁定 §2）。每个配置集一行，由迁移种子写入。粒度：每个配置集一行';
COMMENT ON COLUMN organization_administration.config_sets.config_set_id IS '配置集 ID（UUIDv7）';
COMMENT ON COLUMN organization_administration.config_sets.config_set_code IS '配置集代码：业务键，SEAL_APPROVERS / IT_SERVICE_CATEGORIES / POSITION_ASSET_STANDARDS / EMPLOYEE_ACTIVITY_TYPES / REMINDER_RULES';
COMMENT ON COLUMN organization_administration.config_sets.owner_role_code IS '负责角色代码：该配置的维护与定版负责角色（行政负责人 / IT 负责人 / 员工关系负责人），写边界落在这里；取值为 platform_identity 角色代码，跨 schema 不建外键';
COMMENT ON COLUMN organization_administration.config_sets.created_at IS '创建时间';
COMMENT ON COLUMN organization_administration.config_sets.updated_at IS '更新时间：只随乐观锁版本变化';
COMMENT ON COLUMN organization_administration.config_sets.version IS '乐观锁版本：每条命令（起草、确认、撤回，确认时库内关闭前一版本也随确认命令，哪怕只改版本行）都推进它，并发冲突一律在这一行判定（ADR-0075 决定 3）';

CREATE FUNCTION organization_administration.guard_config_set_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP IN ('DELETE', 'TRUNCATE') THEN
        RAISE EXCEPTION 'organization_administration.config_sets: % is not allowed', TG_OP USING ERRCODE = 'restrict_violation';
    END IF;
    IF (to_jsonb(NEW) - ARRAY['updated_at', 'version']) IS DISTINCT FROM (to_jsonb(OLD) - ARRAY['updated_at', 'version']) THEN
        RAISE EXCEPTION 'organization_administration.config_sets: only the optimistic-lock columns may change' USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER tg_config_sets_guard BEFORE UPDATE OR DELETE ON organization_administration.config_sets
    FOR EACH ROW EXECUTE FUNCTION organization_administration.guard_config_set_change();
CREATE TRIGGER tg_config_sets_guard_truncate BEFORE TRUNCATE ON organization_administration.config_sets
    FOR EACH STATEMENT EXECUTE FUNCTION organization_administration.guard_config_set_change();

-- ═══ 2. 配置集版本（版本头，K-2 形状；形状同 platform_file.preservation_category_versions，另加 WITHDRAWN 与 version）═══
CREATE TABLE organization_administration.config_set_versions (
    config_set_version_id   uuid        NOT NULL,
    config_set_id           uuid        NOT NULL,
    config_set_code         text        NOT NULL,
    version_number          integer     NOT NULL,
    effective_from_date     date        NOT NULL,
    effective_to_date       date,
    confirmation_status     text        NOT NULL,
    author_id               uuid        NOT NULL,
    authored_at             timestamptz NOT NULL,
    confirmed_by            uuid,
    confirmed_at            timestamptz,
    decision_audit_entry_id uuid,
    withdrawn_by            uuid,
    withdrawn_at            timestamptz,
    withdraw_reason_text    text,
    created_at              timestamptz NOT NULL,
    updated_at              timestamptz NOT NULL,
    version                 bigint      NOT NULL DEFAULT 1,
    CONSTRAINT pk_config_set_versions PRIMARY KEY (config_set_version_id),
    CONSTRAINT uk_config_set_versions_config_set_id_version_number UNIQUE (config_set_id, version_number),
    CONSTRAINT uk_config_set_versions_version_id_config_set_code UNIQUE (config_set_version_id, config_set_code),
    CONSTRAINT fk_config_set_versions_config_set FOREIGN KEY (config_set_id, config_set_code)
        REFERENCES organization_administration.config_sets (config_set_id, config_set_code) ON DELETE RESTRICT,
    CONSTRAINT ck_config_set_versions_version_number CHECK (version_number >= 1),
    CONSTRAINT ck_config_set_versions_version CHECK (version >= 1),
    CONSTRAINT ck_config_set_versions_confirmation_status CHECK (confirmation_status IN ('PENDING', 'CONFIRMED', 'WITHDRAWN')),
    CONSTRAINT ck_config_set_versions_effective_range CHECK (effective_to_date IS NULL OR effective_to_date >= effective_from_date),
    CONSTRAINT ck_config_set_versions_pending_is_open CHECK (confirmation_status = 'CONFIRMED' OR effective_to_date IS NULL),
    CONSTRAINT ck_config_set_versions_confirmed CHECK ((confirmation_status = 'CONFIRMED') = (confirmed_by IS NOT NULL)
                                                       AND (confirmed_by IS NULL) = (confirmed_at IS NULL)),
    CONSTRAINT ck_config_set_versions_confirmer_is_not_author CHECK (confirmed_by IS NULL OR confirmed_by <> author_id),
    CONSTRAINT ck_config_set_versions_decision_source CHECK ((confirmation_status = 'CONFIRMED') = (decision_audit_entry_id IS NOT NULL)),
    CONSTRAINT ck_config_set_versions_withdrawal_columns CHECK (
        (confirmation_status = 'WITHDRAWN') = (withdrawn_by IS NOT NULL)
        AND (withdrawn_by IS NULL) = (withdrawn_at IS NULL) AND (withdrawn_by IS NULL) = (withdraw_reason_text IS NULL)
        AND (withdraw_reason_text IS NULL OR (withdraw_reason_text = btrim(withdraw_reason_text) AND char_length(withdraw_reason_text) BETWEEN 1 AND 500))),
    CONSTRAINT ex_config_set_versions_overlap EXCLUDE USING gist (config_set_id WITH =, daterange(effective_from_date, effective_to_date) WITH &&)
        WHERE (confirmation_status = 'CONFIRMED')
);
CREATE UNIQUE INDEX uk_config_set_versions_pending ON organization_administration.config_set_versions (config_set_id)
    WHERE confirmation_status = 'PENDING';
COMMENT ON TABLE organization_administration.config_set_versions IS '配置集版本：配置型版本头，自带状态（待确认 / 已确认 / 已撤回）和 version，状态变化记入 state_transitions；内容不可变、只补确认或撤回、已确认的开放版本只在后继版本确认时被库关闭；「待确认」与「已撤回」版本不得被读取使用；每个配置集至多一个待确认版本，已确认版本的生效区间互不重叠；按业务日期取已确认且区间包含该日的版本，找不到失败告警（D15、D17）。粒度：每个配置集每个版本一行';
COMMENT ON COLUMN organization_administration.config_set_versions.config_set_version_id IS '配置集版本 ID（UUIDv7）：单据保存所用版本时存它';
COMMENT ON COLUMN organization_administration.config_set_versions.config_set_id IS '配置集 ID';
COMMENT ON COLUMN organization_administration.config_set_versions.config_set_code IS '配置集代码：与配置集一致的冗余，使复合外键能把条目表固定到对应的配置集';
COMMENT ON COLUMN organization_administration.config_set_versions.version_number IS '版本号：每个配置集从 1 递增；同日修订以版本号区分（D6）；已撤回版本的号不回收';
COMMENT ON COLUMN organization_administration.config_set_versions.effective_from_date IS '生效日：作者提交内容的一部分；已有前序已确认版本的，确认时早于确认日（按库的事务时间取日）即拒（不追溯）';
COMMENT ON COLUMN organization_administration.config_set_versions.effective_to_date IS '失效日（不含）：为空表示至今仍有效；只在已确认的开放版本被后继版本取代时由后继版本的确认触发器写入一次，等于后继版本的生效日；等于本版本生效日表示同日被取代的空区间';
COMMENT ON COLUMN organization_administration.config_set_versions.confirmation_status IS '确认状态：PENDING 待确认 / CONFIRMED 已确认 / WITHDRAWN 已撤回（终态，版本号不回收）；只能由待确认改成已确认或已撤回';
COMMENT ON COLUMN organization_administration.config_set_versions.author_id IS '作者用户 ID：保存该版本的人，恒非空（初始版本也由负责角色经界面起草）；确认人不得是作者（四眼）';
COMMENT ON COLUMN organization_administration.config_set_versions.authored_at IS '保存该版本的时间';
COMMENT ON COLUMN organization_administration.config_set_versions.confirmed_by IS '确认人用户 ID：为空表示未确认';
COMMENT ON COLUMN organization_administration.config_set_versions.confirmed_at IS '确认时间：应用注入，只作记录；为空表示未确认。「生效日不早于确认日」的确认日取库的事务时间，不取本列';
COMMENT ON COLUMN organization_administration.config_set_versions.decision_audit_entry_id IS '决定审计记录 ID：确认操作同一事务写入的审计记录（ADR-0072 决定 1 第 3 点），是该版本决定出处的唯一来源；确认后非空，为空表示未确认或已撤回；无跨 schema 外键';
COMMENT ON COLUMN organization_administration.config_set_versions.withdrawn_by IS '撤回人的用户账号 ID；为空表示未撤回';
COMMENT ON COLUMN organization_administration.config_set_versions.withdrawn_at IS '撤回时间；为空表示未撤回';
COMMENT ON COLUMN organization_administration.config_set_versions.withdraw_reason_text IS '撤回原因；为空表示未撤回，撤回时必填（1 至 500 字、不带首尾空白）';
COMMENT ON COLUMN organization_administration.config_set_versions.created_at IS '创建时间';
COMMENT ON COLUMN organization_administration.config_set_versions.updated_at IS '更新时间：确认、撤回、被后继版本关闭时各更新一次';
COMMENT ON COLUMN organization_administration.config_set_versions.version IS '乐观锁版本：本行自己的 version，起草时为 1，确认、撤回与被关闭各推进一次；同时是本行状态转换的序号来源（ADR-0075 决定 1、2），关闭不写转换故序号可跳号；并发冲突以配置集根的 version 判定，不以本列判定（决定 3）';

-- 版本行与转换的配合（ADR-0075 决定 1～5，G-6）：
--   * 起草：插入 PENDING 行（version = 1），同事务写转换 ∅ → PENDING（aggregate_type_code = 'ConfigSetVersion'，aggregate_id = 版本行 ID，序号 1）。
--   * 确认：UPDATE 只放行 PENDING → CONFIRMED 与确认列（confirmed_by、confirmed_at、decision_audit_entry_id）及 version、updated_at；version 必须增大，
--     应用同事务写转换 PENDING → CONFIRMED，序号＝写入后的 version，并推进 config_sets.version（决定 3：乐观锁以根为准，所有命令都推进根）。
--     同一触发器内：检查条目完整（assert_config_version_complete）、生效日不早于事务日、并关闭前一已确认版本（见下，应用不单独发关闭 UPDATE）。
--   * 撤回：UPDATE 只放行 PENDING → WITHDRAWN 与撤回列（withdrawn_by、withdrawn_at、withdraw_reason_text）及 version、updated_at；
--     应用同事务写转换 PENDING → WITHDRAWN（序号＝写入后的 version）并推进 config_sets.version；终态，版本号不回收，条目表插入守护只认 PENDING，撤回后自然关闭。
--   * 关闭：只在后继版本的确认触发器内发生（嵌套触发器 pg_trigger_depth() > 1），把前一版本最大号已确认且仍开放的版本的 effective_to_date 写成后继版本的生效日；
--     version 与 updated_at 照常推进，不写转换（不是状态变化），序号因此跳号（决定 5）。
--   * 触发器只守版本行本身（BEFORE 触发器看不到同事务随后写入的转换行）；转换行是否写、序号是否等于 version，由领域方法、仓储与行为守护保证（注册表登记，见文末）。
CREATE FUNCTION organization_administration.guard_config_set_version_insert() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.version <> 1 THEN
        RAISE EXCEPTION 'organization_administration.config_set_versions: a new version row starts at version 1 (ADR-0075 decision 3)'
            USING ERRCODE = 'check_violation';
    END IF;
    IF NEW.confirmation_status <> 'PENDING' OR NEW.confirmed_by IS NOT NULL OR NEW.confirmed_at IS NOT NULL
            OR NEW.decision_audit_entry_id IS NOT NULL OR NEW.effective_to_date IS NOT NULL THEN
        RAISE EXCEPTION 'organization_administration.config_set_versions: a version is inserted PENDING and confirmed by another person later'
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END $$;
CREATE FUNCTION organization_administration.guard_config_set_version_change() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    confirmation_columns text[] := ARRAY['confirmation_status', 'confirmed_by', 'confirmed_at', 'decision_audit_entry_id', 'updated_at', 'version'];
    withdrawal_columns   text[] := ARRAY['confirmation_status', 'withdrawn_by', 'withdrawn_at', 'withdraw_reason_text', 'updated_at', 'version'];
    -- 确认日取库的事务时间，不取应用写入的 confirmed_at（后者只作记录），倒填绕不过去（one-data 更正十，同日历 #547）。
    confirmation_day     date := (transaction_timestamp() AT TIME ZONE 'Asia/Shanghai')::date;
    previous             organization_administration.config_set_versions;
    closed               integer;
BEGIN
    IF TG_OP IN ('DELETE', 'TRUNCATE') THEN
        RAISE EXCEPTION 'organization_administration.config_set_versions: % is not allowed', TG_OP USING ERRCODE = 'restrict_violation';
    END IF;
    IF NEW.version <= OLD.version THEN
        RAISE EXCEPTION 'organization_administration.config_set_versions: version must increase on every change (ADR-0075 decision 2)'
            USING ERRCODE = 'restrict_violation';
    END IF;

    -- 关闭前一已确认版本：只在后继版本的确认触发器内（嵌套深度 > 1）发生；存在待确认的后继、其生效日等于写入的关闭日，且被关闭的是该配置集最大号的已确认版本。
    IF pg_trigger_depth() > 1 AND OLD.confirmation_status = 'CONFIRMED' AND NEW.confirmation_status = 'CONFIRMED'
            AND OLD.effective_to_date IS NULL AND NEW.effective_to_date IS NOT NULL
            AND (to_jsonb(NEW) - ARRAY['effective_to_date', 'updated_at', 'version']) IS NOT DISTINCT FROM (to_jsonb(OLD) - ARRAY['effective_to_date', 'updated_at', 'version'])
            AND EXISTS (SELECT 1 FROM organization_administration.config_set_versions successor
                         WHERE successor.config_set_id = OLD.config_set_id AND successor.version_number > OLD.version_number
                           AND successor.confirmation_status = 'PENDING' AND successor.effective_from_date = NEW.effective_to_date)
            AND OLD.version_number = (SELECT max(latest.version_number) FROM organization_administration.config_set_versions latest
                                       WHERE latest.config_set_id = OLD.config_set_id AND latest.confirmation_status = 'CONFIRMED') THEN
        RETURN NEW;
    END IF;

    IF OLD.confirmation_status = 'PENDING' AND NEW.confirmation_status = 'WITHDRAWN'
            AND (to_jsonb(NEW) - withdrawal_columns) IS NOT DISTINCT FROM (to_jsonb(OLD) - withdrawal_columns) THEN
        RETURN NEW;
    END IF;

    IF OLD.confirmation_status = 'PENDING' AND NEW.confirmation_status = 'CONFIRMED'
            AND (to_jsonb(NEW) - confirmation_columns) IS NOT DISTINCT FROM (to_jsonb(OLD) - confirmation_columns) THEN
        PERFORM organization_administration.assert_config_version_complete(NEW.config_set_version_id, NEW.config_set_code);

        -- 上一版 = 同配置集版本号最大的已确认版本（已撤回的不算）；没有就是首个已确认版本，生效日不受确认日约束。
        SELECT * INTO previous FROM organization_administration.config_set_versions
         WHERE config_set_id = NEW.config_set_id AND confirmation_status = 'CONFIRMED' AND version_number < NEW.version_number
         ORDER BY version_number DESC LIMIT 1;
        IF previous.config_set_version_id IS NOT NULL THEN
            IF NEW.effective_from_date < confirmation_day THEN
                RAISE EXCEPTION 'organization_administration.config_set_versions: effective_from_date % is earlier than the confirmation day %', NEW.effective_from_date, confirmation_day
                    USING ERRCODE = 'check_violation';
            END IF;
            -- 零长区间（同日修订）允许：关闭日等于上一版的生效日，ck_config_set_versions_effective_range 用 >=。
            UPDATE organization_administration.config_set_versions
               SET effective_to_date = NEW.effective_from_date, updated_at = NEW.updated_at, version = version + 1
             WHERE config_set_version_id = previous.config_set_version_id AND effective_to_date IS NULL;
            GET DIAGNOSTICS closed = ROW_COUNT;
            IF closed <> 1 THEN
                RAISE EXCEPTION 'organization_administration.config_set_versions: the previous confirmed version is not open, cannot be succeeded' USING ERRCODE = 'check_violation';
            END IF;
        END IF;
        RETURN NEW;
    END IF;

    RAISE EXCEPTION 'organization_administration.config_set_versions: only PENDING -> CONFIRMED, PENDING -> WITHDRAWN with their own columns, or the closing made by the successor''s confirmation, is allowed'
        USING ERRCODE = 'restrict_violation';
END $$;
CREATE TRIGGER tg_config_set_versions_insert_guard BEFORE INSERT ON organization_administration.config_set_versions
    FOR EACH ROW EXECUTE FUNCTION organization_administration.guard_config_set_version_insert();
CREATE TRIGGER tg_config_set_versions_guard BEFORE UPDATE OR DELETE ON organization_administration.config_set_versions
    FOR EACH ROW EXECUTE FUNCTION organization_administration.guard_config_set_version_change();
CREATE TRIGGER tg_config_set_versions_guard_truncate BEFORE TRUNCATE ON organization_administration.config_set_versions
    FOR EACH STATEMENT EXECUTE FUNCTION organization_administration.guard_config_set_version_change();

-- 版本定版前的完整性检查（R4）：所有配置集至少一条条目；各配置集特有的检查按配置集代码分支。
-- 本卡只放框架与 EMPLOYEE_ACTIVITY_TYPES 分支；其余四个配置集的条目表由各自的卡建，对应分支由后续卡以追加迁移 CREATE OR REPLACE 本函数追加（W-12）。
-- 尚未追加分支的配置集不静默放行：直接失败，避免空版本或未检查的版本被确认。
CREATE FUNCTION organization_administration.assert_config_version_complete(p_config_set_version_id uuid, p_config_set_code text) RETURNS void LANGUAGE plpgsql AS $$
DECLARE
    item_count bigint;
BEGIN
    IF p_config_set_code = 'EMPLOYEE_ACTIVITY_TYPES' THEN
        SELECT count(*) INTO item_count FROM organization_administration.employee_activity_type_items WHERE config_set_version_id = p_config_set_version_id;
    -- SEAL_APPROVERS：除至少一条条目外，另检查非高风险行必有兜底规则、高风险行无兜底；该分支（连同 seal_approver_rule_items 表）由 7.4 后续卡以追加迁移 CREATE OR REPLACE 追加。
    -- IT_SERVICE_CATEGORIES、POSITION_ASSET_STANDARDS、REMINDER_RULES：各自的条目表与分支同样由后续卡追加。
    ELSE
        RAISE EXCEPTION 'organization_administration.config_set_versions: the completeness check for config set % is not installed yet', p_config_set_code
            USING ERRCODE = 'feature_not_supported';
    END IF;
    IF item_count = 0 THEN
        RAISE EXCEPTION 'organization_administration.config_set_versions: a version of % needs at least one item before it can be confirmed', p_config_set_code
            USING ERRCODE = 'check_violation';
    END IF;
END $$;
COMMENT ON FUNCTION organization_administration.assert_config_version_complete(uuid, text) IS '配置集版本定版前的完整性检查：确认触发器调用；所有配置集至少一条条目，各配置集特有检查按配置集代码分支，由各卡以追加迁移 CREATE OR REPLACE 追加；尚未装分支的配置集失败而不放行';

-- ═══ 3. 条目表共用函数（各条目表一个插入触发器、一个只追加触发器；本卡定义，后续卡的条目表复用）═══
-- 条目只能在所属版本待确认时插入：先对版本行加共享锁，使并发的确认（需要独占该行）与插入串行，确认之后插入一律拒。
CREATE FUNCTION organization_administration.guard_config_item_insert() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    v_status text;
BEGIN
    SELECT confirmation_status INTO v_status
      FROM organization_administration.config_set_versions
     WHERE config_set_version_id = (to_jsonb(NEW) ->> 'config_set_version_id')::uuid
       FOR SHARE;
    IF v_status IS DISTINCT FROM 'PENDING' THEN
        RAISE EXCEPTION '%.%: items can only be inserted while the configuration version is PENDING', TG_TABLE_SCHEMA, TG_TABLE_NAME
            USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END $$;
CREATE FUNCTION organization_administration.refuse_append_only_rewrite() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION '%.% is append-only: % is not allowed', TG_TABLE_SCHEMA, TG_TABLE_NAME, TG_OP USING ERRCODE = 'restrict_violation';
END $$;

-- ═══ 4. 员工活动类型条目（EMPLOYEE_ACTIVITY_TYPES 的内容行；HD-01：不在已确认版本里或已停用的类型不得登记活动）═══
CREATE TABLE organization_administration.employee_activity_type_items (
    employee_activity_type_item_id uuid        NOT NULL,
    config_set_version_id          uuid        NOT NULL,
    config_set_code                text        NOT NULL CONSTRAINT ck_employee_activity_type_items_config_set_code CHECK (config_set_code = 'EMPLOYEE_ACTIVITY_TYPES'),
    activity_type_code             text        NOT NULL,
    activity_type_name             text        NOT NULL,
    is_enabled                     boolean     NOT NULL,
    created_at                     timestamptz NOT NULL,
    updated_at                     timestamptz NOT NULL,
    CONSTRAINT pk_employee_activity_type_items PRIMARY KEY (employee_activity_type_item_id),
    CONSTRAINT uk_employee_activity_type_items_version_activity_type_code UNIQUE (config_set_version_id, activity_type_code),
    CONSTRAINT fk_employee_activity_type_items_config_set_version FOREIGN KEY (config_set_version_id, config_set_code)
        REFERENCES organization_administration.config_set_versions (config_set_version_id, config_set_code) ON DELETE RESTRICT,
    CONSTRAINT ck_employee_activity_type_items_activity_type_code CHECK (activity_type_code ~ '^[A-Z][A-Z0-9_]{1,39}$'),
    CONSTRAINT ck_employee_activity_type_items_activity_type_name CHECK (activity_type_name <> '' AND char_length(activity_type_name) <= 100),
    CONSTRAINT ck_employee_activity_type_items_times CHECK (updated_at = created_at)
);
COMMENT ON TABLE organization_administration.employee_activity_type_items IS '员工活动类型条目：EMPLOYEE_ACTIVITY_TYPES 配置集某一版本里的一个活动类型（周年贺卡、节日活动、创新项目发布等），只追加、只在版本待确认时可插入；停用以新版本里 is_enabled 为假表达，不删除（B31 裁定 §2）。粒度：每个版本每个活动类型一行';
COMMENT ON COLUMN organization_administration.employee_activity_type_items.employee_activity_type_item_id IS '员工活动类型条目 ID（UUIDv7）';
COMMENT ON COLUMN organization_administration.employee_activity_type_items.config_set_version_id IS '配置集版本 ID：所属版本，必须属于 EMPLOYEE_ACTIVITY_TYPES 配置集';
COMMENT ON COLUMN organization_administration.employee_activity_type_items.config_set_code IS '配置集代码：固定为 EMPLOYEE_ACTIVITY_TYPES，使复合外键只能挂到对应配置集的版本';
COMMENT ON COLUMN organization_administration.employee_activity_type_items.activity_type_code IS '员工活动类型代码：业务键，同一版本内唯一；活动申请按它引用类型，并同时保存所用版本 ID';
COMMENT ON COLUMN organization_administration.employee_activity_type_items.activity_type_name IS '员工活动类型名称';
COMMENT ON COLUMN organization_administration.employee_activity_type_items.is_enabled IS '是否启用：真表示该类型在本版本生效期内可登记活动，假表示已停用';
COMMENT ON COLUMN organization_administration.employee_activity_type_items.created_at IS '创建时间';
COMMENT ON COLUMN organization_administration.employee_activity_type_items.updated_at IS '更新时间（行不可更新，恒等于创建时间）';
CREATE TRIGGER tg_employee_activity_type_items_insert_guard BEFORE INSERT ON organization_administration.employee_activity_type_items
    FOR EACH ROW EXECUTE FUNCTION organization_administration.guard_config_item_insert();
CREATE TRIGGER tg_employee_activity_type_items_append_only BEFORE UPDATE OR DELETE ON organization_administration.employee_activity_type_items
    FOR EACH ROW EXECUTE FUNCTION organization_administration.refuse_append_only_rewrite();
CREATE TRIGGER tg_employee_activity_type_items_append_only_truncate BEFORE TRUNCATE ON organization_administration.employee_activity_type_items
    FOR EACH STATEMENT EXECUTE FUNCTION organization_administration.refuse_append_only_rewrite();

-- ═══ 5. 五行配置集种子（R7：角色代码待定名，不在本草案里放可执行或注释形式的种子块）═══════════════════
-- 种子迁移（落迁移时单独成段）只在三个负责角色定名并经 platform_identity 建立后写入；其形状为每个配置集一行：
--   config_set_id（固定 UUIDv7 字面量，沿 platform_numbering 种子的 019a0000-… 写法）、config_set_code、owner_role_code（定名后的角色代码）、
--   created_at / updated_at（迁移写入的时间字面量，不用 now() 默认）、version = 0。
-- 归属（one-data 认可）：SEAL_APPROVERS、POSITION_ASSET_STANDARDS、REMINDER_RULES = 行政负责人；IT_SERVICE_CATEGORIES = IT 负责人；EMPLOYEE_ACTIVITY_TYPES = 员工关系负责人。
-- 角色代码见文件头「待定名清单」。不做种子版本（R5）。

-- ═══ 随附改动（不在迁移文件内；只列清单，由卡 A 的实现一并提交，我不改）═════════════════════════════
-- 权限（R6）。三处脚本须同改且一致：
--   (1) docs/modernization/phase1a/infra/one_db_roles_schemas.sql（参照 DDL）；
--   (2) backend/src/test/resources/init-dba-schemas.sql（测试库建库脚本）；
--   (3) docs/modernization/phase1a/infra/runbooks/sql/ 的 c3_dev_db*.sql（c3_dev_db.sql 建 schema 与授权循环，c3_dev_db_after_migrations.sql 放表级 REVOKE，另有 c3_dev_db_verify.sql / c3_dev_db_rollback.sql；若有 c9 生产对应脚本同改）。
--   scripts/ci/db_scripts_aligned.py 核三处 schema 清单、角色、授权一致。
--   CREATE SCHEMA organization_administration AUTHORIZATION one_dev_owner;  -- 并入 schema 清单检查、授权循环、REVOKE CREATE 与 §5 回滚清单
--   REVOKE UPDATE, DELETE, TRUNCATE ON organization_administration.employee_activity_type_items, organization_administration.state_transitions FROM one_dev_app;  -- 只留 SELECT、INSERT
--   REVOKE DELETE, TRUNCATE ON organization_administration.config_set_versions FROM one_dev_app;      -- 保留 SELECT、INSERT、UPDATE：起草、确认、撤回（关闭由确认触发器内完成）
--   REVOKE INSERT, DELETE, TRUNCATE ON organization_administration.config_sets FROM one_dev_app;      -- 保留 SELECT、UPDATE：乐观锁；五行由迁移写入（W-8）
-- 其余：application.properties 的 spring.flyway.schemas、DataArchitectureTest.SCHEMAS、
--   data-access-registry.yaml（config_sets：CONFIG_VERSIONED + versions_table: organization_administration.config_set_versions，MUTABLE 仅乐观锁列变；
--   config_set_versions：AGGREGATE_CHILD + CONDITIONAL_UPDATE（含 version 列；状态持有者，按 ADR-0075 决定 6 实现标记接口并在 ddd/aggregates.yaml 登记为历史主体，
--   state_transitions_legal_entity: none:<配置不属于任何法人>）；employee_activity_type_items：AGGREGATE_CHILD + APPEND_ONLY；state_transitions：LOG_APPEND_ONLY；
--   四表保留期 PERMANENT，lineage NEW）、data-classification.txt（activity_type_name 等 INTERNAL）、ddd/aggregates.yaml、术语登记（W-13）、
--   unversioned-table-writes.txt（无 version 列的追加表写入）。
