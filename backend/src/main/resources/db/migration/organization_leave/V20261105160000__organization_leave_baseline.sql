-- 请假与考勤事实 L1a：#1400，逐表依据签署的数据设计 organization-leave-l1a。
CREATE SCHEMA IF NOT EXISTS organization_leave;
COMMENT ON SCHEMA organization_leave IS '请假与考勤事实及额度规则、账户与账本；L1a、L1b、S3 十四表';

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'btree_gist') THEN
        RAISE EXCEPTION 'extension btree_gist is missing: database setup creates it before migrations';
    END IF;
END $$;

CREATE TABLE organization_leave.leave_requests (
    id uuid NOT NULL,
    employment_relationship_id uuid NOT NULL,
    employee_id uuid NOT NULL,
    leave_type_id uuid NOT NULL,
    start_at timestamptz NOT NULL,
    end_at timestamptz NOT NULL,
    taken_until_at timestamptz,
    status text NOT NULL,
    approval_instance_id uuid,
    legal_entity_id uuid NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT pk_leave_requests PRIMARY KEY (id),
    CONSTRAINT ck_leave_requests_period CHECK (end_at > start_at),
    CONSTRAINT ck_leave_requests_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'IN_APPROVAL', 'APPROVED', 'REJECTED', 'WITHDRAWN', 'RECALLED', 'CANCELLED')),
    CONSTRAINT ck_leave_requests_in_approval_has_instance CHECK (status <> 'IN_APPROVAL' OR approval_instance_id IS NOT NULL),
    CONSTRAINT ck_leave_requests_taken_until_at CHECK ((status = 'RECALLED') = (taken_until_at IS NOT NULL) AND (taken_until_at IS NULL OR taken_until_at BETWEEN start_at AND end_at)),
    CONSTRAINT ex_leave_requests_live_period_overlap EXCLUDE USING gist (employment_relationship_id WITH =, tstzrange(start_at, CASE WHEN status = 'RECALLED' THEN taken_until_at ELSE end_at END) WITH &&) WHERE (status IN ('SUBMITTED', 'IN_APPROVAL', 'APPROVED', 'RECALLED'))
);

CREATE TABLE organization_leave.leave_request_days (
    id uuid NOT NULL,
    leave_request_id uuid NOT NULL,
    work_date date NOT NULL,
    minutes_count integer NOT NULL,
    day_hundredths_count integer NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT pk_leave_request_days PRIMARY KEY (id),
    CONSTRAINT fk_leave_request_days_leave_request_id FOREIGN KEY (leave_request_id) REFERENCES organization_leave.leave_requests (id) ON DELETE RESTRICT,
    CONSTRAINT uk_leave_request_days_request_work_date UNIQUE (leave_request_id, work_date),
    CONSTRAINT ck_leave_request_days_minutes_count CHECK (minutes_count BETWEEN 0 AND 1440),
    CONSTRAINT ck_leave_request_days_day_hundredths_count CHECK (day_hundredths_count BETWEEN 0 AND 100)
);

CREATE TABLE organization_leave.leave_request_holds (
    id uuid NOT NULL,
    leave_request_id uuid NOT NULL,
    ledger_line_id uuid NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT pk_leave_request_holds PRIMARY KEY (id),
    CONSTRAINT fk_leave_request_holds_leave_request_id FOREIGN KEY (leave_request_id) REFERENCES organization_leave.leave_requests (id) ON DELETE RESTRICT,
    CONSTRAINT uk_leave_request_holds_request_line UNIQUE (leave_request_id, ledger_line_id),
    CONSTRAINT uk_leave_request_holds_ledger_line_id UNIQUE (ledger_line_id)
);

CREATE TABLE organization_leave.overtime_requests (
    id uuid NOT NULL,
    employment_relationship_id uuid NOT NULL,
    employee_id uuid NOT NULL,
    work_date date NOT NULL,
    minutes_count integer NOT NULL,
    day_kind text NOT NULL,
    compensation_kind text NOT NULL,
    status text NOT NULL,
    approval_instance_id uuid,
    legal_entity_id uuid NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT pk_overtime_requests PRIMARY KEY (id),
    CONSTRAINT ck_overtime_requests_minutes_count CHECK (minutes_count BETWEEN 1 AND 1440),
    CONSTRAINT ck_overtime_requests_day_kind CHECK (day_kind IN ('WORKDAY', 'REST_DAY', 'STATUTORY_HOLIDAY')),
    CONSTRAINT ck_overtime_requests_compensation_kind CHECK (compensation_kind IN ('PAY', 'COMP_TIME')),
    CONSTRAINT ck_overtime_requests_status CHECK (status IN ('REQUESTED', 'IN_APPROVAL', 'APPROVED', 'COMPENSATED_PAY', 'CREDITED_AS_COMP_TIME', 'REJECTED', 'WITHDRAWN')),
    CONSTRAINT ck_overtime_requests_holiday_pay_only CHECK (day_kind <> 'STATUTORY_HOLIDAY' OR compensation_kind = 'PAY'),
    CONSTRAINT ck_overtime_requests_status_matches_compensation CHECK ((status = 'COMPENSATED_PAY') <= (compensation_kind = 'PAY') AND (status = 'CREDITED_AS_COMP_TIME') <= (compensation_kind = 'COMP_TIME')),
    CONSTRAINT ck_overtime_requests_in_approval_has_instance CHECK (status <> 'IN_APPROVAL' OR approval_instance_id IS NOT NULL)
);

CREATE TABLE organization_leave.attendance_records (
    id uuid NOT NULL,
    employee_id uuid NOT NULL,
    record_kind text NOT NULL,
    occurred_at timestamptz NOT NULL,
    source_system text NOT NULL,
    external_record_key text NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT pk_attendance_records PRIMARY KEY (id),
    CONSTRAINT ck_attendance_records_record_kind CHECK (record_kind IN ('PUNCH', 'OUTFIELD', 'MAKE_UP')),
    CONSTRAINT ck_attendance_records_source_system CHECK (source_system ~ '^[A-Z][A-Z0-9_]{1,31}$'),
    CONSTRAINT ck_attendance_records_external_record_key CHECK (external_record_key = btrim(external_record_key) AND char_length(external_record_key) BETWEEN 1 AND 100),
    CONSTRAINT uk_attendance_records_source_external_record_key UNIQUE (source_system, external_record_key)
);

CREATE TABLE organization_leave.monthly_confirmations (
    id uuid NOT NULL,
    employee_id uuid NOT NULL,
    month_start_date date NOT NULL,
    status text NOT NULL,
    confirmed_by_kind text,
    confirmed_at timestamptz,
    locked_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT pk_monthly_confirmations PRIMARY KEY (id),
    CONSTRAINT ck_monthly_confirmations_month_start_date CHECK (EXTRACT(DAY FROM month_start_date) = 1),
    CONSTRAINT ck_monthly_confirmations_status CHECK (status IN ('GENERATED', 'CONFIRMED', 'LOCKED')),
    CONSTRAINT ck_monthly_confirmations_confirmed_by_kind CHECK (confirmed_by_kind IN ('EMPLOYEE', 'DEFAULT')),
    CONSTRAINT uk_monthly_confirmations_employee_month UNIQUE (employee_id, month_start_date),
    CONSTRAINT ck_monthly_confirmations_confirmation_columns CHECK ((status = 'GENERATED') = (confirmed_by_kind IS NULL) AND (confirmed_by_kind IS NULL) = (confirmed_at IS NULL)),
    CONSTRAINT ck_monthly_confirmations_lock_columns CHECK ((status = 'LOCKED') = (locked_at IS NOT NULL))
);

CREATE TABLE organization_leave.monthly_confirmation_lines (
    id uuid NOT NULL,
    monthly_confirmation_id uuid NOT NULL,
    employment_relationship_id uuid NOT NULL,
    line_kind text NOT NULL,
    leave_type_id uuid,
    is_paid_leave boolean,
    compensation_kind text,
    day_kind text,
    minutes_count integer,
    day_hundredths_count integer,
    original_month_start_date date,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT pk_monthly_confirmation_lines PRIMARY KEY (id),
    CONSTRAINT fk_monthly_confirmation_lines_monthly_confirmation_id FOREIGN KEY (monthly_confirmation_id) REFERENCES organization_leave.monthly_confirmations (id) ON DELETE RESTRICT,
    CONSTRAINT ck_monthly_confirmation_lines_line_kind CHECK (line_kind IN ('ATTENDANCE_DAYS', 'LEAVE', 'OVERTIME')),
    CONSTRAINT ck_monthly_confirmation_lines_compensation_kind CHECK (compensation_kind IN ('PAY', 'COMP_TIME')),
    CONSTRAINT ck_monthly_confirmation_lines_day_kind CHECK (day_kind IN ('WORKDAY', 'REST_DAY', 'STATUTORY_HOLIDAY')),
    CONSTRAINT ck_monthly_confirmation_lines_minutes_count CHECK (minutes_count >= 0),
    CONSTRAINT ck_monthly_confirmation_lines_day_hundredths_count CHECK (day_hundredths_count >= 0),
    CONSTRAINT ck_monthly_confirmation_lines_leave_columns CHECK ((line_kind = 'LEAVE') = (leave_type_id IS NOT NULL) AND (leave_type_id IS NULL) = (is_paid_leave IS NULL)),
    CONSTRAINT ck_monthly_confirmation_lines_overtime_columns CHECK ((line_kind = 'OVERTIME') = (compensation_kind IS NOT NULL) AND (line_kind = 'OVERTIME') = (day_kind IS NOT NULL)),
    CONSTRAINT ck_monthly_confirmation_lines_quantity_columns CHECK ((line_kind = 'ATTENDANCE_DAYS') = (day_hundredths_count IS NOT NULL) AND (line_kind = 'ATTENDANCE_DAYS') = (minutes_count IS NULL)),
    CONSTRAINT ck_monthly_confirmation_lines_original_month_start_date CHECK (EXTRACT(DAY FROM original_month_start_date) = 1),
    CONSTRAINT uk_monthly_confirmation_lines_identity UNIQUE NULLS NOT DISTINCT (monthly_confirmation_id, employment_relationship_id, line_kind, leave_type_id, compensation_kind, day_kind, original_month_start_date)
);

