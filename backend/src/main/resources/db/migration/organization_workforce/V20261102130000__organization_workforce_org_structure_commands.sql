-- IMP-1030-ORG-F1（#905，one-design 签发评论）：组织结构命令表（ADR-0068 第 4 条）。只建表、触发器、状态转换与对它的外键，没有应用代码。
CREATE TABLE organization_workforce.org_structure_commands (
    org_structure_command_id    uuid        NOT NULL,
    command_kind                text        NOT NULL,
    target_kind                 text        NOT NULL,
    target_org_unit_id          uuid,
    target_position_id          uuid,
    effective_date              date        NOT NULL,
    status                      text        NOT NULL,
    target_code                 text,
    target_name                 text,
    target_short_name           text,
    target_region_kind          text,
    target_is_delivery_unit     boolean,
    target_parent_org_unit_id   uuid,
    target_head_employee_id     uuid,
    target_receivables_manager_employee_id uuid,
    merge_into_org_unit_id      uuid,
    target_legal_entity_id      uuid,
    affected_employee_count     integer,
    transferred_employee_count  integer,
    applied_at                  timestamptz,
    withdrawn_at                timestamptz,
    withdrawn_by                uuid,
    withdrawal_reason           text,
    created_at                  timestamptz NOT NULL,
    created_by                  uuid        NOT NULL,
    updated_at                  timestamptz NOT NULL,
    version                     bigint      NOT NULL DEFAULT 0,
    CONSTRAINT pk_org_structure_commands PRIMARY KEY (org_structure_command_id),
    CONSTRAINT uk_org_structure_commands_id_drafter UNIQUE (org_structure_command_id, created_by),
    CONSTRAINT fk_org_structure_commands_target_org_unit_id FOREIGN KEY (target_org_unit_id) REFERENCES organization_workforce.org_units (id) ON DELETE RESTRICT,
    CONSTRAINT fk_org_structure_commands_target_position_id FOREIGN KEY (target_position_id) REFERENCES organization_workforce.positions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_org_structure_commands_parent FOREIGN KEY (target_parent_org_unit_id) REFERENCES organization_workforce.org_units (id) ON DELETE RESTRICT,
    CONSTRAINT fk_org_structure_commands_head FOREIGN KEY (target_head_employee_id) REFERENCES organization_workforce.employees (id) ON DELETE RESTRICT,
    CONSTRAINT fk_org_structure_commands_receivables_manager FOREIGN KEY (target_receivables_manager_employee_id) REFERENCES organization_workforce.employees (id) ON DELETE RESTRICT,
    CONSTRAINT fk_org_structure_commands_merge_into FOREIGN KEY (merge_into_org_unit_id) REFERENCES organization_workforce.org_units (id) ON DELETE RESTRICT,
    CONSTRAINT fk_org_structure_commands_target_legal_entity_id FOREIGN KEY (target_legal_entity_id) REFERENCES organization_workforce.legal_entities (id) ON DELETE RESTRICT,
    CONSTRAINT ck_org_structure_commands_target_kind CHECK (target_kind IN ('ORG_UNIT', 'POSITION')),
    CONSTRAINT ck_org_structure_commands_target CHECK (num_nonnulls(target_org_unit_id, target_position_id) = 1 AND (target_kind = 'ORG_UNIT') = (target_org_unit_id IS NOT NULL)),
    CONSTRAINT ck_org_structure_commands_command_kind CHECK (
        (target_kind = 'ORG_UNIT' AND command_kind IN ('CREATE', 'DISSOLVE', 'SET_ATTRIBUTES', 'MERGE', 'REPARENT', 'ASSIGN_HEAD', 'ASSIGN_RECEIVABLES_MANAGER', 'ASSIGN_LEGAL_ENTITY'))
        OR (target_kind = 'POSITION' AND command_kind IN ('CREATE', 'DISABLE'))),
    CONSTRAINT ck_org_structure_commands_status CHECK (status IN ('PENDING', 'IN_PROGRESS', 'EFFECTIVE', 'WITHDRAWN', 'AWAITING_ATTRIBUTION_COUNTERSIGN')),
    CONSTRAINT ck_org_structure_commands_in_progress_kind CHECK (status <> 'IN_PROGRESS' OR command_kind = 'MERGE'),
    CONSTRAINT ck_org_structure_commands_applied CHECK ((status = 'EFFECTIVE') = (applied_at IS NOT NULL)),
    CONSTRAINT ck_org_structure_commands_withdrawn CHECK ((status = 'WITHDRAWN') = (withdrawn_at IS NOT NULL) AND (withdrawn_at IS NULL) = (withdrawn_by IS NULL) AND (withdrawn_at IS NULL) = (withdrawal_reason IS NULL)),
    CONSTRAINT ck_org_structure_commands_withdrawal_reason CHECK (withdrawal_reason IS NULL OR (char_length(withdrawal_reason) BETWEEN 1 AND 500 AND btrim(withdrawal_reason) <> '')),
    CONSTRAINT ck_org_structure_commands_effective_date_finite CHECK (isfinite(effective_date)),
    CONSTRAINT ck_org_structure_commands_name CHECK (
        (target_name IS NULL OR (char_length(target_name) BETWEEN 1 AND 500 AND btrim(target_name) <> ''))
        AND (target_short_name IS NULL OR (char_length(target_short_name) BETWEEN 1 AND 50 AND btrim(target_short_name) <> ''))),
    CONSTRAINT ck_org_structure_commands_code CHECK (target_code IS NULL OR (char_length(target_code) BETWEEN 1 AND 500 AND btrim(target_code) <> '')),
    CONSTRAINT ck_org_structure_commands_region_kind CHECK (target_region_kind IS NULL OR target_region_kind IN ('HEADQUARTERS', 'REGIONAL')),
    CONSTRAINT ck_org_structure_commands_create_only_columns CHECK ((command_kind = 'CREATE' OR target_code IS NULL)
        AND (command_kind IN ('CREATE', 'SET_ATTRIBUTES') OR num_nonnulls(target_region_kind, target_is_delivery_unit) = 0)),
    CONSTRAINT ck_org_structure_commands_unit_attributes CHECK (command_kind <> 'CREATE'
        OR ((target_kind = 'ORG_UNIT') = (target_is_delivery_unit IS NOT NULL) AND (target_kind = 'ORG_UNIT' OR target_region_kind IS NULL))),
    CONSTRAINT ck_org_structure_commands_create_columns CHECK (command_kind <> 'CREATE'
        OR (target_name IS NOT NULL AND target_head_employee_id IS NULL AND target_receivables_manager_employee_id IS NULL AND merge_into_org_unit_id IS NULL
            AND (target_kind = 'ORG_UNIT' OR (target_short_name IS NULL AND target_parent_org_unit_id IS NULL)))),
    CONSTRAINT ck_org_structure_commands_set_attributes_columns CHECK (command_kind <> 'SET_ATTRIBUTES'
        OR (target_kind = 'ORG_UNIT' AND num_nonnulls(target_name, target_short_name, target_region_kind, target_is_delivery_unit) >= 1
            AND num_nonnulls(target_parent_org_unit_id, target_head_employee_id, target_receivables_manager_employee_id, merge_into_org_unit_id, target_legal_entity_id) = 0)),
    CONSTRAINT ck_org_structure_commands_reparent_columns CHECK (command_kind <> 'REPARENT'
        OR (target_parent_org_unit_id IS NOT NULL AND num_nonnulls(target_name, target_short_name, target_head_employee_id, target_receivables_manager_employee_id, merge_into_org_unit_id) = 0)),
    CONSTRAINT ck_org_structure_commands_head_columns CHECK (command_kind <> 'ASSIGN_HEAD'
        OR (target_head_employee_id IS NOT NULL AND num_nonnulls(target_name, target_short_name, target_parent_org_unit_id, target_receivables_manager_employee_id, merge_into_org_unit_id) = 0)),
    CONSTRAINT ck_org_structure_commands_receivables_columns CHECK (command_kind <> 'ASSIGN_RECEIVABLES_MANAGER'
        OR (target_receivables_manager_employee_id IS NOT NULL AND num_nonnulls(target_name, target_short_name, target_parent_org_unit_id, target_head_employee_id, merge_into_org_unit_id) = 0)),
    CONSTRAINT ck_org_structure_commands_contentless_columns CHECK (command_kind NOT IN ('DISSOLVE', 'DISABLE')
        OR num_nonnulls(target_name, target_short_name, target_parent_org_unit_id, target_head_employee_id, target_receivables_manager_employee_id, merge_into_org_unit_id) = 0),
    CONSTRAINT ck_org_structure_commands_merge_columns CHECK ((command_kind = 'MERGE') = (merge_into_org_unit_id IS NOT NULL)
        AND (command_kind <> 'MERGE' OR num_nonnulls(target_name, target_short_name, target_parent_org_unit_id, target_head_employee_id, target_receivables_manager_employee_id) = 0)),
    CONSTRAINT ck_org_structure_commands_legal_entity_columns CHECK (
        (command_kind = 'ASSIGN_LEGAL_ENTITY' AND target_kind = 'ORG_UNIT' AND target_legal_entity_id IS NOT NULL
            AND num_nonnulls(target_code, target_name, target_short_name, target_region_kind, target_is_delivery_unit, target_parent_org_unit_id,
                             target_head_employee_id, target_receivables_manager_employee_id, merge_into_org_unit_id) = 0)
        OR (command_kind <> 'ASSIGN_LEGAL_ENTITY' AND (target_legal_entity_id IS NULL OR (target_kind = 'ORG_UNIT' AND command_kind = 'CREATE')))),
    CONSTRAINT ck_org_structure_commands_merge_not_self CHECK (merge_into_org_unit_id IS NULL OR merge_into_org_unit_id <> target_org_unit_id),
    CONSTRAINT ck_org_structure_commands_parent_not_self CHECK (target_parent_org_unit_id IS NULL OR target_parent_org_unit_id <> target_org_unit_id),
    CONSTRAINT ck_org_structure_commands_merge_progress CHECK (
        (affected_employee_count IS NOT NULL) = (command_kind = 'MERGE' AND status IN ('IN_PROGRESS', 'EFFECTIVE'))
        AND (affected_employee_count IS NULL) = (transferred_employee_count IS NULL)
        AND (affected_employee_count IS NULL OR (0 <= transferred_employee_count AND transferred_employee_count <= affected_employee_count))
        AND (status <> 'EFFECTIVE' OR transferred_employee_count IS NOT DISTINCT FROM affected_employee_count))
);
CREATE UNIQUE INDEX uk_org_structure_commands_open_org_unit_per_day
    ON organization_workforce.org_structure_commands (target_org_unit_id, effective_date)
    WHERE status IN ('PENDING', 'IN_PROGRESS', 'AWAITING_ATTRIBUTION_COUNTERSIGN') AND target_org_unit_id IS NOT NULL;
