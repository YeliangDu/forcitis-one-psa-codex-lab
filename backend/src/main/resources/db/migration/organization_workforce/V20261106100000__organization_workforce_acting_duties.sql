-- #1536（代理职务切片 1：表结构；数据设计签发记录 docs/data/reviews/workforce-acting-duty.md，#1537）。
-- 只改结构：新增代理职务表；人事命令加六列与三个命令种类；同日一条的唯一索引把三个代理种类排除在外，另加两个代理专用唯一索引。
-- 没有已有行要处理（此前没有代理概念）。命令表的只追加触发器按 to_jsonb 比较整行，新列自动写一次后不再变，不用改。

-- 1. 代理职务：员工聚合内的实体，一段期限内暂代某部门或岗位的职责；除结束日、结束原因、到期日按触发器的规则外只追加。
CREATE TABLE organization_workforce.acting_duties (
    acting_duty_id              uuid        NOT NULL,
    employment_relationship_id  uuid        NOT NULL,
    employee_id                 uuid        NOT NULL,
    covered_org_unit_id         uuid        NOT NULL,
    covered_position_id         uuid,
    covered_employee_id         uuid,
    starts_on_date                   date        NOT NULL,
    ends_before_date                 date        NOT NULL,
    ended_on_date                    date,
    end_cause_kind                   text,
    reason                      text        NOT NULL,
    source_personnel_command_id uuid        NOT NULL,
    created_at                  timestamptz NOT NULL,
    created_by                  uuid        NOT NULL,
    updated_at                  timestamptz NOT NULL,
    version                     bigint      NOT NULL DEFAULT 0,
    CONSTRAINT pk_acting_duties PRIMARY KEY (acting_duty_id),
    CONSTRAINT uk_acting_duties_id_employee UNIQUE (acting_duty_id, employee_id),
    CONSTRAINT uk_acting_duties_source_command UNIQUE (source_personnel_command_id),
    CONSTRAINT fk_acting_duties_relationship FOREIGN KEY (employment_relationship_id, employee_id)
        REFERENCES organization_workforce.employment_relationships (employment_relationship_id, employee_id) ON DELETE RESTRICT,
    CONSTRAINT fk_acting_duties_covered_org_unit_id FOREIGN KEY (covered_org_unit_id) REFERENCES organization_workforce.org_units (id) ON DELETE RESTRICT,
    CONSTRAINT fk_acting_duties_covered_position_id FOREIGN KEY (covered_position_id) REFERENCES organization_workforce.positions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_acting_duties_covered_employee_id FOREIGN KEY (covered_employee_id) REFERENCES organization_workforce.employees (id) ON DELETE RESTRICT,
    CONSTRAINT fk_acting_duties_source_command FOREIGN KEY (source_personnel_command_id, employee_id)
        REFERENCES organization_workforce.personnel_commands (personnel_command_id, employee_id) ON DELETE RESTRICT,
    CONSTRAINT ck_acting_duties_period CHECK (isfinite(starts_on_date) AND isfinite(ends_before_date) AND starts_on_date < ends_before_date),
    CONSTRAINT ck_acting_duties_end_pair CHECK ((ended_on_date IS NULL) = (end_cause_kind IS NULL)),
    CONSTRAINT ck_acting_duties_end_cause CHECK (end_cause_kind IS NULL OR end_cause_kind IN ('EXPIRED', 'ENDED_EARLY', 'SEPARATION')),
    CONSTRAINT ck_acting_duties_ended_range CHECK (ended_on_date IS NULL
        OR (ended_on_date <= ends_before_date AND (ended_on_date > starts_on_date OR (end_cause_kind = 'SEPARATION' AND ended_on_date = starts_on_date)))),
    CONSTRAINT ck_acting_duties_not_self CHECK (covered_employee_id IS DISTINCT FROM employee_id),
    CONSTRAINT ck_acting_duties_reason CHECK (char_length(reason) BETWEEN 1 AND 500 AND btrim(reason) <> '')
);
-- 同一人在同一期间对同一被代理职位只有一个代理职务；岗位可空，空表示部门负责人职责，所以按有无岗位拆成两个排除约束。首尾相接的不算重叠。
ALTER TABLE organization_workforce.acting_duties ADD CONSTRAINT ex_acting_duties_unit_head_overlap EXCLUDE USING gist (
    employee_id WITH =, covered_org_unit_id WITH =, daterange(starts_on_date, coalesce(ended_on_date, ends_before_date), '[)') WITH &&) WHERE (covered_position_id IS NULL);