CREATE UNIQUE INDEX uk_leave_requests_approval_instance_id ON organization_leave.leave_requests (approval_instance_id) WHERE approval_instance_id IS NOT NULL;
CREATE UNIQUE INDEX uk_overtime_requests_approval_instance_id ON organization_leave.overtime_requests (approval_instance_id) WHERE approval_instance_id IS NOT NULL;
CREATE INDEX ix_leave_requests_employee_id_start_at ON organization_leave.leave_requests (employee_id, start_at, id);
CREATE INDEX ix_leave_requests_legal_entity_id_start_at ON organization_leave.leave_requests (legal_entity_id, start_at, id);
CREATE INDEX ix_leave_requests_relationship_id_status ON organization_leave.leave_requests (employment_relationship_id, status);
CREATE INDEX ix_leave_request_days_work_date_leave_request_id ON organization_leave.leave_request_days (work_date, leave_request_id);
CREATE INDEX ix_overtime_requests_employee_id_work_date ON organization_leave.overtime_requests (employee_id, work_date, id);
CREATE INDEX ix_overtime_requests_relationship_id_work_date ON organization_leave.overtime_requests (employment_relationship_id, work_date);
CREATE INDEX ix_attendance_records_employee_id_occurred_at ON organization_leave.attendance_records (employee_id, occurred_at, id);
CREATE INDEX ix_monthly_confirmations_generated_month ON organization_leave.monthly_confirmations (month_start_date) WHERE status = 'GENERATED';

-- RULE-519：追加事实不能改写、删除或清空；占用关联保留同样的事实语义。
CREATE FUNCTION organization_leave.guard_append_only() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
BEGIN
    RAISE EXCEPTION '% is append-only', TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
END $$;
CREATE TRIGGER tg_attendance_records_append_only BEFORE UPDATE OR DELETE ON organization_leave.attendance_records
    FOR EACH ROW EXECUTE FUNCTION organization_leave.guard_append_only();
CREATE TRIGGER tg_attendance_records_truncate BEFORE TRUNCATE ON organization_leave.attendance_records
    FOR EACH STATEMENT EXECUTE FUNCTION organization_leave.guard_append_only();
CREATE TRIGGER tg_leave_request_holds_append_only BEFORE UPDATE OR DELETE ON organization_leave.leave_request_holds
    FOR EACH ROW EXECUTE FUNCTION organization_leave.guard_append_only();
CREATE TRIGGER tg_leave_request_holds_truncate BEFORE TRUNCATE ON organization_leave.leave_request_holds
    FOR EACH STATEMENT EXECUTE FUNCTION organization_leave.guard_append_only();

-- INV-105、RULE-518：明细写入与父单锁定串行；改挂父单同时检查旧、新父单，不能把已锁明细搬走。
CREATE FUNCTION organization_leave.guard_locked_monthly_confirmation() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
DECLARE
    old_parent uuid;
    new_parent uuid;
    parent_status text;
BEGIN
    IF TG_TABLE_NAME = 'monthly_confirmations' THEN
        IF OLD.status = 'LOCKED' THEN
            RAISE EXCEPTION 'monthly confirmation is LOCKED' USING ERRCODE = 'restrict_violation';
        END IF;
    ELSE
        IF TG_OP <> 'INSERT' THEN old_parent := OLD.monthly_confirmation_id; END IF;
        IF TG_OP <> 'DELETE' THEN new_parent := NEW.monthly_confirmation_id; END IF;
        FOR parent_status IN
            SELECT status FROM organization_leave.monthly_confirmations
            WHERE id = old_parent OR id = new_parent ORDER BY id FOR UPDATE
        LOOP
            IF parent_status = 'LOCKED' THEN
                RAISE EXCEPTION 'monthly confirmation is LOCKED' USING ERRCODE = 'restrict_violation';
            END IF;
        END LOOP;
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER guard_locked_monthly_confirmation BEFORE UPDATE OR DELETE ON organization_leave.monthly_confirmations
    FOR EACH ROW EXECUTE FUNCTION organization_leave.guard_locked_monthly_confirmation();
CREATE TRIGGER guard_locked_monthly_confirmation BEFORE INSERT OR UPDATE OR DELETE ON organization_leave.monthly_confirmation_lines
    FOR EACH ROW EXECUTE FUNCTION organization_leave.guard_locked_monthly_confirmation();

COMMENT ON TABLE organization_leave.leave_requests IS '请假申请。粒度：每个雇佣关系每次请假申请一行。';
COMMENT ON COLUMN organization_leave.leave_requests.id IS '主键（UUIDv7）';
COMMENT ON COLUMN organization_leave.leave_requests.employment_relationship_id IS '雇佣关系 ID，本次事实所属关系，跨上下文不建外键';
COMMENT ON COLUMN organization_leave.leave_requests.employee_id IS '员工 ID，用于本人查询与数据范围判断，跨上下文不建外键';
COMMENT ON COLUMN organization_leave.leave_requests.leave_type_id IS '请假类型 ID';
COMMENT ON COLUMN organization_leave.leave_requests.start_at IS '请假起始时刻（含）';
COMMENT ON COLUMN organization_leave.leave_requests.end_at IS '请假结束时刻（不含）';
COMMENT ON COLUMN organization_leave.leave_requests.taken_until_at IS '已休截止时刻；为空表示未被提前终止；销假后为第一天未休的起点（业务时区当天零点；整单销假且请假起点晚于当天零点时取起点 start_at）';
COMMENT ON COLUMN organization_leave.leave_requests.status IS '申请状态：DRAFT 草稿、SUBMITTED 已提交、IN_APPROVAL 审批中、APPROVED 已批准、REJECTED 已拒绝、WITHDRAWN 已撤回、RECALLED 已撤销、CANCELLED 已取消';
COMMENT ON COLUMN organization_leave.leave_requests.approval_instance_id IS '审批实例 ID；为空表示尚未发起审批（草稿、自动通过或在进入审批前被拒）';
COMMENT ON COLUMN organization_leave.leave_requests.legal_entity_id IS '法人主体 ID，事实开始日的成本归属法人，不是用人单位 ID，跨上下文不建外键';
COMMENT ON COLUMN organization_leave.leave_requests.created_at IS '创建时刻';
COMMENT ON COLUMN organization_leave.leave_requests.updated_at IS '更新时刻';
COMMENT ON COLUMN organization_leave.leave_requests.version IS '乐观锁版本';

COMMENT ON TABLE organization_leave.leave_request_days IS '请假申请按日拆分的时长。粒度：每个请假申请的每个工作日一行。';
COMMENT ON COLUMN organization_leave.leave_request_days.id IS '主键（UUIDv7）';
COMMENT ON COLUMN organization_leave.leave_request_days.leave_request_id IS '请假申请 ID，所属申请';
COMMENT ON COLUMN organization_leave.leave_request_days.work_date IS '工作日期，拆分日期';
COMMENT ON COLUMN organization_leave.leave_request_days.minutes_count IS '当天请假分钟数，0 表示非工作日，范围 0 至 1440';
COMMENT ON COLUMN organization_leave.leave_request_days.day_hundredths_count IS '百分之一天数，当天请假天数，范围 0 至 100';
COMMENT ON COLUMN organization_leave.leave_request_days.created_at IS '创建时刻';
COMMENT ON COLUMN organization_leave.leave_request_days.updated_at IS '更新时刻';

COMMENT ON TABLE organization_leave.leave_request_holds IS '请假申请占用的额度流水。粒度：每个请假申请占用的每条额度流水一行。';
COMMENT ON COLUMN organization_leave.leave_request_holds.id IS '主键（UUIDv7）';
COMMENT ON COLUMN organization_leave.leave_request_holds.leave_request_id IS '请假申请 ID，占用额度的申请';
COMMENT ON COLUMN organization_leave.leave_request_holds.ledger_line_id IS '被占用的 HOLD 额度流水 ID';
COMMENT ON COLUMN organization_leave.leave_request_holds.created_at IS '创建时刻';
COMMENT ON COLUMN organization_leave.leave_request_holds.updated_at IS '更新时刻（只追加表，触发器拒绝更新，列只为满足通用审计列约定）';

