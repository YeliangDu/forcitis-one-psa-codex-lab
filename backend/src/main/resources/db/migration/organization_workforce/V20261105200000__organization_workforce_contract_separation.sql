-- #1438（WC1，劳动合同与离职申请三表，数据设计签字记录 docs/data/reviews/workforce-contract-separation-wc0.md）。
-- 只建结构与 separation_bases 种子；没有合同行也没有申请行（旧系统导入是 WC5）。迁移基线开放期内，工作队的新表以新迁移加入（V20261105120000 先例）。
-- 回滚：三表为空时删三表与 personnel_commands 上新加的唯一约束；有数据之后另起迁移。

-- 1. 离职依据清单：配置表，行只经迁移进出。
CREATE TABLE organization_workforce.separation_bases (
    separation_basis_key   text        NOT NULL,
    display_name           text        NOT NULL,
    default_initiator_kind text        NOT NULL,
    is_employer_initiated  boolean     NOT NULL,
    is_system_only         boolean     NOT NULL,
    is_active              boolean     NOT NULL,
    created_at             timestamptz NOT NULL,
    updated_at             timestamptz NOT NULL,
    CONSTRAINT pk_separation_bases PRIMARY KEY (separation_basis_key),
    CONSTRAINT ck_separation_bases_key_format CHECK (separation_basis_key ~ '^[A-Z][A-Z0-9_]{1,39}$'),
    CONSTRAINT ck_separation_bases_display_name CHECK (char_length(display_name) BETWEEN 1 AND 60 AND btrim(display_name) <> ''),
    CONSTRAINT ck_separation_bases_default_initiator_kind CHECK (default_initiator_kind IN ('EMPLOYEE', 'EMPLOYER', 'BOTH')),
    CONSTRAINT ck_separation_bases_system_only_not_employer CHECK (NOT (is_system_only AND is_employer_initiated))
);
COMMENT ON TABLE organization_workforce.separation_bases IS '离职依据清单：离职申请与合同终止所选的法定依据键；配置表，行只经迁移进出，退役的键留着供旧行引用；不含个人信息；规则：任何键不得表达医疗原因或暴露健康的法条子项（第 40 条只有一个键）';
COMMENT ON COLUMN organization_workforce.separation_bases.separation_basis_key IS '依据键（大写字母、数字、下划线）';
COMMENT ON COLUMN organization_workforce.separation_bases.display_name IS '中文显示名，1 至 60 字';
COMMENT ON COLUMN organization_workforce.separation_bases.default_initiator_kind IS '表单预选的发起方：EMPLOYEE 员工 / EMPLOYER 用人单位 / BOTH 双方';
COMMENT ON COLUMN organization_workforce.separation_bases.is_employer_initiated IS '是否用人单位单方解除：为真时批准前必须有四项解除核查（禁止解除情形核查日、通知工会日、工会意见、书面通知结果）';
COMMENT ON COLUMN organization_workforce.separation_bases.is_system_only IS '是否仅系统使用：为真的键（换用人单位、迁入）人事不能选，只能由系统申请使用';
COMMENT ON COLUMN organization_workforce.separation_bases.is_active IS '是否可选：退役的键置假，旧行继续引用';
COMMENT ON COLUMN organization_workforce.separation_bases.created_at IS '创建时间';
COMMENT ON COLUMN organization_workforce.separation_bases.updated_at IS '最近变更时间；行只经迁移变更';

-- 值按用户 2026-11-05 的 Q-1 (a)：法条依据；待法务与旧表核对，只差异找用户。第 40 条合并为一个键；任何键不暴露健康原因。
INSERT INTO organization_workforce.separation_bases (separation_basis_key, display_name, default_initiator_kind, is_employer_initiated, is_system_only, is_active, created_at, updated_at) VALUES
    ('RESIGN_WITH_NOTICE',        '提前通知辞职（第 37 条）',            'EMPLOYEE', false, false, true, now(), now()),
    ('RESIGN_IN_PROBATION',       '试用期辞职（第 37 条）',              'EMPLOYEE', false, false, true, now(), now()),
    ('EMPLOYER_FAULT_RESIGNATION', '因单位过错劳动者解除（第 38 条）',    'EMPLOYEE', false, false, true, now(), now()),
    ('MUTUAL_AGREEMENT',          '协商一致解除（第 36 条）',            'BOTH',     false, false, true, now(), now()),
    ('FAULT_DISMISSAL',           '过错性解除（第 39 条）',              'EMPLOYER', true,  false, true, now(), now()),
    ('NO_FAULT_DISMISSAL',        '无过失性解除（第 40 条）',            'EMPLOYER', true,  false, true, now(), now()),
    ('ECONOMIC_LAYOFF',           '经济性裁员（第 41 条）',              'EMPLOYER', true,  false, true, now(), now()),
    ('CONTRACT_EXPIRY',           '合同期满终止（第 44 条）',            'BOTH',     false, false, true, now(), now()),
    ('DEATH_OR_DISSOLUTION',      '劳动者死亡或单位解散（第 44 条）',     'BOTH',     false, false, true, now(), now()),
    ('RETIREMENT',                '退休',                                'EMPLOYEE', false, false, true, now(), now()),
    ('OTHER_STATUTORY',           '其他法定情形',                        'BOTH',     false, false, true, now(), now()),
    ('EMPLOYER_CHANGE',           '换用人单位（系统）',                  'BOTH',     false, true,  true, now(), now()),
    ('MIGRATION',                 '迁入（系统）',                        'BOTH',     false, true,  true, now(), now());