ALTER TABLE organization_workforce.acting_duties ADD CONSTRAINT ex_acting_duties_position_overlap EXCLUDE USING gist (
    employee_id WITH =, covered_org_unit_id WITH =, covered_position_id WITH =, daterange(starts_on_date, coalesce(ended_on_date, ends_before_date), '[)') WITH &&)
    WHERE (covered_position_id IS NOT NULL);
CREATE INDEX ix_acting_duties_employee_id ON organization_workforce.acting_duties (employee_id, starts_on_date DESC, acting_duty_id DESC);
CREATE INDEX ix_acting_duties_relationship ON organization_workforce.acting_duties (employment_relationship_id, employee_id);
CREATE INDEX ix_acting_duties_covered_org_unit_id ON organization_workforce.acting_duties (covered_org_unit_id, starts_on_date);
CREATE INDEX ix_acting_duties_covered_position_id ON organization_workforce.acting_duties (covered_position_id) WHERE covered_position_id IS NOT NULL;
CREATE INDEX ix_acting_duties_covered_employee_id ON organization_workforce.acting_duties (covered_employee_id) WHERE covered_employee_id IS NOT NULL;
CREATE INDEX ix_acting_duties_open_due ON organization_workforce.acting_duties (ends_before_date) WHERE ended_on_date IS NULL;

COMMENT ON TABLE organization_workforce.acting_duties IS '代理职务：员工在一段期限内暂代某部门或岗位职责的有期限事实，员工聚合内的实体，由人事命令生效时写入；期限内可延长，结束时写一次结束日与原因；状态（待开始、进行中、已结束）由日期算出，不存列；历史在只追加的人事命令里（数据设计 docs/data/reviews/workforce-acting-duty.md）。粒度：每个代理职务一行';
COMMENT ON COLUMN organization_workforce.acting_duties.acting_duty_id IS '代理职务 ID（UUIDv7，由应用层生成）';
COMMENT ON COLUMN organization_workforce.acting_duties.employment_relationship_id IS '雇佣关系 ID：代理人在代理期间所属的雇佣关系（ADR-0086）；与员工 ID 一起做复合外键';
COMMENT ON COLUMN organization_workforce.acting_duties.employee_id IS '员工 ID：代理人';
COMMENT ON COLUMN organization_workforce.acting_duties.covered_org_unit_id IS '被代理部门 ID：代理的是这个部门的职责';
COMMENT ON COLUMN organization_workforce.acting_duties.covered_position_id IS '被代理岗位 ID；为空表示代理的是该部门负责人的职责。岗位是否属于该部门由命令检查（岗位与部门的对应在岗位版本里，跨表）';
COMMENT ON COLUMN organization_workforce.acting_duties.covered_employee_id IS '被代理人 ID（请假、出差期间被暂代的人）；为空表示不指明被代理的具体人。只作信息，在授权问题定论前任何代码不得据此推出授权';
COMMENT ON COLUMN organization_workforce.acting_duties.starts_on_date IS '开始日：代理期的第一天（业务日期，区间左闭）';
COMMENT ON COLUMN organization_workforce.acting_duties.ends_before_date IS '截止日：代理期的最后一天的次日（业务日期，区间右开）；延长时只增不减，每次延长的值留在延长命令里';
COMMENT ON COLUMN organization_workforce.acting_duties.ended_on_date IS '结束日：代理实际结束的业务日期；为空表示尚未结束。到期结束取截止日，提前结束与离职结束取命令生效日；离职恰在开始日生效时等于开始日（空区间）';
COMMENT ON COLUMN organization_workforce.acting_duties.end_cause_kind IS '结束原因：EXPIRED 到期 / ENDED_EARLY 提前结束 / SEPARATION 因离职结束；为空表示尚未结束，与结束日同有同无';
COMMENT ON COLUMN organization_workforce.acting_duties.reason IS '代理事由：人填写的业务原因，1 到 500 字；不得写健康、孕产等个人情况（个人信息 PI）';
COMMENT ON COLUMN organization_workforce.acting_duties.source_personnel_command_id IS '起始人事命令 ID：使本代理职务生效的 ACTING_START 命令；一个命令至多生成一个代理职务';
COMMENT ON COLUMN organization_workforce.acting_duties.created_at IS '创建时间';
COMMENT ON COLUMN organization_workforce.acting_duties.created_by IS '创建人 ID（用户）';
COMMENT ON COLUMN organization_workforce.acting_duties.updated_at IS '更新时间';
COMMENT ON COLUMN organization_workforce.acting_duties.version IS '乐观锁版本号';