COMMENT ON TABLE organization_leave.overtime_requests IS '加班申请。粒度：每个雇佣关系每次加班申请一行。';
COMMENT ON COLUMN organization_leave.overtime_requests.id IS '主键（UUIDv7）';
COMMENT ON COLUMN organization_leave.overtime_requests.employment_relationship_id IS '雇佣关系 ID，本次事实所属关系，跨上下文不建外键';
COMMENT ON COLUMN organization_leave.overtime_requests.employee_id IS '员工 ID，用于本人查询与数据范围判断，跨上下文不建外键';
COMMENT ON COLUMN organization_leave.overtime_requests.work_date IS '工作日期，加班日期';
COMMENT ON COLUMN organization_leave.overtime_requests.minutes_count IS '加班分钟数，范围 1 至 1440';
COMMENT ON COLUMN organization_leave.overtime_requests.day_kind IS '加班日类型：WORKDAY 工作日、REST_DAY 休息日、STATUTORY_HOLIDAY 法定节假日';
COMMENT ON COLUMN organization_leave.overtime_requests.compensation_kind IS '加班补偿类型：PAY 加班工资、COMP_TIME 调休';
COMMENT ON COLUMN organization_leave.overtime_requests.status IS '申请状态：REQUESTED 已申请、IN_APPROVAL 审批中、APPROVED 已批准、COMPENSATED_PAY 已付加班工资、CREDITED_AS_COMP_TIME 已入调休额度、REJECTED 已拒绝、WITHDRAWN 已撤回';
COMMENT ON COLUMN organization_leave.overtime_requests.approval_instance_id IS '审批实例 ID；为空表示尚未发起审批（草稿、自动通过或在进入审批前被拒）';
COMMENT ON COLUMN organization_leave.overtime_requests.legal_entity_id IS '法人主体 ID，事实开始日的成本归属法人，不是用人单位 ID，跨上下文不建外键';
COMMENT ON COLUMN organization_leave.overtime_requests.created_at IS '创建时刻';
COMMENT ON COLUMN organization_leave.overtime_requests.updated_at IS '更新时刻';
COMMENT ON COLUMN organization_leave.overtime_requests.version IS '乐观锁版本';

COMMENT ON TABLE organization_leave.attendance_records IS '考勤记录：外部打卡，只追加。粒度：每个来源系统的每条打卡记录一行。';
COMMENT ON COLUMN organization_leave.attendance_records.id IS '主键（UUIDv7）';
COMMENT ON COLUMN organization_leave.attendance_records.employee_id IS '员工 ID，用于本人查询与数据范围判断，跨上下文不建外键';
COMMENT ON COLUMN organization_leave.attendance_records.record_kind IS '记录类型：PUNCH 打卡、OUTFIELD 外勤、MAKE_UP 补卡（独立记录）';
COMMENT ON COLUMN organization_leave.attendance_records.occurred_at IS '打卡发生时刻';
COMMENT ON COLUMN organization_leave.attendance_records.source_system IS '来源系统代码，2 至 32 位大写字母、数字或下划线，首位大写字母';
COMMENT ON COLUMN organization_leave.attendance_records.external_record_key IS '外部记录标识，来源系统内的记录标识，去首尾空格，1 至 100 字符，不指向个人';
COMMENT ON COLUMN organization_leave.attendance_records.created_at IS '创建时刻';
COMMENT ON COLUMN organization_leave.attendance_records.updated_at IS '更新时刻（只追加表，触发器拒绝更新，列只为满足通用审计列约定）';

COMMENT ON TABLE organization_leave.monthly_confirmations IS '月度考勤确认。粒度：每个员工每个自然月一行。';
COMMENT ON COLUMN organization_leave.monthly_confirmations.id IS '主键（UUIDv7）';
COMMENT ON COLUMN organization_leave.monthly_confirmations.employee_id IS '员工 ID，用于本人查询与数据范围判断，跨上下文不建外键';
COMMENT ON COLUMN organization_leave.monthly_confirmations.month_start_date IS '月份首日，确认月份';
COMMENT ON COLUMN organization_leave.monthly_confirmations.status IS '确认状态：GENERATED 已生成、CONFIRMED 已确认、LOCKED 已锁定';
COMMENT ON COLUMN organization_leave.monthly_confirmations.confirmed_by_kind IS '确认方式：EMPLOYEE 员工确认、DEFAULT 默认确认；为空表示尚未确认';
COMMENT ON COLUMN organization_leave.monthly_confirmations.confirmed_at IS '确认时刻；为空表示尚未确认';
COMMENT ON COLUMN organization_leave.monthly_confirmations.locked_at IS '锁定时刻；为空表示尚未锁定';
COMMENT ON COLUMN organization_leave.monthly_confirmations.created_at IS '创建时刻';
COMMENT ON COLUMN organization_leave.monthly_confirmations.updated_at IS '更新时刻';
COMMENT ON COLUMN organization_leave.monthly_confirmations.version IS '乐观锁版本';

COMMENT ON TABLE organization_leave.monthly_confirmation_lines IS '月度考勤确认的明细。粒度：每个确认单每个关系每个出勤、请假或加班事实一行。';
COMMENT ON COLUMN organization_leave.monthly_confirmation_lines.id IS '主键（UUIDv7）';
COMMENT ON COLUMN organization_leave.monthly_confirmation_lines.monthly_confirmation_id IS '所属月度考勤确认 ID';
COMMENT ON COLUMN organization_leave.monthly_confirmation_lines.employment_relationship_id IS '雇佣关系 ID，本次事实所属关系，跨上下文不建外键';
COMMENT ON COLUMN organization_leave.monthly_confirmation_lines.line_kind IS '明细类型：ATTENDANCE_DAYS 出勤天数、LEAVE 请假分钟、OVERTIME 加班分钟';
COMMENT ON COLUMN organization_leave.monthly_confirmation_lines.leave_type_id IS '请假类型 ID；为空表示不是请假行';
COMMENT ON COLUMN organization_leave.monthly_confirmation_lines.is_paid_leave IS '带薪请假标志，生成时复制；为空表示不是请假行';
COMMENT ON COLUMN organization_leave.monthly_confirmation_lines.compensation_kind IS '加班补偿类型：PAY 加班工资、COMP_TIME 调休；为空表示不是加班行';
COMMENT ON COLUMN organization_leave.monthly_confirmation_lines.day_kind IS '日类型：WORKDAY 工作日、REST_DAY 休息日、STATUTORY_HOLIDAY 法定节假日；为空表示不是加班行';
COMMENT ON COLUMN organization_leave.monthly_confirmation_lines.minutes_count IS '时长（分钟），非负；为空表示这是按天计的出勤行';
COMMENT ON COLUMN organization_leave.monthly_confirmation_lines.day_hundredths_count IS '百分之一天数，非负；为空表示这是按分钟计的行';
COMMENT ON COLUMN organization_leave.monthly_confirmation_lines.original_month_start_date IS '原属月份首日，被更正月份；为空表示不是跨月更正行';
COMMENT ON COLUMN organization_leave.monthly_confirmation_lines.created_at IS '创建时刻';
COMMENT ON COLUMN organization_leave.monthly_confirmation_lines.updated_at IS '更新时刻';


-- L1b #1470：六表结构，以 organization-leave-l1b 签署记录为准；不写规则取值。
CREATE TABLE organization_leave.leave_types (
    id uuid NOT NULL,
    code text NOT NULL,
    display_name text NOT NULL,
    ledger_unit_kind text NOT NULL,
    is_paid boolean NOT NULL,
    is_quota_counted boolean NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT pk_leave_types PRIMARY KEY (id),
    CONSTRAINT uk_leave_types_code UNIQUE (code),
    CONSTRAINT ck_leave_types_code CHECK (code ~ '^[A-Z][A-Z0-9_]{1,31}$'),
    CONSTRAINT ck_leave_types_display_name CHECK (display_name = btrim(display_name) AND char_length(display_name) BETWEEN 1 AND 60),
    CONSTRAINT ck_leave_types_ledger_unit_kind CHECK (ledger_unit_kind IN ('MINUTE', 'DAY'))
);