CREATE UNIQUE INDEX uk_org_structure_commands_open_position_per_day
    ON organization_workforce.org_structure_commands (target_position_id, effective_date) WHERE status = 'PENDING' AND target_position_id IS NOT NULL;
CREATE UNIQUE INDEX uk_org_structure_commands_merge_in_progress
    ON organization_workforce.org_structure_commands (target_org_unit_id) WHERE status = 'IN_PROGRESS';
CREATE INDEX ix_org_structure_commands_due ON organization_workforce.org_structure_commands (effective_date) WHERE status = 'PENDING';
CREATE INDEX ix_org_structure_commands_target_org_unit_id ON organization_workforce.org_structure_commands (target_org_unit_id) WHERE target_org_unit_id IS NOT NULL;
CREATE INDEX ix_org_structure_commands_target_position_id ON organization_workforce.org_structure_commands (target_position_id) WHERE target_position_id IS NOT NULL;
CREATE INDEX ix_org_structure_commands_parent ON organization_workforce.org_structure_commands (target_parent_org_unit_id) WHERE target_parent_org_unit_id IS NOT NULL;
CREATE INDEX ix_org_structure_commands_head ON organization_workforce.org_structure_commands (target_head_employee_id) WHERE target_head_employee_id IS NOT NULL;
CREATE INDEX ix_org_structure_commands_receivables_manager ON organization_workforce.org_structure_commands (target_receivables_manager_employee_id) WHERE target_receivables_manager_employee_id IS NOT NULL;
CREATE INDEX ix_org_structure_commands_merge_into ON organization_workforce.org_structure_commands (merge_into_org_unit_id) WHERE merge_into_org_unit_id IS NOT NULL;
CREATE INDEX ix_org_structure_commands_target_legal_entity_id ON organization_workforce.org_structure_commands (target_legal_entity_id) WHERE target_legal_entity_id IS NOT NULL;