-- 2. 触发器：合法写入就这几种。(1) 以未结束状态插入；(2) 未结束的行只能把截止日改大；(3) 未结束转已结束，结束日与结束原因一次写入，其余列不动；(4) 已结束的行不再变。
-- version 与 updated_at 随每次写入变化，不受限。DELETE 只放行表属主（清除通道随 W2 第 8 节另批），TRUNCATE 拒绝。
CREATE FUNCTION organization_workforce.guard_acting_duty_change() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
DECLARE
    owner_name name;
BEGIN
    IF TG_OP = 'TRUNCATE' THEN
        RAISE EXCEPTION 'acting duties cannot be truncated: truncate on %.% is refused', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
    END IF;
    IF TG_OP = 'DELETE' THEN
        SELECT pg_get_userbyid(relowner) INTO owner_name FROM pg_class WHERE oid = TG_RELID;
        IF current_user <> owner_name THEN
            RAISE EXCEPTION 'acting duties are kept: delete on %.% is refused', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
        END IF;
        RETURN OLD;
    END IF;
    IF TG_OP = 'INSERT' THEN
        IF NEW.ended_on_date IS NOT NULL THEN
            RAISE EXCEPTION 'an acting duty is inserted open: ended_on_date and end_cause_kind stay empty until it ends' USING ERRCODE = 'restrict_violation';
        END IF;
        RETURN NEW;
    END IF;
    IF OLD.ended_on_date IS NOT NULL THEN
        RAISE EXCEPTION 'acting duty % has ended and cannot change', OLD.acting_duty_id USING ERRCODE = 'restrict_violation';
    END IF;
    IF (to_jsonb(NEW) - 'ends_before_date' - 'ended_on_date' - 'end_cause_kind' - 'updated_at' - 'version')
            <> (to_jsonb(OLD) - 'ends_before_date' - 'ended_on_date' - 'end_cause_kind' - 'updated_at' - 'version') THEN
        RAISE EXCEPTION 'only ends_before_date, ended_on_date and end_cause_kind of an open acting duty can change (%)', OLD.acting_duty_id USING ERRCODE = 'restrict_violation';
    END IF;
    IF NEW.ended_on_date IS NOT NULL AND NEW.ends_before_date <> OLD.ends_before_date THEN
        RAISE EXCEPTION 'an acting duty is closed without changing ends_before_date (%)', OLD.acting_duty_id USING ERRCODE = 'restrict_violation';
    END IF;
    IF NEW.ends_before_date < OLD.ends_before_date THEN
        RAISE EXCEPTION 'ends_before_date of acting duty % can only grow', OLD.acting_duty_id USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END $$;
COMMENT ON FUNCTION organization_workforce.guard_acting_duty_change() IS '代理职务写入规则的触发器：未结束的行只能延长截止日或一次写入结束日与结束原因，已结束的行不再变，插入时必须是未结束，DELETE 只放行表属主，TRUNCATE 拒绝';
CREATE TRIGGER tg_acting_duties_change BEFORE INSERT OR UPDATE OR DELETE ON organization_workforce.acting_duties
    FOR EACH ROW EXECUTE FUNCTION organization_workforce.guard_acting_duty_change();
CREATE TRIGGER tg_acting_duties_truncate BEFORE TRUNCATE ON organization_workforce.acting_duties
    FOR EACH STATEMENT EXECUTE FUNCTION organization_workforce.guard_acting_duty_change();

