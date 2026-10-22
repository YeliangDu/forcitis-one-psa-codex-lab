-- 行政事务基线：按卡 A 签发草案建立配置集、状态转换与员工活动类型。
CREATE SCHEMA IF NOT EXISTS organization_administration;
COMMENT ON SCHEMA organization_administration IS '行政事务（组织与人员 BC · 行政事务）：用印、信息技术服务、资产使用、员工活动及其版本化配置';

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'btree_gist') THEN
        RAISE EXCEPTION 'extension btree_gist is missing: the database setup script (one_db_roles_schemas.sql) creates it before the migrations run';
    END IF;
END $$;

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
CREATE FUNCTION organization_administration.refuse_transition_rewrite() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
BEGIN
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

CREATE FUNCTION organization_administration.guard_config_set_change() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
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

CREATE FUNCTION organization_administration.guard_config_set_version_insert() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
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
CREATE FUNCTION organization_administration.guard_config_set_version_change() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
DECLARE
    confirmation_columns text[] := ARRAY['confirmation_status', 'confirmed_by', 'confirmed_at', 'decision_audit_entry_id', 'updated_at', 'version'];
    withdrawal_columns   text[] := ARRAY['confirmation_status', 'withdrawn_by', 'withdrawn_at', 'withdraw_reason_text', 'updated_at', 'version'];
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

        SELECT * INTO previous FROM organization_administration.config_set_versions
         WHERE config_set_id = NEW.config_set_id AND confirmation_status = 'CONFIRMED' AND version_number < NEW.version_number
         ORDER BY version_number DESC LIMIT 1;
        IF previous.config_set_version_id IS NOT NULL THEN
            IF NEW.effective_from_date < confirmation_day THEN
                RAISE EXCEPTION 'organization_administration.config_set_versions: effective_from_date % is earlier than the confirmation day %', NEW.effective_from_date, confirmation_day
                    USING ERRCODE = 'check_violation';
            END IF;
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

CREATE FUNCTION organization_administration.assert_config_version_complete(p_config_set_version_id uuid, p_config_set_code text) RETURNS void LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
DECLARE
    item_count bigint;
BEGIN
    IF p_config_set_code = 'EMPLOYEE_ACTIVITY_TYPES' THEN
        SELECT count(*) INTO item_count FROM organization_administration.employee_activity_type_items WHERE config_set_version_id = p_config_set_version_id;
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

CREATE FUNCTION organization_administration.guard_config_item_insert() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
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
CREATE FUNCTION organization_administration.refuse_append_only_rewrite() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
BEGIN
    RAISE EXCEPTION '%.% is append-only: % is not allowed', TG_TABLE_SCHEMA, TG_TABLE_NAME, TG_OP USING ERRCODE = 'restrict_violation';
END $$;

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

INSERT INTO organization_administration.config_sets (config_set_id, config_set_code, owner_role_code, created_at, updated_at, version) VALUES
    ('019a0000-0000-7000-8000-000000000801', 'SEAL_APPROVERS', 'administration_head', TIMESTAMPTZ '2026-10-22 05:48:11+00', TIMESTAMPTZ '2026-10-22 05:48:11+00', 0),
    ('019a0000-0000-7000-8000-000000000802', 'POSITION_ASSET_STANDARDS', 'administration_head', TIMESTAMPTZ '2026-10-22 05:48:11+00', TIMESTAMPTZ '2026-10-22 05:48:11+00', 0),
    ('019a0000-0000-7000-8000-000000000803', 'REMINDER_RULES', 'administration_head', TIMESTAMPTZ '2026-10-22 05:48:11+00', TIMESTAMPTZ '2026-10-22 05:48:11+00', 0),
    ('019a0000-0000-7000-8000-000000000804', 'IT_SERVICE_CATEGORIES', 'it_head', TIMESTAMPTZ '2026-10-22 05:48:11+00', TIMESTAMPTZ '2026-10-22 05:48:11+00', 0),
    ('019a0000-0000-7000-8000-000000000805', 'EMPLOYEE_ACTIVITY_TYPES', 'employee_relations_head', TIMESTAMPTZ '2026-10-22 05:48:11+00', TIMESTAMPTZ '2026-10-22 05:48:11+00', 0);