CREATE FUNCTION organization_workforce.guard_separation_basis_change() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
DECLARE
    owner_name name;
BEGIN
    IF TG_OP = 'TRUNCATE' THEN
        RAISE EXCEPTION 'separation bases are configuration: truncate on %.% is refused', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
    END IF;
    SELECT pg_get_userbyid(relowner) INTO owner_name FROM pg_class WHERE oid = TG_RELID;
    IF current_user <> owner_name THEN
        RAISE EXCEPTION 'separation bases are configuration: % on %.% is refused, rows change by migration only', lower(TG_OP), TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN CASE TG_OP WHEN 'DELETE' THEN OLD ELSE NEW END;
END $$;
COMMENT ON FUNCTION organization_workforce.guard_separation_basis_change() IS '离职依据清单的触发器：UPDATE、DELETE 只放行表属主（迁移），TRUNCATE 拒绝';
CREATE TRIGGER tg_separation_bases_guard BEFORE UPDATE OR DELETE ON organization_workforce.separation_bases
    FOR EACH ROW EXECUTE FUNCTION organization_workforce.guard_separation_basis_change();
CREATE TRIGGER tg_separation_bases_truncate BEFORE TRUNCATE ON organization_workforce.separation_bases
    FOR EACH STATEMENT EXECUTE FUNCTION organization_workforce.guard_separation_basis_change();

-- 2. 劳动合同：员工聚合内每个雇佣关系的实体；不带用人单位列（取雇佣关系的用人单位，ADR-0086 决定 5）。
CREATE TABLE organization_workforce.employment_contracts (
    employment_contract_id      uuid        NOT NULL,
    employment_relationship_id  uuid        NOT NULL,
    employee_id                 uuid        NOT NULL,
    contract_kind               text        NOT NULL,
    term_start_date             date        NOT NULL,
    term_end_date               date,
    signed_on_date              date,
    probation_months_count      integer     NOT NULL DEFAULT 0,
    renews_contract_id          uuid,
    contract_file_id            uuid,
    status                      text        NOT NULL,
    submitted_by                uuid,
    approved_by                 uuid,
    terminated_on_date          date,
    termination_basis_key       text,
    created_at                  timestamptz NOT NULL,
    updated_at                  timestamptz NOT NULL,
    version                     bigint      NOT NULL DEFAULT 0,
    CONSTRAINT pk_employment_contracts PRIMARY KEY (employment_contract_id),
    CONSTRAINT uk_employment_contracts_id_relationship UNIQUE (employment_contract_id, employment_relationship_id),
    CONSTRAINT fk_employment_contracts_relationship FOREIGN KEY (employment_relationship_id, employee_id)
        REFERENCES organization_workforce.employment_relationships (employment_relationship_id, employee_id) ON DELETE RESTRICT,
    CONSTRAINT fk_employment_contracts_renews FOREIGN KEY (renews_contract_id, employment_relationship_id)
        REFERENCES organization_workforce.employment_contracts (employment_contract_id, employment_relationship_id) ON DELETE RESTRICT,
    CONSTRAINT fk_employment_contracts_termination_basis_key FOREIGN KEY (termination_basis_key)
        REFERENCES organization_workforce.separation_bases (separation_basis_key) ON DELETE RESTRICT,
    CONSTRAINT ck_employment_contracts_contract_kind CHECK (contract_kind IN ('FIXED_TERM', 'OPEN_ENDED', 'TASK_BASED')),
    CONSTRAINT ck_employment_contracts_status CHECK (status IN ('DRAFT', 'IN_APPROVAL', 'APPROVED', 'SIGNED', 'TERMINATED', 'VOID')),
    CONSTRAINT ck_employment_contracts_dates_finite CHECK (isfinite(term_start_date) AND isfinite(term_end_date) AND isfinite(signed_on_date) AND isfinite(terminated_on_date)),
    CONSTRAINT ck_employment_contracts_term_end CHECK ((contract_kind = 'FIXED_TERM') = (term_end_date IS NOT NULL) AND (term_end_date IS NULL OR term_end_date >= term_start_date)),
    CONSTRAINT ck_employment_contracts_probation_months CHECK (probation_months_count BETWEEN 0 AND 6),
    CONSTRAINT ck_employment_contracts_renewal_no_probation CHECK (renews_contract_id IS NULL OR probation_months_count = 0),
    CONSTRAINT ck_employment_contracts_renews_not_self CHECK (renews_contract_id <> employment_contract_id),
    CONSTRAINT ck_employment_contracts_approver_not_submitter CHECK (approved_by IS NULL OR approved_by <> submitted_by),
    CONSTRAINT ck_employment_contracts_termination_pair CHECK ((terminated_on_date IS NULL) = (termination_basis_key IS NULL)),
    CONSTRAINT ck_employment_contracts_termination_range CHECK (terminated_on_date IS NULL
        OR (terminated_on_date >= term_start_date AND (term_end_date IS NULL OR terminated_on_date <= term_end_date))),
    CONSTRAINT ck_employment_contracts_state_columns CHECK (
        (status <> 'DRAFT' OR (submitted_by IS NULL AND approved_by IS NULL AND signed_on_date IS NULL))
        AND (status <> 'IN_APPROVAL' OR (submitted_by IS NOT NULL AND approved_by IS NULL AND signed_on_date IS NULL))
        AND (status <> 'APPROVED' OR (submitted_by IS NOT NULL AND approved_by IS NOT NULL AND signed_on_date IS NULL))
        AND (status NOT IN ('SIGNED', 'TERMINATED') OR (submitted_by IS NOT NULL AND approved_by IS NOT NULL AND signed_on_date IS NOT NULL))
        AND ((status = 'TERMINATED') = (terminated_on_date IS NOT NULL))),
    CONSTRAINT ex_employment_contracts_in_force_period_overlap EXCLUDE USING gist (
        employment_relationship_id WITH =, daterange(term_start_date, coalesce(terminated_on_date, term_end_date), '[]') WITH &&) WHERE (status IN ('SIGNED', 'TERMINATED'))
);
CREATE UNIQUE INDEX uk_employment_contracts_relationship_term_start ON organization_workforce.employment_contracts (employment_relationship_id, term_start_date)
    WHERE status <> 'VOID';
