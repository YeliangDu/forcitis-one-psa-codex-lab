-- 请假与考勤事实 L1a：#1400，逐表依据签署的数据设计 organization-leave-l1a。
CREATE SCHEMA IF NOT EXISTS organization_leave;
COMMENT ON SCHEMA organization_leave IS '请假与考勤事实；L1a 七表，无业务行为';

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
    CONSTRAINT ex_leave_requests_live_period_overlap EXCLUDE USING gist (employment_relationship_id WITH =, tstzrange(start_at, end_at) WITH &&) WHERE (status IN ('SUBMITTED', 'IN_APPROVAL', 'APPROVED'))
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
COMMENT ON COLUMN organization_leave.leave_requests.leave_type_id IS '请假类型 ID，L1b 建表后补外键';
COMMENT ON COLUMN organization_leave.leave_requests.start_at IS '请假起始时刻（含）';
COMMENT ON COLUMN organization_leave.leave_requests.end_at IS '请假结束时刻（不含）';
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
COMMENT ON COLUMN organization_leave.leave_request_holds.ledger_line_id IS '被占用的 HOLD 额度流水 ID，L1b 建表后补外键';
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
COMMENT ON COLUMN organization_leave.monthly_confirmation_lines.leave_type_id IS '请假类型 ID，L1b 建表后补外键；为空表示不是请假行';
COMMENT ON COLUMN organization_leave.monthly_confirmation_lines.is_paid_leave IS '带薪请假标志，生成时复制；为空表示不是请假行';
COMMENT ON COLUMN organization_leave.monthly_confirmation_lines.compensation_kind IS '加班补偿类型：PAY 加班工资、COMP_TIME 调休；为空表示不是加班行';
COMMENT ON COLUMN organization_leave.monthly_confirmation_lines.day_kind IS '日类型：WORKDAY 工作日、REST_DAY 休息日、STATUTORY_HOLIDAY 法定节假日；为空表示不是加班行';
COMMENT ON COLUMN organization_leave.monthly_confirmation_lines.minutes_count IS '时长（分钟），非负；为空表示这是按天计的出勤行';
COMMENT ON COLUMN organization_leave.monthly_confirmation_lines.day_hundredths_count IS '百分之一天数，非负；为空表示这是按分钟计的行';
COMMENT ON COLUMN organization_leave.monthly_confirmation_lines.original_month_start_date IS '原属月份首日，被更正月份；为空表示不是跨月更正行';
COMMENT ON COLUMN organization_leave.monthly_confirmation_lines.created_at IS '创建时刻';
COMMENT ON COLUMN organization_leave.monthly_confirmation_lines.updated_at IS '更新时刻';