CREATE TABLE organization_leave.leave_rule_versions (
    id uuid NOT NULL,
    leave_type_id uuid NOT NULL,
    scope_kind text NOT NULL,
    employer_entity_id uuid,
    version_number integer NOT NULL,
    status text NOT NULL,
    effective_from_date date NOT NULL,
    effective_to_date date,
    request_unit_kind text NOT NULL,
    is_negative_allowed boolean NOT NULL,
    exempt_threshold_minutes_count integer,
    is_proof_required boolean NOT NULL,
    rollover_kind text NOT NULL,
    rollover_cap_qty integer,
    rollover_expiry_month_number integer,
    grant_valid_days_count integer,
    basis_ref_text text NOT NULL,
    published_by uuid NOT NULL,
    published_at timestamptz NOT NULL,
    decision_audit_entry_id uuid NOT NULL,
    voided_by uuid,
    voided_at timestamptz,
    void_reason_text text,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT pk_leave_rule_versions PRIMARY KEY (id),
    CONSTRAINT fk_leave_rule_versions_leave_type_id FOREIGN KEY (leave_type_id) REFERENCES organization_leave.leave_types (id) ON DELETE RESTRICT,
    CONSTRAINT uk_leave_rule_versions_id_leave_type_id UNIQUE (id, leave_type_id),
    CONSTRAINT ck_leave_rule_versions_scope_kind CHECK (scope_kind IN ('ALL', 'EMPLOYER_ENTITY')),
    CONSTRAINT ck_leave_rule_versions_scope_matches_entity CHECK ((scope_kind = 'ALL') = (employer_entity_id IS NULL)),
    CONSTRAINT ck_leave_rule_versions_version_number CHECK (version_number >= 1),
    CONSTRAINT ck_leave_rule_versions_status CHECK (status IN ('IN_EFFECT', 'VOIDED')),
    CONSTRAINT ck_leave_rule_versions_effective_range CHECK (effective_to_date IS NULL OR effective_to_date > effective_from_date),
    CONSTRAINT ck_leave_rule_versions_request_unit_kind CHECK (request_unit_kind IN ('MINUTE', 'HALF_DAY', 'DAY')),
    CONSTRAINT ck_leave_rule_versions_exempt_threshold_minutes_count CHECK (exempt_threshold_minutes_count > 0),
    CONSTRAINT ck_leave_rule_versions_rollover_kind CHECK (rollover_kind IN ('NONE', 'CAPPED', 'UNLIMITED')),
    CONSTRAINT ck_leave_rule_versions_rollover_columns CHECK (
        (rollover_kind = 'CAPPED') = (rollover_cap_qty IS NOT NULL)
        AND rollover_cap_qty > 0
        AND (rollover_kind <> 'NONE' OR rollover_expiry_month_number IS NULL)),
    CONSTRAINT ck_leave_rule_versions_rollover_expiry_month_number CHECK (rollover_expiry_month_number BETWEEN 1 AND 12),
    CONSTRAINT ck_leave_rule_versions_grant_valid_days_count CHECK (grant_valid_days_count >= 1),
    CONSTRAINT ck_leave_rule_versions_basis_ref_text CHECK (basis_ref_text = btrim(basis_ref_text) AND char_length(basis_ref_text) BETWEEN 1 AND 200),
    CONSTRAINT ck_leave_rule_versions_void_columns CHECK (
        (status = 'VOIDED') = (voided_by IS NOT NULL)
        AND (voided_by IS NULL) = (voided_at IS NULL) AND (voided_by IS NULL) = (void_reason_text IS NULL)
        AND (void_reason_text IS NULL OR (void_reason_text = btrim(void_reason_text) AND char_length(void_reason_text) BETWEEN 1 AND 500))),
    CONSTRAINT ex_leave_rule_versions_overlap_entity EXCLUDE USING gist (leave_type_id WITH =, employer_entity_id WITH =, daterange(effective_from_date, effective_to_date, '[)') WITH &&) WHERE (status = 'IN_EFFECT' AND employer_entity_id IS NOT NULL),
    CONSTRAINT ex_leave_rule_versions_overlap_all EXCLUDE USING gist (leave_type_id WITH =, daterange(effective_from_date, effective_to_date, '[)') WITH &&) WHERE (status = 'IN_EFFECT' AND employer_entity_id IS NULL)
);
CREATE UNIQUE INDEX uk_leave_rule_versions_entity_version_number ON organization_leave.leave_rule_versions (leave_type_id, employer_entity_id, version_number) WHERE employer_entity_id IS NOT NULL;
CREATE UNIQUE INDEX uk_leave_rule_versions_all_version_number ON organization_leave.leave_rule_versions (leave_type_id, version_number) WHERE employer_entity_id IS NULL;
CREATE INDEX ix_leave_rule_versions_type_from ON organization_leave.leave_rule_versions (leave_type_id, effective_from_date);

CREATE TABLE organization_leave.leave_entitlement_rules (
    id uuid NOT NULL,
    rule_version_id uuid NOT NULL,
    service_years_from_count integer NOT NULL,
    service_years_to_count integer,
    granted_qty integer NOT NULL,
    proration_kind text NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT pk_leave_entitlement_rules PRIMARY KEY (id),
    CONSTRAINT fk_leave_entitlement_rules_rule_version_id FOREIGN KEY (rule_version_id) REFERENCES organization_leave.leave_rule_versions (id) ON DELETE RESTRICT,
    CONSTRAINT ck_leave_entitlement_rules_service_years_from_count CHECK (service_years_from_count >= 0),
    CONSTRAINT ck_leave_entitlement_rules_service_range CHECK (service_years_to_count IS NULL OR service_years_to_count > service_years_from_count),
    CONSTRAINT ck_leave_entitlement_rules_granted_qty CHECK (granted_qty >= 0),
    CONSTRAINT ck_leave_entitlement_rules_proration_kind CHECK (proration_kind IN ('NONE', 'CALENDAR_DAYS_FLOOR_DAY')),
    CONSTRAINT uk_leave_entitlement_rules_version_from UNIQUE (rule_version_id, service_years_from_count),
    CONSTRAINT ex_leave_entitlement_rules_service_overlap EXCLUDE USING gist (rule_version_id WITH =, int4range(service_years_from_count, service_years_to_count, '[)') WITH &&)
);

CREATE TABLE organization_leave.leave_accounts (
    id uuid NOT NULL,
    employment_relationship_id uuid NOT NULL,
    employee_id uuid NOT NULL,
    leave_type_id uuid NOT NULL,
    leave_year_number integer,
    rule_version_id uuid NOT NULL,
    status text NOT NULL,
    sealed_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT pk_leave_accounts PRIMARY KEY (id),
    CONSTRAINT fk_leave_accounts_leave_type_id FOREIGN KEY (leave_type_id) REFERENCES organization_leave.leave_types (id) ON DELETE RESTRICT,
    CONSTRAINT fk_leave_accounts_rule_version_type FOREIGN KEY (rule_version_id, leave_type_id) REFERENCES organization_leave.leave_rule_versions (id, leave_type_id) ON DELETE RESTRICT,
    CONSTRAINT uk_leave_accounts_relationship_type_year UNIQUE NULLS NOT DISTINCT (employment_relationship_id, leave_type_id, leave_year_number),
    CONSTRAINT uk_leave_accounts_id_relationship_type UNIQUE (id, employment_relationship_id, leave_type_id),
    CONSTRAINT ck_leave_accounts_leave_year_number CHECK (leave_year_number BETWEEN 2000 AND 2999),
    CONSTRAINT ck_leave_accounts_status CHECK (status IN ('OPEN', 'SEALED')),
    CONSTRAINT ck_leave_accounts_sealed_columns CHECK ((status = 'SEALED') = (sealed_at IS NOT NULL) AND (status = 'OPEN' OR leave_year_number IS NOT NULL))
);
CREATE INDEX ix_leave_accounts_leave_type_id ON organization_leave.leave_accounts (leave_type_id);
CREATE INDEX ix_leave_accounts_rule_version_id ON organization_leave.leave_accounts (rule_version_id);
CREATE INDEX ix_leave_accounts_employee_id ON organization_leave.leave_accounts (employee_id);

-- S3 切片 A：政策授予决定，签署记录 leave-attendance-s3；重放键在账户内唯一。
CREATE TABLE organization_leave.leave_policy_grants (
    id uuid NOT NULL,
    request_key text NOT NULL,
    leave_account_id uuid NOT NULL,
    employment_relationship_id uuid NOT NULL,
    leave_type_id uuid NOT NULL,
    grant_qty integer NOT NULL,
    effective_date date NOT NULL,
    basis_text text NOT NULL,
    granted_by uuid NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT pk_leave_policy_grants PRIMARY KEY (id),
    CONSTRAINT uk_leave_policy_grants_account_request_key UNIQUE (leave_account_id, request_key),
    CONSTRAINT fk_leave_policy_grants_account_relationship_type FOREIGN KEY (leave_account_id, employment_relationship_id, leave_type_id) REFERENCES organization_leave.leave_accounts (id, employment_relationship_id, leave_type_id) ON DELETE RESTRICT,
    CONSTRAINT ck_leave_policy_grants_request_key CHECK (request_key = btrim(request_key) AND char_length(request_key) BETWEEN 1 AND 100),
    CONSTRAINT ck_leave_policy_grants_grant_qty CHECK (grant_qty > 0),
    CONSTRAINT ck_leave_policy_grants_effective_date CHECK (isfinite(effective_date)),
    CONSTRAINT ck_leave_policy_grants_basis_text CHECK (basis_text = btrim(basis_text) AND char_length(basis_text) BETWEEN 1 AND 500)
);
CREATE TRIGGER tg_leave_policy_grants_append_only BEFORE UPDATE OR DELETE ON organization_leave.leave_policy_grants
    FOR EACH ROW EXECUTE FUNCTION organization_leave.guard_append_only();
CREATE TRIGGER tg_leave_policy_grants_truncate BEFORE TRUNCATE ON organization_leave.leave_policy_grants
    FOR EACH STATEMENT EXECUTE FUNCTION organization_leave.guard_append_only();