COMMENT ON TABLE organization_workforce.org_structure_commands IS '组织结构命令：组织单元的新建、撤销、改属性、合并、调整上级、指定负责人、区域应收负责人与法人，以及岗位新建、停用的第一手记录，只追加（ADR-0068 第 4 条）。粒度：每条命令一行';
COMMENT ON COLUMN organization_workforce.org_structure_commands.org_structure_command_id IS '组织结构命令 ID（UUIDv7，由应用层生成）';
COMMENT ON COLUMN organization_workforce.org_structure_commands.command_kind IS '命令种类：组织单元 CREATE 新建 / DISSOLVE 撤销 / SET_ATTRIBUTES 改名称、简称、总部区域或是否交付单元 / MERGE 合并 / REPARENT 调整上级 / ASSIGN_HEAD 指定负责人 / ASSIGN_RECEIVABLES_MANAGER 指定区域应收负责人 / ASSIGN_LEGAL_ENTITY 指定法人；岗位 CREATE 新建 / DISABLE 停用';
COMMENT ON COLUMN organization_workforce.org_structure_commands.target_kind IS '目标种类：ORG_UNIT 组织单元 / POSITION 岗位';
COMMENT ON COLUMN organization_workforce.org_structure_commands.target_org_unit_id IS '目标组织单元 ID；合并时是被并入方；为空表示目标是岗位';
COMMENT ON COLUMN organization_workforce.org_structure_commands.target_position_id IS '目标岗位 ID；为空表示目标是组织单元';
COMMENT ON COLUMN organization_workforce.org_structure_commands.effective_date IS '生效日：组织业务生效日，即新状态的第一天';
COMMENT ON COLUMN organization_workforce.org_structure_commands.status IS '状态：PENDING 待生效 / IN_PROGRESS 执行中（仅合并，开始转人后不可撤回）/ EFFECTIVE 已生效 / WITHDRAWN 已撤回 / AWAITING_ATTRIBUTION_COUNTERSIGN 待会签（仅新建时写入，不会在插入后转入）';
COMMENT ON COLUMN organization_workforce.org_structure_commands.target_code IS '目标编码：只在新建命令写入，发号后不变；为空表示不是新建或没有编码';
COMMENT ON COLUMN organization_workforce.org_structure_commands.target_name IS '目标名称（1 至 500 字）：新建必填，改属性可选；为空表示不改名称';
COMMENT ON COLUMN organization_workforce.org_structure_commands.target_short_name IS '目标简称（1 至 50 字）：组织单元新建或改属性时写入；为空表示不设或不改简称';
COMMENT ON COLUMN organization_workforce.org_structure_commands.target_region_kind IS '目标总部区域：HEADQUARTERS 总部 / REGIONAL 区域；组织单元新建或改属性时写入；为空表示不设或不改';
COMMENT ON COLUMN organization_workforce.org_structure_commands.target_is_delivery_unit IS '目标是否交付单元：组织单元新建必填，改属性可选；为空表示不改或目标是岗位';
COMMENT ON COLUMN organization_workforce.org_structure_commands.target_parent_org_unit_id IS '目标上级组织单元 ID：调整上级必填，组织单元新建可选；为空表示不改上级，新建时为空表示根';
COMMENT ON COLUMN organization_workforce.org_structure_commands.target_head_employee_id IS '目标负责人（员工 ID）：指定负责人必填；为空表示不是指定负责人';
COMMENT ON COLUMN organization_workforce.org_structure_commands.target_receivables_manager_employee_id IS '目标区域应收负责人（员工 ID）：指定区域应收负责人必填；为空表示不是该种类';
COMMENT ON COLUMN organization_workforce.org_structure_commands.merge_into_org_unit_id IS '合并承接方组织单元 ID：合并必填；为空表示不是合并';
COMMENT ON COLUMN organization_workforce.org_structure_commands.target_legal_entity_id IS '目标法人 ID：指定法人必填，组织单元新建可选；为空表示不指定法人';
COMMENT ON COLUMN organization_workforce.org_structure_commands.affected_employee_count IS '合并涉及的在职员工数：执行开始时按当时名单写入一次；为空表示合并尚未开始或不是合并';
COMMENT ON COLUMN organization_workforce.org_structure_commands.transferred_employee_count IS '合并已转出的员工数：只增不减，已生效时等于涉及人数；为空表示合并尚未开始或不是合并';
COMMENT ON COLUMN organization_workforce.org_structure_commands.applied_at IS '生效落地时刻；有值当且仅当状态为已生效';
COMMENT ON COLUMN organization_workforce.org_structure_commands.withdrawn_at IS '撤回时刻；为空表示未被撤回';
COMMENT ON COLUMN organization_workforce.org_structure_commands.withdrawn_by IS '撤回人（用户账号 ID，跨 schema 只存 ID）；为空表示未被撤回';
COMMENT ON COLUMN organization_workforce.org_structure_commands.withdrawal_reason IS '撤回原因（1 至 500 字）；为空表示未被撤回';
COMMENT ON COLUMN organization_workforce.org_structure_commands.created_at IS '创建时间';
COMMENT ON COLUMN organization_workforce.org_structure_commands.created_by IS '录入人（用户账号 ID，跨 schema 只存 ID）';
COMMENT ON COLUMN organization_workforce.org_structure_commands.updated_at IS '更新时间';
COMMENT ON COLUMN organization_workforce.org_structure_commands.version IS '乐观锁版本';