CREATE UNIQUE INDEX uk_employment_contracts_one_successor ON organization_workforce.employment_contracts (renews_contract_id)
    WHERE renews_contract_id IS NOT NULL AND status <> 'VOID';
CREATE INDEX ix_employment_contracts_relationship ON organization_workforce.employment_contracts (employment_relationship_id, employee_id);
CREATE INDEX ix_employment_contracts_employee_id ON organization_workforce.employment_contracts (employee_id);
CREATE INDEX ix_employment_contracts_renews_contract_id ON organization_workforce.employment_contracts (renews_contract_id, employment_relationship_id)
    WHERE renews_contract_id IS NOT NULL;
CREATE INDEX ix_employment_contracts_termination_basis_key ON organization_workforce.employment_contracts (termination_basis_key)
    WHERE termination_basis_key IS NOT NULL;
COMMENT ON TABLE organization_workforce.employment_contracts IS '劳动合同：员工聚合内每个雇佣关系下的合同实体，按草拟、审批、签订、终止、作废推进；随员工保留，离职满 10 年由清除函数删除；粒度：每份合同一行；不存用人单位（取雇佣关系的用人单位）、不存实际入职日（取雇佣关系版本的入职日）';
COMMENT ON COLUMN organization_workforce.employment_contracts.employment_contract_id IS '劳动合同 ID（UUIDv7）';
COMMENT ON COLUMN organization_workforce.employment_contracts.employment_relationship_id IS '所属雇佣关系 ID';
COMMENT ON COLUMN organization_workforce.employment_contracts.employee_id IS '员工 ID，与雇佣关系的员工一致';
COMMENT ON COLUMN organization_workforce.employment_contracts.contract_kind IS '合同类型：FIXED_TERM 固定期限 / OPEN_ENDED 无固定期限 / TASK_BASED 以完成一定工作任务为期限';
COMMENT ON COLUMN organization_workforce.employment_contracts.term_start_date IS '约定的合同期起始日（不是实际入职日）';
COMMENT ON COLUMN organization_workforce.employment_contracts.term_end_date IS '合同期截止日（含当日）：为空表示无固定期限或以任务为期限，没有固定截止日；固定期限合同必填';
COMMENT ON COLUMN organization_workforce.employment_contracts.signed_on_date IS '签订日：为空表示尚未签订；可早于或晚于合同期起始日；已签订、已终止必填';
COMMENT ON COLUMN organization_workforce.employment_contracts.probation_months_count IS '试用期月数：0 表示无试用期条款；上限 6（法定绝对上限，分档与「仅首份合同」由命令检查）';
COMMENT ON COLUMN organization_workforce.employment_contracts.renews_contract_id IS '被续订的上一份合同：为空表示首份合同；同一雇佣关系内，一份合同只能被续订一次';
COMMENT ON COLUMN organization_workforce.employment_contracts.contract_file_id IS '合同扫描件文件 ID（文件模块的 ID，无外键）：为空表示未存扫描件；从已批准起可由空变有值一次';
COMMENT ON COLUMN organization_workforce.employment_contracts.status IS '状态：DRAFT 草拟 / IN_APPROVAL 审批中 / APPROVED 已批准 / SIGNED 已签订 / TERMINATED 已终止 / VOID 作废；「有效」按日期读取，不是状态';
COMMENT ON COLUMN organization_workforce.employment_contracts.submitted_by IS '提交人（用户 ID）：为空表示尚未提交；驳回回到草拟时清空';
COMMENT ON COLUMN organization_workforce.employment_contracts.approved_by IS '批准人（用户 ID）：为空表示尚未批准；不得与提交人相同；驳回回到草拟时清空';
COMMENT ON COLUMN organization_workforce.employment_contracts.terminated_on_date IS '终止日：为空表示未终止；仅已终止的合同有值，不早于合同期起始日、不晚于合同期截止日';
COMMENT ON COLUMN organization_workforce.employment_contracts.termination_basis_key IS '终止依据键：为空表示未终止；与终止日同有同无';
COMMENT ON COLUMN organization_workforce.employment_contracts.created_at IS '创建时间';
COMMENT ON COLUMN organization_workforce.employment_contracts.updated_at IS '最近一次变更时间';
COMMENT ON COLUMN organization_workforce.employment_contracts.version IS '版本号，乐观锁；每次状态推进加一';