COMMENT ON TABLE organization_leave.leave_policy_grants IS '请假额度的政策授予：一次经确认的额度授予决定，写一条授予流水，只追加，不可改写或删除。粒度：每次授予一行';
COMMENT ON COLUMN organization_leave.leave_policy_grants.id IS '主键（UUIDv7），也是对应 POLICY_GRANT 授予流水的来源 ID';
COMMENT ON COLUMN organization_leave.leave_policy_grants.request_key IS '客户端提供的授予命令重放键，账户内唯一，去首尾空格，1 至 100 字符';
COMMENT ON COLUMN organization_leave.leave_policy_grants.leave_account_id IS '所属额度账户 ID，与雇佣关系、假期类型共同受账户复合外键约束';
COMMENT ON COLUMN organization_leave.leave_policy_grants.employment_relationship_id IS '雇佣关系 ID，额度所属关系，与账户一致，跨上下文不建外键';
COMMENT ON COLUMN organization_leave.leave_policy_grants.leave_type_id IS '假期类型 ID，与账户一致';
COMMENT ON COLUMN organization_leave.leave_policy_grants.grant_qty IS '授予数量，正整数，单位为假期类型的账本单位';
COMMENT ON COLUMN organization_leave.leave_policy_grants.effective_date IS '授予生效日，由操作人声明的有限业务日期';
COMMENT ON COLUMN organization_leave.leave_policy_grants.basis_text IS '操作人填写并确认的授予依据，去首尾空格，1 至 500 字符，不得写健康、孕产等信息';
COMMENT ON COLUMN organization_leave.leave_policy_grants.granted_by IS '做出授予决定的操作人账号 ID，来自 platform.identity，跨上下文不建外键';
COMMENT ON COLUMN organization_leave.leave_policy_grants.created_at IS '创建时刻，带时区时间戳';
COMMENT ON COLUMN organization_leave.leave_policy_grants.updated_at IS '更新时刻（只追加表，触发器拒绝更新，列只为满足通用审计列约定）';

CREATE TABLE organization_leave.leave_ledger_lines (
    id uuid NOT NULL,
    leave_account_id uuid NOT NULL,
    employment_relationship_id uuid NOT NULL,
    leave_type_id uuid NOT NULL,
    line_kind text NOT NULL,
    line_qty integer NOT NULL,
    source_kind text NOT NULL,
    source_id uuid NOT NULL,
    original_leave_year_number integer,
    effective_date date NOT NULL,
    expiry_date date,
    consumes_line_id uuid,
    corrects_line_id uuid,
    adjusted_by uuid,
    reason_text text,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT pk_leave_ledger_lines PRIMARY KEY (id),
    CONSTRAINT fk_leave_ledger_lines_account_relationship_type FOREIGN KEY (leave_account_id, employment_relationship_id, leave_type_id) REFERENCES organization_leave.leave_accounts (id, employment_relationship_id, leave_type_id) ON DELETE RESTRICT,
    CONSTRAINT fk_leave_ledger_lines_consumes_line_id FOREIGN KEY (consumes_line_id) REFERENCES organization_leave.leave_ledger_lines (id) ON DELETE RESTRICT,
    CONSTRAINT fk_leave_ledger_lines_corrects_line_id FOREIGN KEY (corrects_line_id) REFERENCES organization_leave.leave_ledger_lines (id) ON DELETE RESTRICT,
    CONSTRAINT uk_leave_ledger_lines_id_leave_type_id UNIQUE (id, leave_type_id),
    CONSTRAINT uk_leave_ledger_lines_source_key UNIQUE NULLS NOT DISTINCT (leave_account_id, source_kind, source_id, line_kind, consumes_line_id),
    CONSTRAINT ck_leave_ledger_lines_line_kind CHECK (line_kind IN ('GRANT', 'ROLLOVER_IN', 'HOLD', 'RELEASE', 'USE', 'REVERSE_USE', 'ADJUST', 'EXPIRE', 'ROLLOVER_OUT')),
    CONSTRAINT ck_leave_ledger_lines_sign CHECK ((line_kind = 'ADJUST' AND line_qty <> 0) OR (line_kind <> 'ADJUST' AND line_qty >= 0)),
    CONSTRAINT ck_leave_ledger_lines_source_kind CHECK (source_kind IN ('LEAVE_REQUEST', 'OVERTIME_REQUEST', 'HR_ADJUSTMENT', 'SEALED_YEAR_CORRECTION', 'RELATIONSHIP_EVENT', 'YEAR_OPEN', 'YEAR_CLOSE', 'COMP_TIME_EXPIRY', 'MIGRATION', 'POLICY_GRANT')),
    CONSTRAINT ck_leave_ledger_lines_policy_grant_kind CHECK (source_kind <> 'POLICY_GRANT' OR line_kind = 'GRANT'),
    CONSTRAINT ck_leave_ledger_lines_expiry_kind CHECK (expiry_date IS NULL OR (line_kind IN ('GRANT', 'ROLLOVER_IN') AND expiry_date > effective_date)),
    CONSTRAINT ck_leave_ledger_lines_consumes_kind CHECK (consumes_line_id IS NULL OR line_kind IN ('HOLD', 'RELEASE', 'USE', 'REVERSE_USE', 'EXPIRE', 'ADJUST')),
    CONSTRAINT ck_leave_ledger_lines_reason_text CHECK (reason_text IS NULL OR (reason_text = btrim(reason_text) AND char_length(reason_text) BETWEEN 1 AND 500)),
    CONSTRAINT ck_leave_ledger_lines_adjust_columns CHECK (
        (line_kind = 'ADJUST' OR (adjusted_by IS NULL AND reason_text IS NULL AND corrects_line_id IS NULL))
        AND (line_kind <> 'ADJUST' OR adjusted_by IS NOT NULL OR corrects_line_id IS NOT NULL OR original_leave_year_number IS NOT NULL)
        AND (adjusted_by IS NULL) = (reason_text IS NULL))
);
CREATE UNIQUE INDEX uk_leave_ledger_lines_event_key ON organization_leave.leave_ledger_lines (employment_relationship_id, leave_type_id, source_id, original_leave_year_number, line_kind) WHERE original_leave_year_number IS NOT NULL;
CREATE INDEX ix_leave_ledger_lines_account_effective ON organization_leave.leave_ledger_lines (leave_account_id, effective_date, id);
CREATE INDEX ix_leave_ledger_lines_consumes_line_id ON organization_leave.leave_ledger_lines (consumes_line_id);
CREATE INDEX ix_leave_ledger_lines_corrects_line_id ON organization_leave.leave_ledger_lines (corrects_line_id);

CREATE TABLE organization_leave.leave_grant_basis (
    id uuid NOT NULL,
    leave_ledger_line_id uuid NOT NULL,
    relationship_start_date date NOT NULL,
    prior_service_months_count integer NOT NULL,
    relationship_end_date date,
    unpaid_leave_days_count integer NOT NULL,
    employer_entity_id uuid NOT NULL,
    rule_version_id uuid NOT NULL,
    leave_type_id uuid NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT pk_leave_grant_basis PRIMARY KEY (id),
    CONSTRAINT fk_leave_grant_basis_line_type FOREIGN KEY (leave_ledger_line_id, leave_type_id) REFERENCES organization_leave.leave_ledger_lines (id, leave_type_id) ON DELETE RESTRICT,
    CONSTRAINT fk_leave_grant_basis_rule_version_type FOREIGN KEY (rule_version_id, leave_type_id) REFERENCES organization_leave.leave_rule_versions (id, leave_type_id) ON DELETE RESTRICT,
    CONSTRAINT uk_leave_grant_basis_leave_ledger_line_id UNIQUE (leave_ledger_line_id),
    CONSTRAINT ck_leave_grant_basis_prior_service_months_count CHECK (prior_service_months_count >= 0),
    CONSTRAINT ck_leave_grant_basis_unpaid_leave_days_count CHECK (unpaid_leave_days_count >= 0)
);
CREATE INDEX ix_leave_grant_basis_rule_version_id ON organization_leave.leave_grant_basis (rule_version_id);

ALTER TABLE organization_leave.leave_requests ADD CONSTRAINT fk_leave_requests_leave_type_id FOREIGN KEY (leave_type_id) REFERENCES organization_leave.leave_types (id) ON DELETE RESTRICT;
ALTER TABLE organization_leave.monthly_confirmation_lines ADD CONSTRAINT fk_monthly_confirmation_lines_leave_type_id FOREIGN KEY (leave_type_id) REFERENCES organization_leave.leave_types (id) ON DELETE RESTRICT;
ALTER TABLE organization_leave.leave_request_holds ADD CONSTRAINT fk_leave_request_holds_ledger_line_id FOREIGN KEY (ledger_line_id) REFERENCES organization_leave.leave_ledger_lines (id) ON DELETE RESTRICT;
CREATE INDEX ix_leave_requests_leave_type_id ON organization_leave.leave_requests (leave_type_id);
CREATE INDEX ix_monthly_confirmation_lines_leave_type_id ON organization_leave.monthly_confirmation_lines (leave_type_id);
-- leave_request_holds 的 uk_leave_request_holds_ledger_line_id 已是以 ledger_line_id 开头的索引。