-- 只追加：状态按 PENDING 到 IN_PROGRESS、EFFECTIVE、WITHDRAWN，IN_PROGRESS 到 EFFECTIVE，AWAITING_ATTRIBUTION_COUNTERSIGN 到 PENDING、EFFECTIVE、WITHDRAWN 前进；
-- 时刻列、撤回三列与涉及人数各只能由空变非空一次，已转人数只增不减，其余列除更新时间与版本外不变；DELETE 只放行表属主（经清除函数），TRUNCATE 拒绝。
CREATE FUNCTION organization_workforce.refuse_org_structure_command_rewrite() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
DECLARE
    owner_name name;
    column_name text;
BEGIN
    IF TG_OP = 'TRUNCATE' THEN
        RAISE EXCEPTION 'org structure commands are append-only: truncate on %.% is refused', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
    END IF;
    IF TG_OP = 'DELETE' THEN
        SELECT pg_get_userbyid(relowner) INTO owner_name FROM pg_class WHERE oid = TG_RELID;
        IF current_user <> owner_name THEN
            RAISE EXCEPTION 'org structure commands are append-only: delete on %.% is refused', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
        END IF;
        RETURN OLD;
    END IF;
    IF NEW.status <> OLD.status AND NOT ((OLD.status = 'PENDING' AND NEW.status IN ('IN_PROGRESS', 'EFFECTIVE', 'WITHDRAWN'))
            OR (OLD.status = 'IN_PROGRESS' AND NEW.status = 'EFFECTIVE')
            OR (OLD.status = 'AWAITING_ATTRIBUTION_COUNTERSIGN' AND NEW.status IN ('PENDING', 'EFFECTIVE', 'WITHDRAWN'))) THEN
        RAISE EXCEPTION 'org structure command status cannot move from % to %', OLD.status, NEW.status USING ERRCODE = 'restrict_violation';
    END IF;
    FOREACH column_name IN ARRAY ARRAY['applied_at', 'withdrawn_at', 'withdrawn_by', 'withdrawal_reason', 'affected_employee_count'] LOOP
        IF (to_jsonb(OLD) -> column_name) <> 'null'::jsonb AND (to_jsonb(NEW) -> column_name) IS DISTINCT FROM (to_jsonb(OLD) -> column_name) THEN
            RAISE EXCEPTION 'column % of %.% is set once and cannot change', column_name, TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
        END IF;
    END LOOP;
    IF OLD.transferred_employee_count IS NOT NULL AND (NEW.transferred_employee_count IS NULL OR NEW.transferred_employee_count < OLD.transferred_employee_count) THEN
        RAISE EXCEPTION 'transferred_employee_count of %.% can only increase', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
    END IF;
    IF (to_jsonb(NEW) - 'status' - 'applied_at' - 'withdrawn_at' - 'withdrawn_by' - 'withdrawal_reason' - 'affected_employee_count' - 'transferred_employee_count' - 'updated_at' - 'version')
            <> (to_jsonb(OLD) - 'status' - 'applied_at' - 'withdrawn_at' - 'withdrawn_by' - 'withdrawal_reason' - 'affected_employee_count' - 'transferred_employee_count' - 'updated_at' - 'version') THEN
        RAISE EXCEPTION 'only the status, progress and timestamp columns of %.% can change', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END $$;