-- 离职申请指向的人事命令须在同一雇佣关系：该唯一约束是复合外键的目标，不增加业务规则（命令 ID 本来就是主键）。
ALTER TABLE organization_workforce.personnel_commands ADD CONSTRAINT uk_personnel_commands_id_relationship UNIQUE (personnel_command_id, employment_relationship_id);

-- 3. 离职申请：员工聚合内每个雇佣关系的实体；每个雇佣关系同时只有一份未结束的申请。
CREATE TABLE organization_workforce.separation_requests (
    separation_request_id                  uuid        NOT NULL,
    employment_relationship_id             uuid        NOT NULL,
    employee_id                            uuid        NOT NULL,
    initiator_kind                         text        NOT NULL,
    separation_basis_key                   text        NOT NULL,
    request_source_kind                    text        NOT NULL,
    requested_by                           uuid        NOT NULL,
    decided_by                             uuid,
    requested_on_date                      date        NOT NULL,
    approved_on_date                       date,
    last_working_date                      date,
    handover_completed_on_date             date,
    agreement_file_id                      uuid,
    protected_circumstances_checked_on_date date,
    union_notified_on_date                 date,
    union_opinion_note                     text,
    union_notice_result_note               text,
    note                                   text,
    personnel_command_id                   uuid,
    status                                 text        NOT NULL,
    created_at                             timestamptz NOT NULL,
    updated_at                             timestamptz NOT NULL,
    version                                bigint      NOT NULL DEFAULT 0,
    CONSTRAINT pk_separation_requests PRIMARY KEY (separation_request_id),
    CONSTRAINT fk_separation_requests_relationship FOREIGN KEY (employment_relationship_id, employee_id)
        REFERENCES organization_workforce.employment_relationships (employment_relationship_id, employee_id) ON DELETE RESTRICT,
    CONSTRAINT fk_separation_requests_separation_basis_key FOREIGN KEY (separation_basis_key)
        REFERENCES organization_workforce.separation_bases (separation_basis_key) ON DELETE RESTRICT,
    CONSTRAINT fk_separation_requests_personnel_command FOREIGN KEY (personnel_command_id, employment_relationship_id)
        REFERENCES organization_workforce.personnel_commands (personnel_command_id, employment_relationship_id) ON DELETE RESTRICT,
    CONSTRAINT ck_separation_requests_initiator_kind CHECK (initiator_kind IN ('EMPLOYEE', 'EMPLOYER', 'BOTH')),
    CONSTRAINT ck_separation_requests_request_source_kind CHECK (request_source_kind IN ('EMPLOYEE_SELF', 'HR_ON_BEHALF', 'SYSTEM')),
    CONSTRAINT ck_separation_requests_status CHECK (status IN ('REQUESTED', 'APPROVED', 'SEPARATING', 'COMPLETED', 'WITHDRAWN', 'REJECTED')),
    CONSTRAINT ck_separation_requests_dates_finite CHECK (isfinite(requested_on_date) AND isfinite(approved_on_date) AND isfinite(last_working_date) AND isfinite(handover_completed_on_date)
        AND isfinite(protected_circumstances_checked_on_date) AND isfinite(union_notified_on_date)),
    CONSTRAINT ck_separation_requests_approver_not_requester CHECK (request_source_kind = 'SYSTEM' OR decided_by IS NULL OR decided_by <> requested_by),
    CONSTRAINT ck_separation_requests_date_order CHECK ((approved_on_date IS NULL OR requested_on_date <= approved_on_date)
        AND (last_working_date IS NULL OR request_source_kind <> 'EMPLOYEE_SELF' OR last_working_date >= requested_on_date)),
    CONSTRAINT ck_separation_requests_notes CHECK (
        (union_opinion_note IS NULL OR (char_length(union_opinion_note) BETWEEN 1 AND 500 AND btrim(union_opinion_note) <> ''))
        AND (union_notice_result_note IS NULL OR (char_length(union_notice_result_note) BETWEEN 1 AND 500 AND btrim(union_notice_result_note) <> ''))
        AND (note IS NULL OR (char_length(note) BETWEEN 1 AND 500 AND btrim(note) <> ''))),
    CONSTRAINT ck_separation_requests_state_columns CHECK (
        (status <> 'REQUESTED' OR (approved_on_date IS NULL AND decided_by IS NULL AND personnel_command_id IS NULL))
        AND (status <> 'REJECTED' OR (decided_by IS NOT NULL AND approved_on_date IS NULL))
        AND (status NOT IN ('APPROVED', 'SEPARATING', 'COMPLETED') OR (approved_on_date IS NOT NULL AND decided_by IS NOT NULL AND last_working_date IS NOT NULL))
        AND (status <> 'SEPARATING' OR personnel_command_id IS NOT NULL)
        AND (personnel_command_id IS NULL OR status IN ('SEPARATING', 'COMPLETED'))
        AND (status <> 'COMPLETED' OR personnel_command_id IS NOT NULL OR request_source_kind = 'SYSTEM')
        AND (request_source_kind <> 'SYSTEM' OR (status NOT IN ('REQUESTED', 'REJECTED') AND approved_on_date = requested_on_date)))
);
CREATE UNIQUE INDEX uk_separation_requests_open_per_relationship ON organization_workforce.separation_requests (employment_relationship_id)
    WHERE status IN ('REQUESTED', 'APPROVED', 'SEPARATING');