CREATE FUNCTION organization_leave.guard_leave_type_change() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        IF (to_jsonb(NEW) - 'display_name') = (to_jsonb(OLD) - 'display_name') THEN RETURN NEW; END IF;
    END IF;
    RAISE EXCEPTION 'tg_leave_types_change: only display_name may change' USING ERRCODE = 'restrict_violation', CONSTRAINT = TG_NAME;
END $$;
CREATE TRIGGER tg_leave_types_change BEFORE UPDATE OR DELETE ON organization_leave.leave_types
    FOR EACH ROW EXECUTE FUNCTION organization_leave.guard_leave_type_change();
CREATE TRIGGER tg_leave_types_truncate BEFORE TRUNCATE ON organization_leave.leave_types
    FOR EACH STATEMENT EXECUTE FUNCTION organization_leave.guard_leave_type_change();

CREATE FUNCTION organization_leave.guard_leave_rule_version_insert() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
DECLARE
    latest integer;
    latest_from date;
    today date := (transaction_timestamp() AT TIME ZONE 'Asia/Shanghai')::date;
BEGIN
    IF NEW.status <> 'IN_EFFECT' OR NEW.effective_to_date IS NOT NULL THEN
        RAISE EXCEPTION 'tg_leave_rule_versions_insert: a new version is IN_EFFECT and open' USING ERRCODE = 'check_violation', CONSTRAINT = TG_NAME;
    END IF;
    SELECT max(version_number) INTO latest FROM organization_leave.leave_rule_versions
        WHERE leave_type_id = NEW.leave_type_id AND employer_entity_id IS NOT DISTINCT FROM NEW.employer_entity_id;
    IF NEW.version_number <> coalesce(latest, 0) + 1 THEN
        RAISE EXCEPTION 'tg_leave_rule_versions_insert: version_number must follow the scope latest, including voided versions' USING ERRCODE = 'check_violation', CONSTRAINT = TG_NAME;
    END IF;
    SELECT max(effective_from_date) INTO latest_from FROM organization_leave.leave_rule_versions
        WHERE leave_type_id = NEW.leave_type_id AND employer_entity_id IS NOT DISTINCT FROM NEW.employer_entity_id AND status = 'IN_EFFECT';
    IF latest_from IS NOT NULL AND NEW.effective_from_date <= latest_from THEN
        RAISE EXCEPTION 'tg_leave_rule_versions_insert: effective_from_date must be later than the latest non-voided version' USING ERRCODE = 'check_violation', CONSTRAINT = TG_NAME;
    END IF;
    IF NEW.effective_from_date < today THEN
        RAISE EXCEPTION 'tg_leave_rule_versions_insert: no backdating, including first and all-voided scopes' USING ERRCODE = 'check_violation', CONSTRAINT = TG_NAME;
    END IF;
    UPDATE organization_leave.leave_rule_versions SET effective_to_date = NEW.effective_from_date
        WHERE leave_type_id = NEW.leave_type_id AND employer_entity_id IS NOT DISTINCT FROM NEW.employer_entity_id AND status = 'IN_EFFECT' AND effective_to_date IS NULL;
    -- As for standard hours, track inserted ids, not xmin (closing/reopening rewrites old rows).
    PERFORM set_config('forcitis.leave_new_versions', coalesce(current_setting('forcitis.leave_new_versions', true), '') || NEW.id::text || ',', true);
    RETURN NEW;
END $$;
CREATE FUNCTION organization_leave.guard_leave_rule_version_change() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
DECLARE
    void_columns text[] := ARRAY['status', 'voided_by', 'voided_at', 'void_reason_text'];
    today date := (transaction_timestamp() AT TIME ZONE 'Asia/Shanghai')::date;
BEGIN
    IF TG_OP = 'UPDATE' THEN
        IF pg_trigger_depth() > 1 AND OLD.status = 'IN_EFFECT' AND NEW.status = 'IN_EFFECT'
                AND (to_jsonb(NEW) - 'effective_to_date') = (to_jsonb(OLD) - 'effective_to_date')
                AND ((OLD.effective_to_date IS NULL AND NEW.effective_to_date IS NOT NULL) OR (OLD.effective_to_date IS NOT NULL AND NEW.effective_to_date IS NULL)) THEN
            RETURN NEW;
        END IF;
        IF OLD.status = 'IN_EFFECT' AND NEW.status = 'VOIDED' AND (to_jsonb(NEW) - void_columns) = (to_jsonb(OLD) - void_columns) THEN
            IF OLD.effective_from_date <= today THEN
                RAISE EXCEPTION 'tg_leave_rule_versions_change: only a future version can be voided' USING ERRCODE = 'check_violation', CONSTRAINT = TG_NAME;
            END IF;
            IF EXISTS (SELECT 1 FROM organization_leave.leave_rule_versions later WHERE later.leave_type_id = OLD.leave_type_id
                    AND later.employer_entity_id IS NOT DISTINCT FROM OLD.employer_entity_id AND later.status = 'IN_EFFECT' AND later.effective_from_date > OLD.effective_from_date) THEN
                RAISE EXCEPTION 'tg_leave_rule_versions_change: only the latest version can be voided' USING ERRCODE = 'check_violation', CONSTRAINT = TG_NAME;
            END IF;
            RETURN NEW;
        END IF;
    END IF;
    RAISE EXCEPTION 'tg_leave_rule_versions_change: versions only close, reopen or void once' USING ERRCODE = 'restrict_violation', CONSTRAINT = TG_NAME;
END $$;
CREATE FUNCTION organization_leave.reopen_leave_rule_predecessor() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
BEGIN
    UPDATE organization_leave.leave_rule_versions SET effective_to_date = NULL WHERE leave_type_id = OLD.leave_type_id
        AND employer_entity_id IS NOT DISTINCT FROM OLD.employer_entity_id AND status = 'IN_EFFECT' AND effective_to_date = OLD.effective_from_date;
    RETURN NULL;
END $$;
CREATE TRIGGER tg_leave_rule_versions_insert BEFORE INSERT ON organization_leave.leave_rule_versions
    FOR EACH ROW EXECUTE FUNCTION organization_leave.guard_leave_rule_version_insert();
CREATE TRIGGER tg_leave_rule_versions_change BEFORE UPDATE OR DELETE ON organization_leave.leave_rule_versions
    FOR EACH ROW EXECUTE FUNCTION organization_leave.guard_leave_rule_version_change();
CREATE TRIGGER tg_leave_rule_versions_void_reopens AFTER UPDATE ON organization_leave.leave_rule_versions
    FOR EACH ROW WHEN (OLD.status = 'IN_EFFECT' AND NEW.status = 'VOIDED') EXECUTE FUNCTION organization_leave.reopen_leave_rule_predecessor();
CREATE TRIGGER tg_leave_rule_versions_truncate BEFORE TRUNCATE ON organization_leave.leave_rule_versions
    FOR EACH STATEMENT EXECUTE FUNCTION organization_leave.guard_leave_rule_version_change();

CREATE FUNCTION organization_leave.guard_leave_entitlement_change() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF NOT EXISTS (SELECT 1 FROM organization_leave.leave_rule_versions WHERE id = NEW.rule_version_id) THEN
            RAISE EXCEPTION 'fk_leave_entitlement_rules_rule_version_id: missing version' USING ERRCODE = 'foreign_key_violation', CONSTRAINT = 'fk_leave_entitlement_rules_rule_version_id';
        END IF;
        IF position(NEW.rule_version_id::text || ',' IN coalesce(current_setting('forcitis.leave_new_versions', true), '')) > 0 THEN RETURN NEW; END IF;
    END IF;
    RAISE EXCEPTION 'tg_leave_entitlement_rules_change: insert only in the version transaction; append-only' USING ERRCODE = 'restrict_violation', CONSTRAINT = TG_NAME;
END $$;
CREATE TRIGGER tg_leave_entitlement_rules_change BEFORE INSERT OR UPDATE OR DELETE ON organization_leave.leave_entitlement_rules
    FOR EACH ROW EXECUTE FUNCTION organization_leave.guard_leave_entitlement_change();
CREATE TRIGGER tg_leave_entitlement_rules_truncate BEFORE TRUNCATE ON organization_leave.leave_entitlement_rules
    FOR EACH STATEMENT EXECUTE FUNCTION organization_leave.guard_leave_entitlement_change();

CREATE FUNCTION organization_leave.guard_leave_account_change() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF NEW.status = 'OPEN' THEN RETURN NEW; END IF;
    END IF;
    IF TG_OP = 'UPDATE' THEN
        IF OLD.status = 'OPEN' AND NEW.status = 'SEALED' AND NEW.sealed_at IS NOT NULL
                AND (to_jsonb(NEW) - ARRAY['status', 'sealed_at', 'updated_at']) = (to_jsonb(OLD) - ARRAY['status', 'sealed_at', 'updated_at']) THEN
            RETURN NEW;
        END IF;
    END IF;
    RAISE EXCEPTION 'tg_leave_accounts_change: insert OPEN, then seal once; no other changes' USING ERRCODE = 'restrict_violation', CONSTRAINT = TG_NAME;
END $$;
CREATE TRIGGER tg_leave_accounts_change BEFORE INSERT OR UPDATE OR DELETE ON organization_leave.leave_accounts
    FOR EACH ROW EXECUTE FUNCTION organization_leave.guard_leave_account_change();
