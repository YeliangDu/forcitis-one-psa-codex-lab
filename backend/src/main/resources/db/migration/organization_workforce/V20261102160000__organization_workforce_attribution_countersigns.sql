-- IMP-1030-FC-B（#893，one-design 签发 #893 评论及两次修订）：法人归属会签的表与状态转换。只建表、列、约束、触发器与登记，没有应用代码。

-- 1. 人事命令：法人变更命令、待法人归属会签状态、目标法人与会签依据。
ALTER TABLE organization_workforce.personnel_commands
    ADD COLUMN target_legal_entity_id                uuid,
    ADD COLUMN countersign_previous_legal_entity_id  uuid,
    ADD COLUMN countersign_new_legal_entity_id       uuid,
    ADD CONSTRAINT fk_personnel_commands_target_legal_entity_id FOREIGN KEY (target_legal_entity_id) REFERENCES organization_workforce.legal_entities (id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_personnel_commands_countersign_previous_legal_entity_id FOREIGN KEY (countersign_previous_legal_entity_id) REFERENCES organization_workforce.legal_entities (id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_personnel_commands_countersign_new_legal_entity_id FOREIGN KEY (countersign_new_legal_entity_id) REFERENCES organization_workforce.legal_entities (id) ON DELETE RESTRICT;
CREATE INDEX ix_personnel_commands_target_legal_entity_id ON organization_workforce.personnel_commands (target_legal_entity_id) WHERE target_legal_entity_id IS NOT NULL;
CREATE INDEX ix_personnel_commands_countersign_previous_legal_entity_id ON organization_workforce.personnel_commands (countersign_previous_legal_entity_id) WHERE countersign_previous_legal_entity_id IS NOT NULL;
CREATE INDEX ix_personnel_commands_countersign_new_legal_entity_id ON organization_workforce.personnel_commands (countersign_new_legal_entity_id) WHERE countersign_new_legal_entity_id IS NOT NULL;

ALTER TABLE organization_workforce.personnel_commands DROP CONSTRAINT ck_personnel_commands_command_kind;
ALTER TABLE organization_workforce.personnel_commands ADD CONSTRAINT ck_personnel_commands_command_kind CHECK (command_kind IN
    ('HIRE', 'REGULARIZE', 'TRANSFER', 'SEPARATE', 'REINSTATE', 'CORRECTION', 'UNPAID_LEAVE_START', 'UNPAID_LEAVE_END', 'CHANGE_LEGAL_ENTITY'));
ALTER TABLE organization_workforce.personnel_commands DROP CONSTRAINT ck_personnel_commands_status;
ALTER TABLE organization_workforce.personnel_commands ADD CONSTRAINT ck_personnel_commands_status CHECK (status IN
    ('PENDING', 'HELD', 'EFFECTIVE', 'WITHDRAWN', 'AWAITING_ATTRIBUTION_COUNTERSIGN'));
ALTER TABLE organization_workforce.personnel_commands ADD CONSTRAINT ck_personnel_commands_legal_entity_columns CHECK (
    (command_kind = 'CHANGE_LEGAL_ENTITY' AND target_legal_entity_id IS NOT NULL
        AND num_nonnulls(target_org_unit_id, target_position_id, target_reports_to_employee_id, is_target_fee_earner, target_employment_phase_kind) = 0)
    OR (command_kind <> 'CHANGE_LEGAL_ENTITY' AND (target_legal_entity_id IS NULL OR command_kind IN ('TRANSFER', 'CORRECTION', 'HIRE', 'REINSTATE'))));
ALTER TABLE organization_workforce.personnel_commands ADD CONSTRAINT ck_personnel_commands_countersign_basis CHECK (
    (countersign_previous_legal_entity_id IS NULL) = (countersign_new_legal_entity_id IS NULL)
    AND (countersign_previous_legal_entity_id IS NULL OR countersign_previous_legal_entity_id <> countersign_new_legal_entity_id)
    AND (status <> 'AWAITING_ATTRIBUTION_COUNTERSIGN' OR countersign_new_legal_entity_id IS NOT NULL));
ALTER TABLE organization_workforce.personnel_commands DROP CONSTRAINT ck_personnel_commands_correction_immediate;
ALTER TABLE organization_workforce.personnel_commands ADD CONSTRAINT ck_personnel_commands_correction_immediate CHECK (
    command_kind <> 'CORRECTION' OR status = 'EFFECTIVE'
    OR (countersign_new_legal_entity_id IS NOT NULL AND status IN ('AWAITING_ATTRIBUTION_COUNTERSIGN', 'WITHDRAWN')));
DROP INDEX organization_workforce.uk_personnel_commands_open_per_day;
CREATE UNIQUE INDEX uk_personnel_commands_open_per_day ON organization_workforce.personnel_commands (employee_id, effective_date)
    WHERE status IN ('PENDING', 'HELD', 'AWAITING_ATTRIBUTION_COUNTERSIGN');
ALTER TABLE organization_workforce.personnel_commands ADD CONSTRAINT uk_personnel_commands_id_employee_drafter UNIQUE (personnel_command_id, employee_id, created_by);

COMMENT ON COLUMN organization_workforce.personnel_commands.command_kind IS '命令种类：HIRE 入职 / REGULARIZE 转正 / TRANSFER 调岗 / SEPARATE 离职 / REINSTATE 复职 / CORRECTION 更正 / UNPAID_LEAVE_START 停薪留职开始 / UNPAID_LEAVE_END 停薪留职结束 / CHANGE_LEGAL_ENTITY 变更法人归属';
COMMENT ON COLUMN organization_workforce.personnel_commands.status IS '状态：PENDING 待生效 / HELD 待放行（仅离职）/ EFFECTIVE 已生效 / WITHDRAWN 已撤回 / AWAITING_ATTRIBUTION_COUNTERSIGN 待法人归属会签（只在建立时写入，会签后转为待生效、已生效或已撤回）';
COMMENT ON COLUMN organization_workforce.personnel_commands.target_legal_entity_id IS '目标法人 ID：变更法人归属必填，调岗、更正、入职、复职可选（首次归属，不需会签）；为空表示不指定法人';
COMMENT ON COLUMN organization_workforce.personnel_commands.countersign_previous_legal_entity_id IS '会签依据的原法人 ID：命令进入待法人归属会签时写入，之后不变；为空表示命令不需会签';
COMMENT ON COLUMN organization_workforce.personnel_commands.countersign_new_legal_entity_id IS '会签依据的新法人 ID：命令进入待法人归属会签时写入，之后不变，与原法人不同；为空表示命令不需会签';

-- 只追加的触发器：加待法人归属会签到待生效、已生效、已撤回的转换；转入待法人归属会签只在建立时（插入）。
CREATE OR REPLACE FUNCTION organization_workforce.refuse_personnel_command_rewrite() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
DECLARE
    owner_name name;
    column_name text;
BEGIN
    IF TG_OP = 'TRUNCATE' THEN
        RAISE EXCEPTION 'personnel commands are append-only: truncate on %.% is refused', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
    END IF;
    IF TG_OP = 'DELETE' THEN
        SELECT pg_get_userbyid(relowner) INTO owner_name FROM pg_class WHERE oid = TG_RELID;
        IF current_user <> owner_name THEN
            RAISE EXCEPTION 'personnel commands are append-only: delete on %.% is refused', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
        END IF;
        RETURN OLD;
    END IF;
    IF NEW.status <> OLD.status AND NOT ((OLD.status = 'PENDING' AND NEW.status IN ('EFFECTIVE', 'WITHDRAWN', 'HELD'))
            OR (OLD.status = 'HELD' AND NEW.status IN ('EFFECTIVE', 'WITHDRAWN', 'PENDING'))
            OR (OLD.status = 'AWAITING_ATTRIBUTION_COUNTERSIGN' AND NEW.status IN ('PENDING', 'EFFECTIVE', 'WITHDRAWN'))) THEN
        RAISE EXCEPTION 'personnel command status cannot move from % to %', OLD.status, NEW.status USING ERRCODE = 'restrict_violation';
    END IF;
    FOREACH column_name IN ARRAY ARRAY['applied_at', 'withdrawn_at', 'withdrawn_by', 'withdrawal_reason'] LOOP
        IF (to_jsonb(OLD) -> column_name) <> 'null'::jsonb AND (to_jsonb(NEW) -> column_name) IS DISTINCT FROM (to_jsonb(OLD) -> column_name) THEN
            RAISE EXCEPTION 'column % of %.% is set once and cannot change', column_name, TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
        END IF;
    END LOOP;
    IF (to_jsonb(NEW) - 'status' - 'applied_at' - 'withdrawn_at' - 'withdrawn_by' - 'withdrawal_reason' - 'updated_at' - 'version')
            <> (to_jsonb(OLD) - 'status' - 'applied_at' - 'withdrawn_at' - 'withdrawn_by' - 'withdrawal_reason' - 'updated_at' - 'version') THEN
        RAISE EXCEPTION 'only the status and timestamp columns of %.% can change', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END $$;
COMMENT ON FUNCTION organization_workforce.refuse_personnel_command_rewrite() IS '人事命令只追加的触发器：状态按 PENDING 到 EFFECTIVE、WITHDRAWN、HELD，HELD 到 EFFECTIVE、WITHDRAWN、PENDING，待法人归属会签到 PENDING、EFFECTIVE、WITHDRAWN 前进（转入待法人归属会签只在建立时），时刻列只放行由空变非空一次，内容列不变，DELETE 只放行表属主，TRUNCATE 拒绝';

-- 2. 法人归属会签表：每个会签请求（按命令、请求种类、法人）一行。
CREATE TABLE organization_workforce.attribution_countersigns (
    attribution_countersign_id uuid        NOT NULL,
    personnel_command_id       uuid        NOT NULL,
    employee_id                uuid        NOT NULL,
    drafted_by                 uuid        NOT NULL,
    request_kind               text        NOT NULL,
    request_sequence_number    integer     NOT NULL,
    legal_entity_id            uuid        NOT NULL,
    requested_at               timestamptz NOT NULL,
    requested_by               uuid        NOT NULL,
    due_on_date                date        NOT NULL,
    decision_kind              text,
    decided_at                 timestamptz,
    decided_by                 uuid,
    decision_reason            text,
    decision_audit_entry_id    uuid,
    escalation_level_number    integer     NOT NULL DEFAULT 0,
    created_at                 timestamptz NOT NULL,
    updated_at                 timestamptz NOT NULL,
    version                    bigint      NOT NULL DEFAULT 0,
    CONSTRAINT pk_attribution_countersigns PRIMARY KEY (attribution_countersign_id),
    CONSTRAINT uk_attribution_countersigns_request UNIQUE (personnel_command_id, request_kind, legal_entity_id, request_sequence_number),
    CONSTRAINT fk_attribution_countersigns_command FOREIGN KEY (personnel_command_id, employee_id, drafted_by)
        REFERENCES organization_workforce.personnel_commands (personnel_command_id, employee_id, created_by) ON DELETE RESTRICT,
    CONSTRAINT fk_attribution_countersigns_legal_entity_id FOREIGN KEY (legal_entity_id) REFERENCES organization_workforce.legal_entities (id) ON DELETE RESTRICT,
    CONSTRAINT ck_attribution_countersigns_request_kind CHECK (request_kind IN ('CHANGE', 'WITHDRAWAL')),
    CONSTRAINT ck_attribution_countersigns_decision_kind CHECK (decision_kind IS NULL OR decision_kind IN ('APPROVED', 'REJECTED', 'LAPSED')),
    CONSTRAINT ck_attribution_countersigns_escalation_level_number CHECK (escalation_level_number BETWEEN 0 AND 3),
    CONSTRAINT ck_attribution_countersigns_request_sequence_number CHECK (request_sequence_number >= 1),
    CONSTRAINT ck_attribution_countersigns_due_on_date_finite CHECK (isfinite(due_on_date)),
    CONSTRAINT ck_attribution_countersigns_not_drafter CHECK (decided_by IS DISTINCT FROM drafted_by AND decided_by IS DISTINCT FROM requested_by),
    CONSTRAINT ck_attribution_countersigns_change_requester CHECK (request_kind <> 'CHANGE' OR (requested_by = drafted_by AND request_sequence_number = 1)),
    CONSTRAINT ck_attribution_countersigns_decision CHECK ((decision_kind IS NULL) = (decided_at IS NULL) AND (decided_at IS NULL) = (decision_audit_entry_id IS NULL)
        AND (decided_by IS NULL) = (decision_kind IS NULL OR decision_kind = 'LAPSED')),
    CONSTRAINT ck_attribution_countersigns_reason CHECK ((decision_kind IS DISTINCT FROM 'REJECTED' OR decision_reason IS NOT NULL)
        AND (decision_reason IS NULL OR (decision_kind IS NOT NULL AND char_length(decision_reason) BETWEEN 1 AND 500 AND btrim(decision_reason) <> '')))
);
CREATE UNIQUE INDEX uk_attribution_countersigns_open ON organization_workforce.attribution_countersigns (personnel_command_id, request_kind, legal_entity_id) WHERE decision_kind IS NULL;
CREATE INDEX ix_attribution_countersigns_open_due ON organization_workforce.attribution_countersigns (due_on_date) WHERE decision_kind IS NULL;
CREATE INDEX ix_attribution_countersigns_legal_entity_id ON organization_workforce.attribution_countersigns (legal_entity_id);

COMMENT ON TABLE organization_workforce.attribution_countersigns IS '法人归属会签：法人归属变更或撤回需要相关法人的财务负责人会签，每个会签请求（命令、请求种类、法人各一）一行，只追加：决定一次写定，升级级别只升不降。粒度：每个命令每种请求每个法人每次请求一行';
COMMENT ON COLUMN organization_workforce.attribution_countersigns.attribution_countersign_id IS '法人归属会签 ID（UUIDv7，由应用层生成）';
COMMENT ON COLUMN organization_workforce.attribution_countersigns.personnel_command_id IS '人事命令 ID：被会签的法人变更命令';
COMMENT ON COLUMN organization_workforce.attribution_countersigns.employee_id IS '员工 ID：命令所涉员工，与命令一致';
COMMENT ON COLUMN organization_workforce.attribution_countersigns.drafted_by IS '起草人（用户账号 ID）：命令的录入人，由数据库按命令校验，不由应用层抄写';
COMMENT ON COLUMN organization_workforce.attribution_countersigns.request_kind IS '请求种类：CHANGE 变更法人归属 / WITHDRAWAL 撤回已会签的变更';
COMMENT ON COLUMN organization_workforce.attribution_countersigns.request_sequence_number IS '请求序号：变更恒为 1，撤回被拒后可再次请求，依次为 1、2、……';
COMMENT ON COLUMN organization_workforce.attribution_countersigns.legal_entity_id IS '法人主体 ID：由哪个法人的财务负责人会签，原法人与新法人各一行';
COMMENT ON COLUMN organization_workforce.attribution_countersigns.requested_at IS '请求时刻';
COMMENT ON COLUMN organization_workforce.attribution_countersigns.requested_by IS '请求人（用户账号 ID，跨 schema 只存 ID）：变更为起草人，撤回为撤回人';
COMMENT ON COLUMN organization_workforce.attribution_countersigns.due_on_date IS '会签到期日：自请求起 3 个工作日（按工作日历）';
COMMENT ON COLUMN organization_workforce.attribution_countersigns.decision_kind IS '决定：APPROVED 批准 / REJECTED 拒绝 / LAPSED 失效；为空表示仍待会签';
COMMENT ON COLUMN organization_workforce.attribution_countersigns.decided_at IS '决定时刻；为空表示仍待会签';
COMMENT ON COLUMN organization_workforce.attribution_countersigns.decided_by IS '决定人（用户账号 ID，跨 schema 只存 ID）：不得是起草人或请求人；为空表示仍待会签或已失效（失效不由人决定）';
COMMENT ON COLUMN organization_workforce.attribution_countersigns.decision_reason IS '决定理由（1 至 500 字）：拒绝必填，批准可填；为空表示未填或仍待会签';
COMMENT ON COLUMN organization_workforce.attribution_countersigns.decision_audit_entry_id IS '决定审计记录 ID（跨模块，只存 ID、不建外键）；为空表示仍待会签';
COMMENT ON COLUMN organization_workforce.attribution_countersigns.escalation_level_number IS '升级级别：0 未升级 / 1 提醒 / 2 二级 / 3 三级，只升不降';
COMMENT ON COLUMN organization_workforce.attribution_countersigns.created_at IS '创建时间';
COMMENT ON COLUMN organization_workforce.attribution_countersigns.updated_at IS '更新时间';
COMMENT ON COLUMN organization_workforce.attribution_countersigns.version IS '乐观锁版本';

-- 只追加：决定各列只能由空变非空一次，升级级别只升不降且已决定后不再变，其余列除更新时间与版本外不变；DELETE 只放行表属主（10 年清除），TRUNCATE 拒绝。
CREATE FUNCTION organization_workforce.refuse_attribution_countersign_rewrite() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
DECLARE
    owner_name name;
    column_name text;
BEGIN
    IF TG_OP = 'TRUNCATE' THEN
        RAISE EXCEPTION 'attribution countersigns are append-only: truncate on %.% is refused', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
    END IF;
    IF TG_OP = 'DELETE' THEN
        SELECT pg_get_userbyid(relowner) INTO owner_name FROM pg_class WHERE oid = TG_RELID;
        IF current_user <> owner_name THEN
            RAISE EXCEPTION 'attribution countersigns are append-only: delete on %.% is refused', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
        END IF;
        RETURN OLD;
    END IF;
    FOREACH column_name IN ARRAY ARRAY['decision_kind', 'decided_at', 'decided_by', 'decision_reason', 'decision_audit_entry_id'] LOOP
        IF (to_jsonb(OLD) -> column_name) <> 'null'::jsonb AND (to_jsonb(NEW) -> column_name) IS DISTINCT FROM (to_jsonb(OLD) -> column_name) THEN
            RAISE EXCEPTION 'column % of %.% is set once and cannot change', column_name, TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
        END IF;
    END LOOP;
    IF NEW.escalation_level_number < OLD.escalation_level_number OR (OLD.decision_kind IS NOT NULL AND NEW.escalation_level_number <> OLD.escalation_level_number) THEN
        RAISE EXCEPTION 'escalation_level_number of %.% can only increase while the request is open', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
    END IF;
    IF (to_jsonb(NEW) - 'decision_kind' - 'decided_at' - 'decided_by' - 'decision_reason' - 'decision_audit_entry_id' - 'escalation_level_number' - 'updated_at' - 'version')
            <> (to_jsonb(OLD) - 'decision_kind' - 'decided_at' - 'decided_by' - 'decision_reason' - 'decision_audit_entry_id' - 'escalation_level_number' - 'updated_at' - 'version') THEN
        RAISE EXCEPTION 'only the decision columns and the escalation level of %.% can change', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END $$;
COMMENT ON FUNCTION organization_workforce.refuse_attribution_countersign_rewrite() IS '法人归属会签只追加的触发器：决定各列只放行由空变非空一次，升级级别只升不降且决定后不再变，其余列不变，DELETE 只放行表属主，TRUNCATE 拒绝';
CREATE TRIGGER tg_attribution_countersigns_append_only BEFORE UPDATE OR DELETE ON organization_workforce.attribution_countersigns
    FOR EACH ROW EXECUTE FUNCTION organization_workforce.refuse_attribution_countersign_rewrite();
CREATE TRIGGER tg_attribution_countersigns_append_only_truncate BEFORE TRUNCATE ON organization_workforce.attribution_countersigns
    FOR EACH STATEMENT EXECUTE FUNCTION organization_workforce.refuse_attribution_countersign_rewrite();

-- 3. 状态转换：加法人归属会签这一类状态持有者，人事命令加待法人归属会签的转换。
ALTER TABLE organization_workforce.state_transitions DROP CONSTRAINT ck_state_transitions_aggregate_type_code;
ALTER TABLE organization_workforce.state_transitions ADD CONSTRAINT ck_state_transitions_aggregate_type_code CHECK (aggregate_type_code IN
    ('PersonnelCommand', 'SeparationHold', 'OrgStructureCommand', 'AttributionCountersign'));
ALTER TABLE organization_workforce.state_transitions DROP CONSTRAINT ck_state_transitions_from_status;
ALTER TABLE organization_workforce.state_transitions ADD CONSTRAINT ck_state_transitions_from_status CHECK (from_status IN
    ('PENDING', 'HELD', 'IN_PROGRESS', 'AWAITING_ATTRIBUTION_COUNTERSIGN', 'OPEN'));
ALTER TABLE organization_workforce.state_transitions DROP CONSTRAINT ck_state_transitions_to_status;
ALTER TABLE organization_workforce.state_transitions ADD CONSTRAINT ck_state_transitions_to_status CHECK (to_status IN
    ('PENDING', 'HELD', 'EFFECTIVE', 'WITHDRAWN', 'RELEASED', 'IN_PROGRESS', 'AWAITING_ATTRIBUTION_COUNTERSIGN', 'OPEN', 'APPROVED', 'REJECTED', 'LAPSED'));
ALTER TABLE organization_workforce.state_transitions DROP CONSTRAINT ck_state_transitions_status_of_aggregate;
ALTER TABLE organization_workforce.state_transitions ADD CONSTRAINT ck_state_transitions_status_of_aggregate CHECK (coalesce(
        (aggregate_type_code = 'PersonnelCommand'
            AND ((from_status IS NULL AND to_status IN ('PENDING', 'HELD', 'EFFECTIVE', 'AWAITING_ATTRIBUTION_COUNTERSIGN'))
                 OR (from_status = 'PENDING' AND to_status IN ('EFFECTIVE', 'WITHDRAWN', 'HELD'))
                 OR (from_status = 'HELD' AND to_status IN ('EFFECTIVE', 'WITHDRAWN', 'PENDING'))
                 OR (from_status = 'AWAITING_ATTRIBUTION_COUNTERSIGN' AND to_status IN ('PENDING', 'EFFECTIVE', 'WITHDRAWN'))))
        OR (aggregate_type_code = 'SeparationHold'
            AND ((from_status IS NULL AND to_status = 'HELD')
                 OR (from_status = 'HELD' AND to_status IN ('RELEASED', 'WITHDRAWN'))))
        OR (aggregate_type_code = 'OrgStructureCommand'
            AND ((from_status IS NULL AND to_status IN ('PENDING', 'EFFECTIVE', 'AWAITING_ATTRIBUTION_COUNTERSIGN'))
                 OR (from_status = 'PENDING' AND to_status IN ('IN_PROGRESS', 'EFFECTIVE', 'WITHDRAWN'))
                 OR (from_status = 'IN_PROGRESS' AND to_status = 'EFFECTIVE')
                 OR (from_status = 'AWAITING_ATTRIBUTION_COUNTERSIGN' AND to_status IN ('PENDING', 'EFFECTIVE', 'WITHDRAWN'))))
        OR (aggregate_type_code = 'AttributionCountersign'
            AND ((from_status IS NULL AND to_status = 'OPEN')
                 OR (from_status = 'OPEN' AND to_status IN ('APPROVED', 'REJECTED', 'LAPSED')))), false));
COMMENT ON COLUMN organization_workforce.state_transitions.aggregate_type_code IS '聚合类型代码：状态持有者的类型，有 PersonnelCommand（人事命令）、SeparationHold（待放行离职）、OrgStructureCommand（组织结构命令，组织单元或岗位聚合内带自己状态机的实体）与 AttributionCountersign（法人归属会签请求）；后续卡随各自状态机追加';
COMMENT ON COLUMN organization_workforce.state_transitions.aggregate_id IS '聚合 ID：状态持有者自己的 ID，PersonnelCommand 取 personnel_command_id，SeparationHold 取 separation_hold_id，OrgStructureCommand 取 org_structure_command_id，AttributionCountersign 取 attribution_countersign_id，无外键（多态主体）';
COMMENT ON COLUMN organization_workforce.state_transitions.from_status IS '原状态：为空表示新建或迁入的初始状态；取值为本 schema 各状态机的状态并集，合法流转见 ck_state_transitions_status_of_aggregate：PersonnelCommand 为空到待生效、待放行、已生效、待法人归属会签，待生效到已生效、已撤回、待放行，待放行到已生效、已撤回、待生效，待法人归属会签到待生效、已生效、已撤回；SeparationHold 为空到待放行，待放行到已放行、已撤回；OrgStructureCommand 为空到待生效、已生效、待会签，待生效到执行中、已生效、已撤回，执行中到已生效，待会签到待生效、已生效、已撤回；AttributionCountersign 为空到 OPEN，OPEN 到 APPROVED、REJECTED、LAPSED';
COMMENT ON COLUMN organization_workforce.state_transitions.to_status IS '新状态：PENDING 待生效 / HELD 待放行（仅离职）/ EFFECTIVE 已生效 / WITHDRAWN 已撤回 / RELEASED 已放行（仅待放行离职）/ IN_PROGRESS 执行中（仅组织结构命令的合并）/ AWAITING_ATTRIBUTION_COUNTERSIGN 待法人归属会签（人事命令与组织结构命令）/ OPEN 待会签 / APPROVED 已批准 / REJECTED 已拒绝 / LAPSED 已失效（后三项与待会签只用于法人归属会签请求）';

-- 4. 法人版本：法人财务负责人（法人归属会签的决定人依据）。
ALTER TABLE organization_workforce.legal_entity_versions
    ADD COLUMN finance_head_employee_id uuid,
    ADD CONSTRAINT fk_legal_entity_versions_finance_head_employee_id FOREIGN KEY (finance_head_employee_id) REFERENCES organization_workforce.employees (id) ON DELETE RESTRICT;
CREATE INDEX ix_legal_entity_versions_finance_head_employee_id ON organization_workforce.legal_entity_versions (finance_head_employee_id) WHERE finance_head_employee_id IS NOT NULL;
COMMENT ON COLUMN organization_workforce.legal_entity_versions.finance_head_employee_id IS '法人财务负责人（员工 ID）：为空表示该版本期间未指定，此期间该法人的法人归属会签无人可批，只能升级';