CREATE UNIQUE INDEX uk_separation_requests_personnel_command ON organization_workforce.separation_requests (personnel_command_id, employment_relationship_id)
    WHERE personnel_command_id IS NOT NULL;
CREATE INDEX ix_separation_requests_relationship ON organization_workforce.separation_requests (employment_relationship_id, employee_id);
CREATE INDEX ix_separation_requests_employee_id ON organization_workforce.separation_requests (employee_id);
CREATE INDEX ix_separation_requests_separation_basis_key ON organization_workforce.separation_requests (separation_basis_key);
COMMENT ON TABLE organization_workforce.separation_requests IS '离职申请：员工聚合内每个雇佣关系的实体，按申请、批准、办理离职、完成推进，或撤回、驳回；含终止依据与解除核查，属敏感个人信息之外的个人信息；随员工保留，离职满 10 年由清除函数删除；粒度：每份申请一行，每个雇佣关系同时只有一份未结束的申请';
COMMENT ON COLUMN organization_workforce.separation_requests.separation_request_id IS '离职申请 ID（UUIDv7）';
COMMENT ON COLUMN organization_workforce.separation_requests.employment_relationship_id IS '所属雇佣关系 ID';
COMMENT ON COLUMN organization_workforce.separation_requests.employee_id IS '员工 ID，与雇佣关系的员工一致';
COMMENT ON COLUMN organization_workforce.separation_requests.initiator_kind IS '发起方：EMPLOYEE 员工 / EMPLOYER 用人单位 / BOTH 双方';
COMMENT ON COLUMN organization_workforce.separation_requests.separation_basis_key IS '离职依据键，引用离职依据清单';
COMMENT ON COLUMN organization_workforce.separation_requests.request_source_kind IS '申请来源：EMPLOYEE_SELF 员工自助 / HR_ON_BEHALF 人事代办 / SYSTEM 系统（换用人单位、迁入）';
COMMENT ON COLUMN organization_workforce.separation_requests.requested_by IS '申请人（用户 ID）；系统申请记系统角色';
COMMENT ON COLUMN organization_workforce.separation_requests.decided_by IS '批准或驳回的人（用户 ID）：为空表示尚未决定；人工申请不得与申请人相同，系统申请记系统角色';
COMMENT ON COLUMN organization_workforce.separation_requests.requested_on_date IS '申请日';
COMMENT ON COLUMN organization_workforce.separation_requests.approved_on_date IS '批准日：为空表示尚未批准；批准后即使撤回也保留';
COMMENT ON COLUMN organization_workforce.separation_requests.last_working_date IS '最后工作日：为空表示尚未确定（人事至迟在批准时填）；批准起必填；不存推算日期';
COMMENT ON COLUMN organization_workforce.separation_requests.handover_completed_on_date IS '交接完成日：为空表示交接未完成；批准起可填，更正时写新值并留审计';
COMMENT ON COLUMN organization_workforce.separation_requests.agreement_file_id IS '协议或书面通知文件 ID（文件模块的 ID，无外键）：为空表示没有协议或通知文件；批准起可由空变有值一次';
COMMENT ON COLUMN organization_workforce.separation_requests.protected_circumstances_checked_on_date IS '禁止解除情形核查日：为空表示非用人单位单方解除，或尚未确认';
COMMENT ON COLUMN organization_workforce.separation_requests.union_notified_on_date IS '通知工会日：为空表示非用人单位单方解除，或尚未通知';
COMMENT ON COLUMN organization_workforce.separation_requests.union_opinion_note IS '工会意见记录：为空表示未记录（期限内未提意见时写明「未提出」）；1 至 500 字，不得含健康或怀孕内容';
COMMENT ON COLUMN organization_workforce.separation_requests.union_notice_result_note IS '书面通知结果记录：为空表示未记录；1 至 500 字，不得含健康或怀孕内容';
COMMENT ON COLUMN organization_workforce.separation_requests.note IS '备注：为空表示未记录；1 至 500 字，不得含健康或怀孕内容';
COMMENT ON COLUMN organization_workforce.separation_requests.personnel_command_id IS '关联的离职人事命令：为空表示尚未录入离职命令；必须是同一雇佣关系的 SEPARATE 命令，生效日为最后工作日次日';
COMMENT ON COLUMN organization_workforce.separation_requests.status IS '状态：REQUESTED 已申请 / APPROVED 已批准 / SEPARATING 办理离职中 / COMPLETED 已完成 / WITHDRAWN 已撤回 / REJECTED 已驳回';
COMMENT ON COLUMN organization_workforce.separation_requests.created_at IS '创建时间';
COMMENT ON COLUMN organization_workforce.separation_requests.updated_at IS '最近一次变更时间';
COMMENT ON COLUMN organization_workforce.separation_requests.version IS '版本号，乐观锁；每次状态推进加一';