CREATE TRIGGER tg_leave_accounts_truncate BEFORE TRUNCATE ON organization_leave.leave_accounts
    FOR EACH STATEMENT EXECUTE FUNCTION organization_leave.guard_leave_account_change();

CREATE FUNCTION organization_leave.guard_leave_ledger_line() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
DECLARE
    account_status text;
    account_year integer;
BEGIN
    IF TG_OP <> 'INSERT' THEN
        RAISE EXCEPTION 'tg_leave_ledger_lines_guard: ledger is append-only' USING ERRCODE = 'restrict_violation', CONSTRAINT = TG_NAME;
    END IF;
    SELECT status, leave_year_number INTO account_status, account_year FROM organization_leave.leave_accounts WHERE id = NEW.leave_account_id FOR SHARE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'fk_leave_ledger_lines_account_relationship_type: missing account' USING ERRCODE = 'foreign_key_violation', CONSTRAINT = 'fk_leave_ledger_lines_account_relationship_type';
    END IF;
    IF account_status = 'SEALED' THEN
        RAISE EXCEPTION 'tg_leave_ledger_lines_guard: SEALED account takes no line' USING ERRCODE = 'restrict_violation', CONSTRAINT = TG_NAME;
    END IF;
    IF NEW.consumes_line_id IS NOT NULL THEN
        IF NOT EXISTS (SELECT 1 FROM organization_leave.leave_ledger_lines WHERE id = NEW.consumes_line_id) THEN
            RAISE EXCEPTION 'fk_leave_ledger_lines_consumes_line_id: missing lot' USING ERRCODE = 'foreign_key_violation', CONSTRAINT = 'fk_leave_ledger_lines_consumes_line_id';
        END IF;
        IF account_year IS NOT NULL OR NOT EXISTS (SELECT 1 FROM organization_leave.leave_ledger_lines
                WHERE id = NEW.consumes_line_id AND leave_account_id = NEW.leave_account_id AND line_kind IN ('GRANT', 'ROLLOVER_IN')) THEN
            RAISE EXCEPTION 'tg_leave_ledger_lines_guard: consumes_line_id must name a GRANT or ROLLOVER_IN lot of the same rolling account' USING ERRCODE = 'check_violation', CONSTRAINT = TG_NAME;
        END IF;
    ELSIF account_year IS NULL AND NEW.line_kind IN ('HOLD', 'RELEASE', 'USE', 'REVERSE_USE', 'EXPIRE', 'ADJUST') THEN
        RAISE EXCEPTION 'tg_leave_ledger_lines_guard: rolling account requires a lot' USING ERRCODE = 'check_violation', CONSTRAINT = TG_NAME;
    END IF;
    IF NEW.created_at IS DISTINCT FROM NEW.updated_at THEN
        RAISE EXCEPTION 'tg_leave_ledger_lines_guard: audit times must be equal on insert' USING ERRCODE = 'check_violation', CONSTRAINT = TG_NAME;
    END IF;
    PERFORM set_config('forcitis.leave_new_lines', coalesce(current_setting('forcitis.leave_new_lines', true), '') || NEW.id::text || ',', true);
    RETURN NEW;
END $$;
CREATE TRIGGER tg_leave_ledger_lines_guard BEFORE INSERT OR UPDATE OR DELETE ON organization_leave.leave_ledger_lines
    FOR EACH ROW EXECUTE FUNCTION organization_leave.guard_leave_ledger_line();
CREATE TRIGGER tg_leave_ledger_lines_truncate BEFORE TRUNCATE ON organization_leave.leave_ledger_lines
    FOR EACH STATEMENT EXECUTE FUNCTION organization_leave.guard_leave_ledger_line();

CREATE FUNCTION organization_leave.guard_leave_grant_basis_change() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
DECLARE
    parent_kind text;
BEGIN
    IF TG_OP = 'INSERT' THEN
        SELECT line_kind INTO parent_kind FROM organization_leave.leave_ledger_lines WHERE id = NEW.leave_ledger_line_id;
        IF NOT FOUND THEN
            RAISE EXCEPTION 'fk_leave_grant_basis_line_type: missing line' USING ERRCODE = 'foreign_key_violation', CONSTRAINT = 'fk_leave_grant_basis_line_type';
        END IF;
        IF parent_kind NOT IN ('GRANT', 'ADJUST') THEN
            RAISE EXCEPTION 'tg_leave_grant_basis_change: basis requires a GRANT or ADJUST line' USING ERRCODE = 'check_violation', CONSTRAINT = TG_NAME;
        END IF;
        IF position(NEW.leave_ledger_line_id::text || ',' IN coalesce(current_setting('forcitis.leave_new_lines', true), '')) > 0 THEN RETURN NEW; END IF;
    END IF;
    RAISE EXCEPTION 'tg_leave_grant_basis_change: insert only in the line transaction; append-only' USING ERRCODE = 'restrict_violation', CONSTRAINT = TG_NAME;
END $$;
CREATE TRIGGER tg_leave_grant_basis_change BEFORE INSERT OR UPDATE OR DELETE ON organization_leave.leave_grant_basis
    FOR EACH ROW EXECUTE FUNCTION organization_leave.guard_leave_grant_basis_change();
CREATE TRIGGER tg_leave_grant_basis_truncate BEFORE TRUNCATE ON organization_leave.leave_grant_basis
    FOR EACH STATEMENT EXECUTE FUNCTION organization_leave.guard_leave_grant_basis_change();


COMMENT ON TABLE organization_leave.leave_types IS '假期类型。粒度：每种假期一行，只由迁移写入';
COMMENT ON COLUMN organization_leave.leave_types.id IS '主键（UUIDv7）';
COMMENT ON COLUMN organization_leave.leave_types.code IS '假期类型代码，2 至 32 位大写字母、数字或下划线，首位大写字母';
COMMENT ON COLUMN organization_leave.leave_types.display_name IS '假期显示名称，去首尾空格，1 至 60 字符';
COMMENT ON COLUMN organization_leave.leave_types.ledger_unit_kind IS '账本单位：MINUTE 分钟、DAY 百分之一天（5 天存 500）';
COMMENT ON COLUMN organization_leave.leave_types.is_paid IS '是否带薪，布尔值';
COMMENT ON COLUMN organization_leave.leave_types.is_quota_counted IS '是否计额度，false 表示只有请假申请、不设账户';
COMMENT ON COLUMN organization_leave.leave_types.created_at IS '创建时刻，带时区时间戳';
COMMENT ON COLUMN organization_leave.leave_types.updated_at IS '更新时刻，带时区时间戳';

COMMENT ON TABLE organization_leave.leave_rule_versions IS '假期规则版本。粒度：每个假期类型每个适用范围每个生效区间一行，只追加';
COMMENT ON COLUMN organization_leave.leave_rule_versions.id IS '主键（UUIDv7）';
COMMENT ON COLUMN organization_leave.leave_rule_versions.leave_type_id IS '假期类型 ID';
COMMENT ON COLUMN organization_leave.leave_rule_versions.scope_kind IS '适用范围：ALL 所有用人单位、EMPLOYER_ENTITY 指定用人单位';
COMMENT ON COLUMN organization_leave.leave_rule_versions.employer_entity_id IS '用人单位 ID；为空表示规则适用于所有用人单位';
COMMENT ON COLUMN organization_leave.leave_rule_versions.version_number IS '同类型同范围版本号，从 1 逐个加 1，作废仍占号';
COMMENT ON COLUMN organization_leave.leave_rule_versions.status IS '版本状态：IN_EFFECT 有效、VOIDED 已作废';
COMMENT ON COLUMN organization_leave.leave_rule_versions.effective_from_date IS '生效日期（含当天），不得早于上海时区的数据库事务日期';
COMMENT ON COLUMN organization_leave.leave_rule_versions.effective_to_date IS '失效日期（不含当天）；为空表示没有后继版本，一直有效';
COMMENT ON COLUMN organization_leave.leave_rule_versions.request_unit_kind IS '最小申请步长：MINUTE 分钟、HALF_DAY 半天、DAY 一天';
COMMENT ON COLUMN organization_leave.leave_rule_versions.is_negative_allowed IS '是否允许负余额，布尔值';
COMMENT ON COLUMN organization_leave.leave_rule_versions.exempt_threshold_minutes_count IS '免审批时长阈值，单位分钟，正数；为空表示没有免审批时长';
COMMENT ON COLUMN organization_leave.leave_rule_versions.is_proof_required IS '是否要求证明，布尔值';
COMMENT ON COLUMN organization_leave.leave_rule_versions.rollover_kind IS '结转方式：NONE 不结转、CAPPED 限额、UNLIMITED 不限额';
COMMENT ON COLUMN organization_leave.leave_rule_versions.rollover_cap_qty IS '结转上限，单位为类型的账本单位（分钟或百分之一天）；为空表示不设结转上限（结转方式不是限额）';
COMMENT ON COLUMN organization_leave.leave_rule_versions.rollover_expiry_month_number IS '结转额度到期月份，1 至 12，新年度该月末到期；为空表示结转的额度不过期';
COMMENT ON COLUMN organization_leave.leave_rule_versions.grant_valid_days_count IS '批次有效天数，至少 1 天，发放日期加此天数为到期日；为空表示发放的额度（调休）不过期';
COMMENT ON COLUMN organization_leave.leave_rule_versions.basis_ref_text IS '规则取值依据引用，去首尾空格，1 至 200 字符，仅引用';
COMMENT ON COLUMN organization_leave.leave_rule_versions.published_by IS '发布人账号 ID';
COMMENT ON COLUMN organization_leave.leave_rule_versions.published_at IS '发布时刻，带时区时间戳';
COMMENT ON COLUMN organization_leave.leave_rule_versions.decision_audit_entry_id IS '决定审计记录 ID，发布依据，跨上下文不建外键';
COMMENT ON COLUMN organization_leave.leave_rule_versions.voided_by IS '作废人账号 ID；为空表示版本未作废';
COMMENT ON COLUMN organization_leave.leave_rule_versions.voided_at IS '作废时刻；为空表示版本未作废';
COMMENT ON COLUMN organization_leave.leave_rule_versions.void_reason_text IS '作废原因，去首尾空格，1 至 500 字符；为空表示版本未作废';
COMMENT ON COLUMN organization_leave.leave_rule_versions.created_at IS '创建时刻，带时区时间戳';
COMMENT ON COLUMN organization_leave.leave_rule_versions.updated_at IS '更新时刻，带时区时间戳';