COMMENT ON FUNCTION organization_workforce.refuse_org_structure_command_rewrite() IS '组织结构命令只追加的触发器：状态按 PENDING 到 IN_PROGRESS、EFFECTIVE、WITHDRAWN，IN_PROGRESS 到 EFFECTIVE，待会签到 PENDING、EFFECTIVE、WITHDRAWN 前进，时刻列、撤回列与涉及人数只放行由空变非空一次，已转人数只增不减，其余列不变，DELETE 只放行表属主，TRUNCATE 拒绝';
CREATE TRIGGER tg_org_structure_commands_append_only BEFORE UPDATE OR DELETE ON organization_workforce.org_structure_commands
    FOR EACH ROW EXECUTE FUNCTION organization_workforce.refuse_org_structure_command_rewrite();
CREATE TRIGGER tg_org_structure_commands_append_only_truncate BEFORE TRUNCATE ON organization_workforce.org_structure_commands
    FOR EACH STATEMENT EXECUTE FUNCTION organization_workforce.refuse_org_structure_command_rewrite();

-- 合并产生的逐人调岗引用组织结构命令（W2 第 9 节的承诺）。
ALTER TABLE organization_workforce.personnel_commands
    ADD CONSTRAINT fk_personnel_commands_org_structure_command_id FOREIGN KEY (org_structure_command_id) REFERENCES organization_workforce.org_structure_commands (org_structure_command_id) ON DELETE RESTRICT;