-- 4. 劳动合同的触发器：合法写入的完整清单，其余一律拒绝。DELETE 只放行表属主（离职满 10 年的清除函数），TRUNCATE 拒绝。
CREATE FUNCTION organization_workforce.guard_employment_contract_change() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
DECLARE
    owner_name name;
    changeable text[];
BEGIN
    IF TG_OP = 'TRUNCATE' THEN
        RAISE EXCEPTION 'employment contracts: truncate on %.% is refused', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
    END IF;
    IF TG_OP = 'DELETE' THEN
        SELECT pg_get_userbyid(relowner) INTO owner_name FROM pg_class WHERE oid = TG_RELID;
        IF current_user <> owner_name THEN
            RAISE EXCEPTION 'employment contracts: delete on %.% is refused, only the table owner purges', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
        END IF;
        RETURN OLD;
    END IF;
    IF TG_OP = 'INSERT' THEN
        IF NEW.status <> 'DRAFT' THEN
            RAISE EXCEPTION 'employment contracts: a contract is inserted as DRAFT' USING ERRCODE = 'restrict_violation';
        END IF;
        RETURN NEW;
    END IF;
    IF NEW.version <> OLD.version + 1 THEN
        RAISE EXCEPTION 'employment contracts: version must advance by one with every change' USING ERRCODE = 'restrict_violation';
    END IF;
    changeable := CASE
        WHEN OLD.status = 'DRAFT' AND NEW.status = 'DRAFT' THEN ARRAY['contract_kind', 'term_start_date', 'term_end_date', 'probation_months_count', 'renews_contract_id', 'contract_file_id']
        WHEN OLD.status = 'DRAFT' AND NEW.status = 'IN_APPROVAL' THEN ARRAY['submitted_by']
        WHEN OLD.status = 'IN_APPROVAL' AND NEW.status = 'APPROVED' THEN ARRAY['approved_by']
        WHEN OLD.status = 'IN_APPROVAL' AND NEW.status = 'DRAFT' THEN ARRAY['submitted_by', 'approved_by']
        WHEN OLD.status = 'APPROVED' AND NEW.status = 'SIGNED' THEN ARRAY['signed_on_date']
        WHEN OLD.status = 'SIGNED' AND NEW.status = 'TERMINATED' THEN ARRAY['terminated_on_date', 'termination_basis_key']
        WHEN OLD.status IN ('DRAFT', 'IN_APPROVAL', 'APPROVED', 'SIGNED') AND NEW.status = 'VOID' THEN ARRAY[]::text[]
        WHEN OLD.status = NEW.status AND OLD.status IN ('APPROVED', 'SIGNED', 'TERMINATED') AND OLD.contract_file_id IS NULL THEN ARRAY['contract_file_id']
        ELSE NULL
    END;
    IF changeable IS NULL THEN
        RAISE EXCEPTION 'employment contracts: no legal change from % to %', OLD.status, NEW.status USING ERRCODE = 'restrict_violation';
    END IF;
    IF (to_jsonb(NEW) - changeable - 'status' - 'updated_at' - 'version') <> (to_jsonb(OLD) - changeable - 'status' - 'updated_at' - 'version') THEN
        RAISE EXCEPTION 'employment contracts: change from % to % may only write %', OLD.status, NEW.status, changeable USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END $$;