COMMENT ON TABLE organization_leave.leave_entitlement_rules IS '假期规则的额度档位。粒度：每个规则版本每个工龄档位一行';
COMMENT ON COLUMN organization_leave.leave_entitlement_rules.id IS '主键（UUIDv7）';
COMMENT ON COLUMN organization_leave.leave_entitlement_rules.rule_version_id IS '规则版本 ID，只在创建版本的事务写入';
COMMENT ON COLUMN organization_leave.leave_entitlement_rules.service_years_from_count IS '工龄下限（含），单位整年，按假期年度 1 月 1 日累计工龄计算';
COMMENT ON COLUMN organization_leave.leave_entitlement_rules.service_years_to_count IS '工龄上限（不含），单位整年；为空表示工龄没有上限';
COMMENT ON COLUMN organization_leave.leave_entitlement_rules.granted_qty IS '全年额度，非负，单位为类型的账本单位（分钟或百分之一天）';
COMMENT ON COLUMN organization_leave.leave_entitlement_rules.proration_kind IS '折算方式：NONE 不折算、CALENDAR_DAYS_FLOOR_DAY 按剩余日历天除以 365 后向下取整至全天';
COMMENT ON COLUMN organization_leave.leave_entitlement_rules.created_at IS '创建时刻，带时区时间戳';
COMMENT ON COLUMN organization_leave.leave_entitlement_rules.updated_at IS '更新时刻，带时区时间戳';

COMMENT ON TABLE organization_leave.leave_accounts IS '假期额度账户。粒度：每个雇佣关系每个假期类型每个假期年度一行；无年度的为滚动账户';
COMMENT ON COLUMN organization_leave.leave_accounts.id IS '主键（UUIDv7）';
COMMENT ON COLUMN organization_leave.leave_accounts.employment_relationship_id IS '雇佣关系 ID，跨上下文不建外键';
COMMENT ON COLUMN organization_leave.leave_accounts.employee_id IS '员工 ID，用于数据范围判断与本人余额查询，跨上下文不建外键';
COMMENT ON COLUMN organization_leave.leave_accounts.leave_type_id IS '账户假期类型 ID';
COMMENT ON COLUMN organization_leave.leave_accounts.leave_year_number IS '假期年度，2000 至 2999；为空表示滚动账户，无年度';
COMMENT ON COLUMN organization_leave.leave_accounts.rule_version_id IS '规则版本 ID，开户日有效，封存后仍保留';
COMMENT ON COLUMN organization_leave.leave_accounts.status IS '账户状态：OPEN 开放、SEALED 年度已封存，滚动账户不封存';
COMMENT ON COLUMN organization_leave.leave_accounts.sealed_at IS '年度封存时刻；为空表示该年度尚未封存';
COMMENT ON COLUMN organization_leave.leave_accounts.created_at IS '创建时刻，带时区时间戳';
COMMENT ON COLUMN organization_leave.leave_accounts.updated_at IS '更新时刻，带时区时间戳';

COMMENT ON TABLE organization_leave.leave_ledger_lines IS '额度流水，只追加，余额由它推出。粒度：每个账户每次额度变动一行';
COMMENT ON COLUMN organization_leave.leave_ledger_lines.id IS '主键（UUIDv7）';
COMMENT ON COLUMN organization_leave.leave_ledger_lines.leave_account_id IS '所属额度账户 ID';
COMMENT ON COLUMN organization_leave.leave_ledger_lines.employment_relationship_id IS '雇佣关系 ID，与账户一致，事件幂等键使用';
COMMENT ON COLUMN organization_leave.leave_ledger_lines.leave_type_id IS '假期类型 ID，与账户一致，事件幂等键使用';
COMMENT ON COLUMN organization_leave.leave_ledger_lines.line_kind IS '流水类型：GRANT 发放、ROLLOVER_IN 转入、HOLD 占用、RELEASE 释放、USE 使用、REVERSE_USE 退回、ADJUST 调整、EXPIRE 到期、ROLLOVER_OUT 转出';
COMMENT ON COLUMN organization_leave.leave_ledger_lines.line_qty IS '流水数量，单位为类型的账本单位（分钟或百分之一天）；ADJUST 有符号且非零，其余非负';
COMMENT ON COLUMN organization_leave.leave_ledger_lines.source_kind IS '来源类型：请假、加班、人事调整、封存年度更正、关系事件、开年、结年、调休到期、迁移、政策授予';
COMMENT ON COLUMN organization_leave.leave_ledger_lines.source_id IS '来源申请、事件、审批或命令 ID';
COMMENT ON COLUMN organization_leave.leave_ledger_lines.original_leave_year_number IS '关系事件原属年度，跨封存重投仍用同年；为空表示该行不是由关系事件按年度触发';
COMMENT ON COLUMN organization_leave.leave_ledger_lines.effective_date IS '流水发生日期';
COMMENT ON COLUMN organization_leave.leave_ledger_lines.expiry_date IS '批次到期日期，晚于发生日，仅 GRANT、ROLLOVER_IN 使用；为空表示不过期（不是批次，或批次不过期）';
COMMENT ON COLUMN organization_leave.leave_ledger_lines.consumes_line_id IS '被扣减的 GRANT 或 ROLLOVER_IN 批次流水 ID；为空表示不是从某个批次扣减';
COMMENT ON COLUMN organization_leave.leave_ledger_lines.corrects_line_id IS '被更正的原流水 ID，仅 ADJUST 使用；为空表示不是对已封存年度某行的更正';
COMMENT ON COLUMN organization_leave.leave_ledger_lines.adjusted_by IS '手工调整人账号 ID，仅 ADJUST 使用；为空表示不是人事手工调整';
COMMENT ON COLUMN organization_leave.leave_ledger_lines.reason_text IS '余额更正原因，去首尾空格，1 至 500 字符，不得写病情或个人隐私；为空表示不是人事手工调整';
COMMENT ON COLUMN organization_leave.leave_ledger_lines.created_at IS '创建时刻，带时区时间戳';
COMMENT ON COLUMN organization_leave.leave_ledger_lines.updated_at IS '更新时刻，带时区时间戳，插入时等于创建时刻且永不更改';

COMMENT ON TABLE organization_leave.leave_grant_basis IS '额度发放或调整所依据的输入。粒度：每条发放或调整流水一行';
COMMENT ON COLUMN organization_leave.leave_grant_basis.id IS '主键（UUIDv7）';
COMMENT ON COLUMN organization_leave.leave_grant_basis.leave_ledger_line_id IS '发放或调整流水 ID，每条流水最多一个依据';
COMMENT ON COLUMN organization_leave.leave_grant_basis.relationship_start_date IS '计算时从雇佣关系 API 读取的关系开始日期';
COMMENT ON COLUMN organization_leave.leave_grant_basis.prior_service_months_count IS '本关系之前的累计工龄，单位月，非负';
COMMENT ON COLUMN organization_leave.leave_grant_basis.relationship_end_date IS '计算时的关系结束日期；为空表示仍在职';
COMMENT ON COLUMN organization_leave.leave_grant_basis.unpaid_leave_days_count IS '计算年度内停薪留职日历天数，单位天，非负';
COMMENT ON COLUMN organization_leave.leave_grant_basis.employer_entity_id IS '计算时的用人单位 ID，不能确定时失败，跨上下文不建外键';
COMMENT ON COLUMN organization_leave.leave_grant_basis.rule_version_id IS '规则版本 ID，本次计算使用';
COMMENT ON COLUMN organization_leave.leave_grant_basis.leave_type_id IS '流水与规则版本共有的假期类型 ID';
COMMENT ON COLUMN organization_leave.leave_grant_basis.created_at IS '创建时刻，带时区时间戳';
COMMENT ON COLUMN organization_leave.leave_grant_basis.updated_at IS '更新时刻，带时区时间戳';