COMMENT ON COLUMN organization_workforce.personnel_commands.org_structure_command_id IS '组织结构命令 ID：只用于合并产生的逐人调岗；为空表示不是合并产生的调岗';

-- 状态转换：加组织结构命令这一类状态持有者。
ALTER TABLE organization_workforce.state_transitions DROP CONSTRAINT ck_state_transitions_aggregate_type_code;
ALTER TABLE organization_workforce.state_transitions ADD CONSTRAINT ck_state_transitions_aggregate_type_code CHECK (aggregate_type_code IN ('PersonnelCommand', 'SeparationHold', 'OrgStructureCommand'));
ALTER TABLE organization_workforce.state_transitions DROP CONSTRAINT ck_state_transitions_from_status;
ALTER TABLE organization_workforce.state_transitions ADD CONSTRAINT ck_state_transitions_from_status CHECK (from_status IN ('PENDING', 'HELD', 'IN_PROGRESS', 'AWAITING_ATTRIBUTION_COUNTERSIGN'));
ALTER TABLE organization_workforce.state_transitions DROP CONSTRAINT ck_state_transitions_to_status;
ALTER TABLE organization_workforce.state_transitions ADD CONSTRAINT ck_state_transitions_to_status CHECK (to_status IN ('PENDING', 'HELD', 'EFFECTIVE', 'WITHDRAWN', 'RELEASED', 'IN_PROGRESS', 'AWAITING_ATTRIBUTION_COUNTERSIGN'));
ALTER TABLE organization_workforce.state_transitions DROP CONSTRAINT ck_state_transitions_status_of_aggregate;
ALTER TABLE organization_workforce.state_transitions ADD CONSTRAINT ck_state_transitions_status_of_aggregate CHECK (coalesce(
        (aggregate_type_code = 'PersonnelCommand'
            AND ((from_status IS NULL AND to_status IN ('PENDING', 'HELD', 'EFFECTIVE'))
                 OR (from_status = 'PENDING' AND to_status IN ('EFFECTIVE', 'WITHDRAWN', 'HELD'))
                 OR (from_status = 'HELD' AND to_status IN ('EFFECTIVE', 'WITHDRAWN', 'PENDING'))))
        OR (aggregate_type_code = 'SeparationHold'
            AND ((from_status IS NULL AND to_status = 'HELD')
                 OR (from_status = 'HELD' AND to_status IN ('RELEASED', 'WITHDRAWN'))))
        OR (aggregate_type_code = 'OrgStructureCommand'
            AND ((from_status IS NULL AND to_status IN ('PENDING', 'EFFECTIVE', 'AWAITING_ATTRIBUTION_COUNTERSIGN'))
                 OR (from_status = 'PENDING' AND to_status IN ('IN_PROGRESS', 'EFFECTIVE', 'WITHDRAWN'))
                 OR (from_status = 'IN_PROGRESS' AND to_status = 'EFFECTIVE')
                 OR (from_status = 'AWAITING_ATTRIBUTION_COUNTERSIGN' AND to_status IN ('PENDING', 'EFFECTIVE', 'WITHDRAWN')))), false));