COMMENT ON FUNCTION organization_workforce.guard_employment_contract_change() IS '劳动合同的触发器：只放行合法状态边及其可写的列，DELETE 只放行表属主，TRUNCATE 拒绝';
CREATE TRIGGER tg_employment_contracts_change BEFORE INSERT OR UPDATE OR DELETE ON organization_workforce.employment_contracts
    FOR EACH ROW EXECUTE FUNCTION organization_workforce.guard_employment_contract_change();
CREATE TRIGGER tg_employment_contracts_truncate BEFORE TRUNCATE ON organization_workforce.employment_contracts
    FOR EACH STATEMENT EXECUTE FUNCTION organization_workforce.guard_employment_contract_change();

-- 5. 离职申请的触发器：合法写入的完整清单。数据库在此承载三条跨行规则：系统专用依据只给系统申请、用人单位单方解除批准前须有四项核查、关联命令须是同一关系的 SEPARATE 且生效日为最后工作日次日。
CREATE FUNCTION organization_workforce.guard_separation_request_change() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
DECLARE
    owner_name name;
    changeable text[];
    basis organization_workforce.separation_bases%ROWTYPE;
    linked organization_workforce.personnel_commands%ROWTYPE;
BEGIN
    IF TG_OP = 'TRUNCATE' THEN
        RAISE EXCEPTION 'separation requests: truncate on %.% is refused', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
    END IF;
    IF TG_OP = 'DELETE' THEN
        SELECT pg_get_userbyid(relowner) INTO owner_name FROM pg_class WHERE oid = TG_RELID;
        IF current_user <> owner_name THEN
            RAISE EXCEPTION 'separation requests: delete on %.% is refused, only the table owner purges', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
        END IF;
        RETURN OLD;
    END IF;
    IF TG_OP = 'INSERT' OR NEW.separation_basis_key <> OLD.separation_basis_key THEN
        SELECT * INTO basis FROM organization_workforce.separation_bases WHERE separation_basis_key = NEW.separation_basis_key;
        IF basis.is_system_only <> (NEW.request_source_kind = 'SYSTEM') THEN
            RAISE EXCEPTION 'separation requests: a system-only basis belongs to SYSTEM requests only, and a SYSTEM request uses a system-only basis' USING ERRCODE = 'restrict_violation';
        END IF;
    END IF;
    IF TG_OP = 'INSERT' THEN
        IF NOT ((NEW.request_source_kind <> 'SYSTEM' AND NEW.status = 'REQUESTED') OR (NEW.request_source_kind = 'SYSTEM' AND NEW.status IN ('APPROVED', 'COMPLETED'))) THEN
            RAISE EXCEPTION 'separation requests: a human-filed request starts REQUESTED, a SYSTEM request starts APPROVED or COMPLETED' USING ERRCODE = 'restrict_violation';
        END IF;
        RETURN NEW;
    END IF;
    IF NEW.version <> OLD.version + 1 THEN
        RAISE EXCEPTION 'separation requests: version must advance by one with every change' USING ERRCODE = 'restrict_violation';
    END IF;
    changeable := CASE
        WHEN OLD.status = 'REQUESTED' AND NEW.status = 'REQUESTED' THEN ARRAY['initiator_kind', 'separation_basis_key', 'requested_on_date', 'last_working_date', 'agreement_file_id',
            'protected_circumstances_checked_on_date', 'union_notified_on_date', 'union_opinion_note', 'union_notice_result_note', 'note']
        WHEN OLD.status = 'REQUESTED' AND NEW.status = 'APPROVED' THEN ARRAY['approved_on_date', 'decided_by', 'last_working_date']
        WHEN OLD.status = 'REQUESTED' AND NEW.status = 'REJECTED' THEN ARRAY['decided_by']
        WHEN OLD.status IN ('REQUESTED', 'APPROVED') AND NEW.status = 'WITHDRAWN' THEN ARRAY[]::text[]
        WHEN OLD.status = 'APPROVED' AND NEW.status = 'SEPARATING' THEN ARRAY['personnel_command_id']
        WHEN OLD.status = 'SEPARATING' AND NEW.status = 'APPROVED' THEN ARRAY['personnel_command_id']
        WHEN OLD.status = 'SEPARATING' AND NEW.status = 'COMPLETED' THEN ARRAY[]::text[]
        WHEN OLD.status = NEW.status AND OLD.status IN ('APPROVED', 'SEPARATING', 'COMPLETED') AND OLD.agreement_file_id IS NULL THEN ARRAY['handover_completed_on_date', 'agreement_file_id']
        WHEN OLD.status = NEW.status AND OLD.status IN ('APPROVED', 'SEPARATING', 'COMPLETED') THEN ARRAY['handover_completed_on_date']
        ELSE NULL
    END;
    IF changeable IS NULL THEN
        RAISE EXCEPTION 'separation requests: no legal change from % to %', OLD.status, NEW.status USING ERRCODE = 'restrict_violation';
    END IF;
    IF (to_jsonb(NEW) - changeable - 'status' - 'updated_at' - 'version') <> (to_jsonb(OLD) - changeable - 'status' - 'updated_at' - 'version') THEN
        RAISE EXCEPTION 'separation requests: change from % to % may only write %', OLD.status, NEW.status, changeable USING ERRCODE = 'restrict_violation';
    END IF;
    IF OLD.status = 'REQUESTED' AND NEW.status = 'APPROVED' THEN
        SELECT * INTO basis FROM organization_workforce.separation_bases WHERE separation_basis_key = NEW.separation_basis_key;
        IF basis.is_employer_initiated AND num_nonnulls(NEW.protected_circumstances_checked_on_date, NEW.union_notified_on_date, NEW.union_opinion_note, NEW.union_notice_result_note) <> 4 THEN
            RAISE EXCEPTION 'separation requests: an employer-initiated basis needs the four dismissal check columns before approval' USING ERRCODE = 'restrict_violation';
        END IF;
    END IF;
    IF OLD.status = 'APPROVED' AND NEW.status = 'SEPARATING' THEN
        SELECT * INTO linked FROM organization_workforce.personnel_commands WHERE personnel_command_id = NEW.personnel_command_id FOR SHARE;
        IF linked.command_kind IS DISTINCT FROM 'SEPARATE' OR linked.status = 'WITHDRAWN' OR linked.effective_date IS DISTINCT FROM NEW.last_working_date + 1 THEN
            RAISE EXCEPTION 'separation requests: the linked command must be a live SEPARATE effective on last_working_date plus one day' USING ERRCODE = 'restrict_violation';
        END IF;
    END IF;
    IF OLD.status = 'SEPARATING' AND NEW.status = 'COMPLETED' THEN
        IF NOT EXISTS (SELECT 1 FROM organization_workforce.personnel_commands WHERE personnel_command_id = OLD.personnel_command_id AND status = 'EFFECTIVE') THEN
            RAISE EXCEPTION 'separation requests: a request completes only when its SEPARATE command is EFFECTIVE' USING ERRCODE = 'restrict_violation';
        END IF;
    END IF;
    RETURN NEW;
END $$;
COMMENT ON FUNCTION organization_workforce.guard_separation_request_change() IS '离职申请的触发器：只放行合法状态边及其可写的列，并检查系统专用依据、解除核查四项、关联命令；DELETE 只放行表属主，TRUNCATE 拒绝';
CREATE TRIGGER tg_separation_requests_change BEFORE INSERT OR UPDATE OR DELETE ON organization_workforce.separation_requests
    FOR EACH ROW EXECUTE FUNCTION organization_workforce.guard_separation_request_change();
CREATE TRIGGER tg_separation_requests_truncate BEFORE TRUNCATE ON organization_workforce.separation_requests
    FOR EACH STATEMENT EXECUTE FUNCTION organization_workforce.guard_separation_request_change();