-- 3. 人事命令：六个可空列，三个新命令种类，按种类约束列。
ALTER TABLE organization_workforce.personnel_commands
    ADD COLUMN acting_covered_org_unit_id uuid,
    ADD COLUMN acting_covered_position_id uuid,
    ADD COLUMN acting_covered_employee_id uuid,
    ADD COLUMN acting_ends_before_date         date,
    ADD COLUMN target_acting_duty_id      uuid,
    ADD COLUMN reason                     text,
    ADD CONSTRAINT fk_personnel_commands_acting_covered_org_unit_id FOREIGN KEY (acting_covered_org_unit_id) REFERENCES organization_workforce.org_units (id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_personnel_commands_acting_covered_position_id FOREIGN KEY (acting_covered_position_id) REFERENCES organization_workforce.positions (id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_personnel_commands_acting_covered_employee_id FOREIGN KEY (acting_covered_employee_id) REFERENCES organization_workforce.employees (id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_personnel_commands_target_acting_duty FOREIGN KEY (target_acting_duty_id, employee_id)
        REFERENCES organization_workforce.acting_duties (acting_duty_id, employee_id) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_personnel_commands_reason CHECK (reason IS NULL OR (char_length(reason) BETWEEN 1 AND 500 AND btrim(reason) <> '')),
    ADD CONSTRAINT ck_personnel_commands_acting_position_unit CHECK (acting_covered_position_id IS NULL OR acting_covered_org_unit_id IS NOT NULL),
    ADD CONSTRAINT ck_personnel_commands_acting_not_self CHECK (acting_covered_employee_id IS DISTINCT FROM employee_id),
    ADD CONSTRAINT ck_personnel_commands_acting_no_assignment_targets CHECK (command_kind NOT IN ('ACTING_START', 'ACTING_END', 'ACTING_EXTEND')
        OR num_nonnulls(target_org_unit_id, target_position_id, target_reports_to_employee_id, is_target_fee_earner, target_employment_phase_kind,
            is_target_on_unpaid_leave, target_assignment_id) = 0);
ALTER TABLE organization_workforce.personnel_commands ADD CONSTRAINT ck_personnel_commands_acting_columns CHECK (
    CASE command_kind
        WHEN 'ACTING_START' THEN acting_covered_org_unit_id IS NOT NULL AND acting_ends_before_date IS NOT NULL AND reason IS NOT NULL
            AND effective_date < acting_ends_before_date AND target_acting_duty_id IS NULL
        WHEN 'ACTING_END' THEN target_acting_duty_id IS NOT NULL AND reason IS NOT NULL
            AND num_nonnulls(acting_covered_org_unit_id, acting_covered_position_id, acting_covered_employee_id, acting_ends_before_date) = 0
        WHEN 'ACTING_EXTEND' THEN target_acting_duty_id IS NOT NULL AND acting_ends_before_date IS NOT NULL AND reason IS NOT NULL
            AND num_nonnulls(acting_covered_org_unit_id, acting_covered_position_id, acting_covered_employee_id) = 0
        ELSE num_nonnulls(acting_covered_org_unit_id, acting_covered_position_id, acting_covered_employee_id, acting_ends_before_date, target_acting_duty_id, reason) = 0
    END);
CREATE INDEX ix_personnel_commands_acting_covered_org_unit_id ON organization_workforce.personnel_commands (acting_covered_org_unit_id) WHERE acting_covered_org_unit_id IS NOT NULL;
CREATE INDEX ix_personnel_commands_acting_covered_position_id ON organization_workforce.personnel_commands (acting_covered_position_id) WHERE acting_covered_position_id IS NOT NULL;
CREATE INDEX ix_personnel_commands_acting_covered_employee_id ON organization_workforce.personnel_commands (acting_covered_employee_id) WHERE acting_covered_employee_id IS NOT NULL;
CREATE INDEX ix_personnel_commands_target_acting_duty ON organization_workforce.personnel_commands (target_acting_duty_id, employee_id) WHERE target_acting_duty_id IS NOT NULL;

ALTER TABLE organization_workforce.personnel_commands DROP CONSTRAINT ck_personnel_commands_command_kind;
ALTER TABLE organization_workforce.personnel_commands ADD CONSTRAINT ck_personnel_commands_command_kind CHECK (command_kind IN
    ('HIRE', 'REGULARIZE', 'TRANSFER', 'SEPARATE', 'REINSTATE', 'CORRECTION', 'UNPAID_LEAVE_START', 'UNPAID_LEAVE_END', 'CHANGE_LEGAL_ENTITY', 'START_CONCURRENT', 'END_CONCURRENT',
     'ACTING_START', 'ACTING_END', 'ACTING_EXTEND'));
-- 延长在录入当天立即生效（同更正），所以与更正一样必须是已生效状态。
ALTER TABLE organization_workforce.personnel_commands DROP CONSTRAINT ck_personnel_commands_correction_immediate;
ALTER TABLE organization_workforce.personnel_commands ADD CONSTRAINT ck_personnel_commands_correction_immediate CHECK (
    command_kind NOT IN ('CORRECTION', 'ACTING_EXTEND') OR status = 'EFFECTIVE'
    OR (command_kind = 'CORRECTION' AND countersign_new_legal_entity_id IS NOT NULL AND status IN ('AWAITING_ATTRIBUTION_COUNTERSIGN', 'WITHDRAWN')));

-- 同日一条：代理三种命令不占雇佣关系的当日名额（代理不是任职变动，与调岗同日是合法的）；代理专用的两条唯一索引防同日重复。
DROP INDEX organization_workforce.uk_personnel_commands_open_per_relationship_day;
CREATE UNIQUE INDEX uk_personnel_commands_open_per_relationship_day ON organization_workforce.personnel_commands (employment_relationship_id, effective_date)
    WHERE status IN ('PENDING', 'HELD', 'AWAITING_ATTRIBUTION_COUNTERSIGN') AND employment_relationship_id IS NOT NULL
        AND command_kind NOT IN ('START_CONCURRENT', 'ACTING_START', 'ACTING_END', 'ACTING_EXTEND') AND target_assignment_kind IS DISTINCT FROM 'CONCURRENT';
-- One open start per person, covered unit and day; the post is nullable, so a unit-head start and a post start are two partial indexes (no sentinel value).
CREATE UNIQUE INDEX uk_personnel_commands_open_acting_start_unit ON organization_workforce.personnel_commands (employee_id, acting_covered_org_unit_id, effective_date)
    WHERE command_kind = 'ACTING_START' AND status = 'PENDING' AND acting_covered_position_id IS NULL;
CREATE UNIQUE INDEX uk_personnel_commands_open_acting_start_position ON organization_workforce.personnel_commands (
    employee_id, acting_covered_org_unit_id, acting_covered_position_id, effective_date)
    WHERE command_kind = 'ACTING_START' AND status = 'PENDING' AND acting_covered_position_id IS NOT NULL;
CREATE UNIQUE INDEX uk_personnel_commands_open_acting_end ON organization_workforce.personnel_commands (target_acting_duty_id)
    WHERE command_kind = 'ACTING_END' AND status = 'PENDING';

COMMENT ON COLUMN organization_workforce.personnel_commands.command_kind IS '命令种类：HIRE 入职 / REGULARIZE 转正 / TRANSFER 调岗 / SEPARATE 离职 / REINSTATE 复职 / CORRECTION 更正 / UNPAID_LEAVE_START 停薪留职开始 / UNPAID_LEAVE_END 停薪留职结束 / CHANGE_LEGAL_ENTITY 变更法人归属 / START_CONCURRENT 新增兼职 / END_CONCURRENT 结束兼职 / ACTING_START 代理开始 / ACTING_END 代理提前结束 / ACTING_EXTEND 代理延长';
COMMENT ON COLUMN organization_workforce.personnel_commands.acting_covered_org_unit_id IS '被代理部门 ID：代理开始必填；为空表示不是代理开始命令';
COMMENT ON COLUMN organization_workforce.personnel_commands.acting_covered_position_id IS '被代理岗位 ID：代理开始可填；为空表示代理的是该部门负责人的职责，或不是代理开始命令';
COMMENT ON COLUMN organization_workforce.personnel_commands.acting_covered_employee_id IS '被代理人 ID：代理开始可填；为空表示不指明被代理的具体人，或不是代理开始命令';
COMMENT ON COLUMN organization_workforce.personnel_commands.acting_ends_before_date IS '代理截止日（最后一天的次日）：代理开始与代理延长必填；为空表示不是这两种命令';
COMMENT ON COLUMN organization_workforce.personnel_commands.target_acting_duty_id IS '目标代理职务 ID：代理提前结束与代理延长必填，指向被结束或被延长的代理职务；为空表示不是这两种命令';
COMMENT ON COLUMN organization_workforce.personnel_commands.reason IS '事由：代理三种命令必填，人填写的业务原因，1 到 500 字，不得写健康、孕产等个人情况；为空表示不是代理命令';