COMMENT ON COLUMN organization_workforce.state_transitions.aggregate_type_code IS '聚合类型代码：状态持有者的类型，有 PersonnelCommand（人事命令）、SeparationHold（待放行离职）与 OrgStructureCommand（组织结构命令，组织单元或岗位聚合内带自己状态机的实体）；后续卡随各自状态机追加';
COMMENT ON COLUMN organization_workforce.state_transitions.aggregate_id IS '聚合 ID：状态持有者自己的 ID，PersonnelCommand 取 personnel_command_id，SeparationHold 取 separation_hold_id，OrgStructureCommand 取 org_structure_command_id，无外键（多态主体）';
COMMENT ON COLUMN organization_workforce.state_transitions.from_status IS '原状态：为空表示新建或迁入的初始状态；取值为本 schema 各状态机的状态并集，合法流转见 ck_state_transitions_status_of_aggregate：PersonnelCommand 为空到待生效、待放行、已生效，待生效到已生效、已撤回、待放行，待放行到已生效、已撤回、待生效（放行原生效日在将来的离职）；SeparationHold 为空到待放行，待放行到已放行、已撤回；OrgStructureCommand 为空到待生效、已生效、待会签，待生效到执行中、已生效、已撤回，执行中到已生效，待会签到待生效、已生效、已撤回';
COMMENT ON COLUMN organization_workforce.state_transitions.to_status IS '新状态：PENDING 待生效 / HELD 待放行（仅离职）/ EFFECTIVE 已生效 / WITHDRAWN 已撤回 / RELEASED 已放行（仅待放行离职）/ IN_PROGRESS 执行中（仅组织结构命令的合并）/ AWAITING_ATTRIBUTION_COUNTERSIGN 待会签（仅组织结构命令）';
