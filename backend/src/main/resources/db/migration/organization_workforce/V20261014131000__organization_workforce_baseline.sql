-- 组织快照：只读，外部组织源为源（RULE-357/359/368）。表中不含任何金额列（GUIDING_PRINCIPLES §9a）。
CREATE SCHEMA IF NOT EXISTS organization_workforce;
COMMENT ON SCHEMA organization_workforce IS '组织人事 BC：组织快照（只读，来源可替换）';

CREATE TABLE organization_workforce.positions (
    id         uuid        PRIMARY KEY,
    code       text,
    name       text        NOT NULL,
    sort_order integer,
    status     text        NOT NULL CHECK (status IN ('ACTIVE', 'DISABLED', 'DELETED')),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    version    bigint      NOT NULL DEFAULT 0,
    CONSTRAINT ck_positions_name CHECK (char_length(name) <= 500 AND btrim(name) <> ''),
    CONSTRAINT ck_positions_code CHECK (code IS NULL OR (char_length(code) <= 500 AND btrim(code) <> ''))
);
COMMENT ON TABLE organization_workforce.positions IS '岗位：组织源岗位的快照，停用与删除同步（RULE-367）；粒度：每个岗位一行';
COMMENT ON COLUMN organization_workforce.positions.id IS '岗位 ID（UUIDv7；组织源岗位经来源对照映射）';
COMMENT ON COLUMN organization_workforce.positions.code IS '岗位编码';
COMMENT ON COLUMN organization_workforce.positions.name IS '岗位名称';
COMMENT ON COLUMN organization_workforce.positions.sort_order IS '显示顺序';
COMMENT ON COLUMN organization_workforce.positions.status IS '状态：ACTIVE 启用 / DISABLED 停用 / DELETED 已删除（组织源删除或不再返回）';
COMMENT ON COLUMN organization_workforce.positions.created_at IS '创建时间';
COMMENT ON COLUMN organization_workforce.positions.updated_at IS '更新时间';
COMMENT ON COLUMN organization_workforce.positions.version IS '乐观锁版本';

CREATE TABLE organization_workforce.org_units (
    id                              uuid        PRIMARY KEY,
    code                            text,
    name                            text        NOT NULL,
    parent_org_unit_id              uuid,
    sort_order                      integer,
    status                          text        NOT NULL CHECK (status IN ('ACTIVE', 'DISABLED', 'REVOKED')),
    head_employee_id                uuid,
    regional_receivables_manager_id uuid,
    region_category                 text        CHECK (region_category IN ('HEADQUARTERS', 'REGIONAL')),
    is_delivery_unit                boolean     NOT NULL DEFAULT false,
    short_name                      text,
    revoked_at                      timestamptz,
    last_sync_run_id                uuid,
    created_at                      timestamptz NOT NULL DEFAULT now(),
    updated_at                      timestamptz NOT NULL DEFAULT now(),
    version                         bigint      NOT NULL DEFAULT 0,
    CONSTRAINT ck_org_units_revoked_at CHECK ((status = 'REVOKED') = (revoked_at IS NOT NULL)),
    CONSTRAINT ck_org_units_name CHECK (char_length(name) <= 500 AND btrim(name) <> ''),
    CONSTRAINT ck_org_units_code CHECK (code IS NULL OR (char_length(code) <= 500 AND btrim(code) <> '')),
    CONSTRAINT ck_org_units_short_name CHECK (short_name IS NULL OR (char_length(short_name) <= 255 AND btrim(short_name) <> ''))
);
CREATE INDEX org_units_parent_org_unit_id_idx ON organization_workforce.org_units (parent_org_unit_id);
COMMENT ON TABLE organization_workforce.org_units IS '组织单元：组织源组织单元的快照；停用（DISABLED）与撤销（REVOKED，组织源不再返回）分开（RULE-355/357）；粒度：每个组织单元一行，变化历史在 org_unit_versions';
COMMENT ON COLUMN organization_workforce.org_units.id IS '组织单元 ID（UUIDv7；组织源部门经来源对照映射）';
COMMENT ON COLUMN organization_workforce.org_units.code IS '组织单元编码';
COMMENT ON COLUMN organization_workforce.org_units.name IS '组织单元名称';
COMMENT ON COLUMN organization_workforce.org_units.parent_org_unit_id IS '组织单元的上级（由组织源的防腐层算出父编号，RULE-354）；根为空';
COMMENT ON COLUMN organization_workforce.org_units.sort_order IS '同级显示顺序';
COMMENT ON COLUMN organization_workforce.org_units.status IS '状态：ACTIVE 有效 / DISABLED 停用（组织源停用标志，NULL 视为有效）/ REVOKED 已撤销（组织源不再返回）';
COMMENT ON COLUMN organization_workforce.org_units.head_employee_id IS '部门负责人（员工 ID，组织源关系；多名取组织源人员 ID 最小者，由集成层适配器按源 ID 升序交出，RULE-356）';
COMMENT ON COLUMN organization_workforce.org_units.regional_receivables_manager_id IS '区域应收负责人（员工 ID，老系统「区域 AR」，组织源关系）';
COMMENT ON COLUMN organization_workforce.org_units.region_category IS '总部 / 区域类别：HEADQUARTERS 总部、REGIONAL 区域；组织源未维护为空';
COMMENT ON COLUMN organization_workforce.org_units.is_delivery_unit IS '是否交付部门（组织源扩展属性）';
COMMENT ON COLUMN organization_workforce.org_units.short_name IS '部门简称：组织源没有，每天从老系统只读刷新；为空表示没有简称';
COMMENT ON COLUMN organization_workforce.org_units.revoked_at IS '撤销时间（组织源不再返回该组织单元的时间）；为空表示组织单元未被撤销';
COMMENT ON COLUMN organization_workforce.org_units.last_sync_run_id IS '最近一次改动它的同步记录';
COMMENT ON COLUMN organization_workforce.org_units.created_at IS '创建时间';
COMMENT ON COLUMN organization_workforce.org_units.updated_at IS '更新时间';
COMMENT ON COLUMN organization_workforce.org_units.version IS '乐观锁版本';

CREATE TABLE organization_workforce.employees (
    id                uuid        PRIMARY KEY,
    employee_code     text        NOT NULL,
    display_name      text        NOT NULL,
    login_name        text,
    email             text,
    gender            text        CHECK (gender IN ('MALE', 'FEMALE')),
    wecom_user_id     text,
    employment_status text        NOT NULL CHECK (employment_status IN ('EMPLOYED', 'SEPARATED')),
    is_fee_earner     boolean     NOT NULL DEFAULT false,
    separated_at      timestamptz,
    last_sync_run_id  uuid,
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    version           bigint      NOT NULL DEFAULT 0,
    event_count       bigint      NOT NULL DEFAULT 0,
    CONSTRAINT ck_employees_employee_code CHECK (char_length(employee_code) <= 500 AND btrim(employee_code) <> ''),
    CONSTRAINT ck_employees_display_name CHECK (char_length(display_name) <= 500 AND btrim(display_name) <> ''),
    CONSTRAINT ck_employees_email_address CHECK (email IS NULL OR (char_length(email) <= 100 AND btrim(email) <> ''))
);
CREATE UNIQUE INDEX employees_wecom_user_id_employed_key ON organization_workforce.employees (wecom_user_id)
    WHERE employment_status = 'EMPLOYED' AND wecom_user_id IS NOT NULL;
CREATE INDEX employees_employee_code_idx ON organization_workforce.employees (employee_code);
COMMENT ON TABLE organization_workforce.employees IS '员工：组织源人员的快照；没有登录名即离职（RULE-363），不含生日等敏感信息；粒度：每个员工一行';
COMMENT ON COLUMN organization_workforce.employees.id IS '员工 ID（UUIDv7；组织源人员经来源对照映射）';
COMMENT ON COLUMN organization_workforce.employees.employee_code IS '员工编号（工号）';
COMMENT ON COLUMN organization_workforce.employees.display_name IS '姓名';
COMMENT ON COLUMN organization_workforce.employees.login_name IS '登录名；为空（null 或空串）表示已离职（RULE-363）';
COMMENT ON COLUMN organization_workforce.employees.email IS '企业邮箱';
COMMENT ON COLUMN organization_workforce.employees.gender IS '性别：MALE / FEMALE；组织源未维护或无法识别为空（不再默认写成女）';
COMMENT ON COLUMN organization_workforce.employees.wecom_user_id IS '企业微信 userid（企业微信登录与通知的唯一键，建档时写入，RULE-362）；在职员工中唯一';
COMMENT ON COLUMN organization_workforce.employees.employment_status IS '在职状态：EMPLOYED 在职 / SEPARATED 离职（含不在组织源结果中的离职候选，RULE-366）';
COMMENT ON COLUMN organization_workforce.employees.is_fee_earner IS '是否收费员工：组织源的人事分类，只是可计费性判定的输入之一，不等于可计费';
COMMENT ON COLUMN organization_workforce.employees.separated_at IS '离职时间（首次同步到离职的时间）';
COMMENT ON COLUMN organization_workforce.employees.last_sync_run_id IS '最近一次改动它的同步记录';
COMMENT ON COLUMN organization_workforce.employees.created_at IS '创建时间';
COMMENT ON COLUMN organization_workforce.employees.updated_at IS '更新时间';
COMMENT ON COLUMN organization_workforce.employees.version IS '乐观锁版本';
COMMENT ON COLUMN organization_workforce.employees.event_count IS '事件序号：员工聚合已发出的事件数（登录时登记发一个），作为事件信封的 aggregateVersion；同步写入不改变它';

CREATE TABLE organization_workforce.assignments (
    employee_id            uuid        PRIMARY KEY REFERENCES organization_workforce.employees (id),
    org_unit_id            uuid        NOT NULL REFERENCES organization_workforce.org_units (id),
    position_id            uuid        REFERENCES organization_workforce.positions (id),
    reports_to_employee_id uuid,
    created_at             timestamptz NOT NULL DEFAULT now(),
    updated_at             timestamptz NOT NULL DEFAULT now(),
    version                bigint      NOT NULL DEFAULT 0
);
CREATE INDEX assignments_org_unit_id_idx ON organization_workforce.assignments (org_unit_id);
CREATE INDEX ix_assignments_position_id ON organization_workforce.assignments (position_id);
COMMENT ON TABLE organization_workforce.assignments IS '任职：员工所在组织单元、岗位与直属领导（汇报线），组织源为源；员工的 1:1 扩展表（主岗投影），主键即员工 ID；粒度：每个员工当前一行，变化历史在 assignment_versions';
COMMENT ON COLUMN organization_workforce.assignments.employee_id IS '员工 ID';
COMMENT ON COLUMN organization_workforce.assignments.org_unit_id IS '组织单元 ID（所属部门）';
COMMENT ON COLUMN organization_workforce.assignments.position_id IS '岗位 ID；组织源未给岗位的行在同步时被隔离，因此正常数据不为空';
COMMENT ON COLUMN organization_workforce.assignments.reports_to_employee_id IS '直属领导（员工 ID）；组织源清空即清空；不存在、已离职、本人或成环的领导不写入（RULE-372）';
COMMENT ON COLUMN organization_workforce.assignments.created_at IS '创建时间';
COMMENT ON COLUMN organization_workforce.assignments.updated_at IS '更新时间';
COMMENT ON COLUMN organization_workforce.assignments.version IS '乐观锁版本';

-- 同步记录与差异（RULE-358/366/373）：每 5 分钟一次，缩水时 HELD，管理员可放行。
CREATE TABLE organization_workforce.sync_runs (
    id                             uuid        PRIMARY KEY,
    trigger_kind                   text        NOT NULL CHECK (trigger_kind IN ('SCHEDULED', 'MANUAL')),
    status                         text        NOT NULL CHECK (status IN ('RUNNING', 'SUCCEEDED', 'HELD', 'RELEASED', 'FAILED')),
    started_at                     timestamptz NOT NULL,
    finished_at                    timestamptz,
    source_org_unit_count          integer,
    source_employee_count          integer,
    incoming_active_org_unit_count integer,
    incoming_employed_count        integer,
    previous_active_org_unit_count integer,
    previous_employed_count        integer,
    org_unit_change_count          integer,
    employee_change_count          integer,
    position_change_count          integer,
    isolated_row_count             integer,
    issue_count                    integer,
    employed_without_wecom_count   integer,
    held_reason                    text,
    failure_reason                 text,
    released_by                    uuid,
    released_at                    timestamptz,
    released_by_run_id             uuid,
    created_at                     timestamptz NOT NULL DEFAULT now(),
    updated_at                     timestamptz NOT NULL DEFAULT now(),
    version                        bigint      NOT NULL DEFAULT 0,
    event_count                    bigint      NOT NULL DEFAULT 0,
    hold_kind_codes                text[]      NOT NULL DEFAULT '{}' CHECK (hold_kind_codes <@ ARRAY['SHRINK_GUARD', 'ADMINISTRATOR_SEPARATION']),
    released_kind_codes            text[]      NOT NULL DEFAULT '{}' CHECK (released_kind_codes <@ ARRAY['SHRINK_GUARD', 'ADMINISTRATOR_SEPARATION'])
);
CREATE INDEX sync_runs_started_at_idx ON organization_workforce.sync_runs (started_at DESC);
CREATE INDEX sync_runs_status_idx ON organization_workforce.sync_runs (status);
COMMENT ON TABLE organization_workforce.sync_runs IS '同步记录：组织快照每次同步的记录与缩水保护结果；粒度：每次同步运行一行';
COMMENT ON COLUMN organization_workforce.sync_runs.id IS '同步记录 ID（UUIDv7）';
COMMENT ON COLUMN organization_workforce.sync_runs.trigger_kind IS '触发方式：SCHEDULED 定时 / MANUAL 管理员放行';
COMMENT ON COLUMN organization_workforce.sync_runs.status IS '状态：RUNNING 运行中 / SUCCEEDED 成功 / HELD 因缩水保护中止 / RELEASED 已被管理员放行 / FAILED 失败';
COMMENT ON COLUMN organization_workforce.sync_runs.started_at IS '开始时间';
COMMENT ON COLUMN organization_workforce.sync_runs.finished_at IS '结束时间';
COMMENT ON COLUMN organization_workforce.sync_runs.source_org_unit_count IS '组织源返回的组织单元行数';
COMMENT ON COLUMN organization_workforce.sync_runs.source_employee_count IS '组织源返回的人员行数（同步范围内）';
COMMENT ON COLUMN organization_workforce.sync_runs.incoming_active_org_unit_count IS '组织源返回的有效组织单元数（缩水保护的分子）';
COMMENT ON COLUMN organization_workforce.sync_runs.incoming_employed_count IS '组织源返回的在职人数（缩水保护的分子）';
COMMENT ON COLUMN organization_workforce.sync_runs.previous_active_org_unit_count IS '同步前快照中的有效组织单元数（缩水保护的分母）';
COMMENT ON COLUMN organization_workforce.sync_runs.previous_employed_count IS '同步前快照中的在职人数（缩水保护的分母）';
COMMENT ON COLUMN organization_workforce.sync_runs.org_unit_change_count IS '本次变更的组织单元数';
COMMENT ON COLUMN organization_workforce.sync_runs.employee_change_count IS '本次变更的员工数';
COMMENT ON COLUMN organization_workforce.sync_runs.position_change_count IS '本次变更的岗位数';
COMMENT ON COLUMN organization_workforce.sync_runs.isolated_row_count IS '被隔离的人员行数（缺部门或岗位，RULE-365）';
COMMENT ON COLUMN organization_workforce.sync_runs.issue_count IS '数据例外条数（见同步差异中的 ISSUE 行）';
COMMENT ON COLUMN organization_workforce.sync_runs.employed_without_wecom_count IS '在职但无企业微信 userid 的人数（数据质量，RULE-374）';
COMMENT ON COLUMN organization_workforce.sync_runs.held_reason IS '中止原因（缩水保护）';
COMMENT ON COLUMN organization_workforce.sync_runs.failure_reason IS '失败原因';
COMMENT ON COLUMN organization_workforce.sync_runs.released_by IS '放行人（用户 ID）';
COMMENT ON COLUMN organization_workforce.sync_runs.released_at IS '放行时间';
COMMENT ON COLUMN organization_workforce.sync_runs.released_by_run_id IS '放行后实际执行的同步记录';
COMMENT ON COLUMN organization_workforce.sync_runs.created_at IS '创建时间';
COMMENT ON COLUMN organization_workforce.sync_runs.updated_at IS '更新时间';
COMMENT ON COLUMN organization_workforce.sync_runs.version IS '乐观锁版本';
COMMENT ON COLUMN organization_workforce.sync_runs.event_count IS '事件序号：该聚合已发出的事件数，每发一个事件加 1，作为事件信封的 aggregateVersion；与乐观锁 version 分开';
COMMENT ON COLUMN organization_workforce.sync_runs.hold_kind_codes IS '暂停类型集合：SHRINK_GUARD 缩水保护 / ADMINISTRATOR_SEPARATION 管理员离职；状态为 HELD 时才有值，held_reason 只是给人看的说明';
COMMENT ON COLUMN organization_workforce.sync_runs.released_kind_codes IS '已解除的暂停类型：全部解除后才按最新源数据重跑并应用；源数据变化使暂停类型或离职名单改变时清空';

CREATE TABLE organization_workforce.sync_diffs (
    id             uuid        PRIMARY KEY,
    sync_run_id    uuid        NOT NULL REFERENCES organization_workforce.sync_runs (id) ON DELETE CASCADE,
    diff_number    integer     NOT NULL CHECK (diff_number >= 1),
    entity_kind    text        NOT NULL CHECK (entity_kind IN ('ORG_UNIT', 'EMPLOYEE', 'POSITION')),
    subject_key    text        NOT NULL,
    change_kind    text        NOT NULL CHECK (change_kind IN ('CREATED', 'UPDATED', 'SEPARATED', 'REHIRED', 'REVOKED', 'REVIVED', 'ISSUE')),
    issue_kind     text,
    detail         text,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    changed_field_codes text[],
    CONSTRAINT uk_sync_diffs_sync_run_id_diff_number UNIQUE (sync_run_id, diff_number)
);
CREATE INDEX sync_diffs_sync_run_id_idx ON organization_workforce.sync_diffs (sync_run_id);
COMMENT ON TABLE organization_workforce.sync_diffs IS '同步差异：每次同步改动的对象与字段，以及数据例外（ISSUE）；粒度：每次同步中每个被改动或有例外的对象一行';
COMMENT ON COLUMN organization_workforce.sync_diffs.id IS '差异 ID（UUIDv7）';
COMMENT ON COLUMN organization_workforce.sync_diffs.sync_run_id IS '同步记录 ID';
COMMENT ON COLUMN organization_workforce.sync_diffs.diff_number IS '差异序号：在同一次同步内按发现顺序从 1 起编号，显示顺序按它排';
COMMENT ON COLUMN organization_workforce.sync_diffs.entity_kind IS '对象类别：ORG_UNIT / EMPLOYEE / POSITION';
COMMENT ON COLUMN organization_workforce.sync_diffs.subject_key IS '对象键，如 employee:0192f3a4-7b1c-7d2e-8f30-5a6b7c8d9e01（类别 + 自有 ID）';
COMMENT ON COLUMN organization_workforce.sync_diffs.change_kind IS '变更类别：CREATED / UPDATED / SEPARATED / REHIRED / REVOKED / REVIVED / ISSUE（数据例外）';
COMMENT ON COLUMN organization_workforce.sync_diffs.issue_kind IS '数据例外类别（仅 ISSUE 行）';
COMMENT ON COLUMN organization_workforce.sync_diffs.detail IS '说明';
COMMENT ON COLUMN organization_workforce.sync_diffs.created_at IS '创建时间';
COMMENT ON COLUMN organization_workforce.sync_diffs.updated_at IS '更新时间';
COMMENT ON COLUMN organization_workforce.sync_diffs.changed_field_codes IS '变更的字段代码，每个字段一个元素；仅 UPDATE 类变更有值';

CREATE TABLE organization_workforce.legal_entities (
    id         uuid        PRIMARY KEY,
    code       text        NOT NULL UNIQUE,
    name       text        NOT NULL,
    status     text        NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    version    bigint      NOT NULL DEFAULT 0,
    event_count bigint     NOT NULL DEFAULT 0,
    CONSTRAINT legal_entities_code_format CHECK (code ~ '^[A-Z]{2,10}$'),
    CONSTRAINT ck_legal_entities_name CHECK (char_length(name) <= 100 AND btrim(name) <> '')
);
COMMENT ON TABLE organization_workforce.legal_entities IS '法人主体：由管理员在新系统维护的法人小表（约 5 家），不从外部系统同步；粒度：每个法人一行，变化历史在 legal_entity_versions';
COMMENT ON COLUMN organization_workforce.legal_entities.id IS '法人 ID（UUIDv7）';
COMMENT ON COLUMN organization_workforce.legal_entities.code IS '法人代码：2 至 10 位大写字母，全表唯一，该法人发过号之前可更正、发号后禁改，用于单据编号的 {LE} 占位符；核算系统中的数字编码不在此表，见 platform_integration.source_references';
COMMENT ON COLUMN organization_workforce.legal_entities.name IS '法人名称';
COMMENT ON COLUMN organization_workforce.legal_entities.status IS '状态：ACTIVE 启用 / INACTIVE 停用';
COMMENT ON COLUMN organization_workforce.legal_entities.created_at IS '创建时间';
COMMENT ON COLUMN organization_workforce.legal_entities.updated_at IS '更新时间';
COMMENT ON COLUMN organization_workforce.legal_entities.version IS '乐观锁版本';
COMMENT ON COLUMN organization_workforce.legal_entities.event_count IS '事件序号：该聚合已发出的事件数，每发一个事件加 1，作为事件信封的 aggregateVersion；与乐观锁 version 分开';

-- ADR-0026 第 2 项：员工与部门的法人归属带生效区间，按日期解析；数据打分卡 T1：组织单元与任职的变化历史在交易库保留。
-- 区间一律左闭右开：effective_from_date 当天起有效，effective_to_date 当天不再有效，为空表示至今。
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE organization_workforce.legal_entity_attributions (
    attribution_id uuid        NOT NULL,
    org_unit_id    uuid,
    employee_id    uuid,
    version        bigint      NOT NULL DEFAULT 0,
    event_count    bigint      NOT NULL DEFAULT 0,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_legal_entity_attributions PRIMARY KEY (attribution_id),
    CONSTRAINT fk_legal_entity_attributions_org_unit_id FOREIGN KEY (org_unit_id) REFERENCES organization_workforce.org_units (id),
    CONSTRAINT fk_legal_entity_attributions_employee_id FOREIGN KEY (employee_id) REFERENCES organization_workforce.employees (id),
    CONSTRAINT uk_legal_entity_attributions_org_unit_id UNIQUE (org_unit_id),
    CONSTRAINT uk_legal_entity_attributions_employee_id UNIQUE (employee_id),
    CONSTRAINT ck_legal_entity_attributions_subject CHECK (num_nonnulls(org_unit_id, employee_id) = 1)
);
COMMENT ON TABLE organization_workforce.legal_entity_attributions IS '法人归属：部门或员工的法人归属聚合的版本与事件序号；粒度：每个部门、每个员工至多一行；归属期间在 org_unit_legal_entities 与 employee_legal_entities';
COMMENT ON COLUMN organization_workforce.legal_entity_attributions.attribution_id IS '法人归属 ID';
COMMENT ON COLUMN organization_workforce.legal_entity_attributions.org_unit_id IS '组织单元 ID：归属对象是部门时填（子部门继承，可在子树上覆盖）；为空表示归属对象是另一类（员工）；与 employee_id 恰有一个非空';
COMMENT ON COLUMN organization_workforce.legal_entity_attributions.employee_id IS '员工 ID：归属对象是员工时填（单独指定优先于部门）；为空表示归属对象是另一类（部门）；与 org_unit_id 恰有一个非空';
COMMENT ON COLUMN organization_workforce.legal_entity_attributions.version IS '乐观锁版本';
COMMENT ON COLUMN organization_workforce.legal_entity_attributions.event_count IS '事件序号：该聚合已发出的事件数，每发一个事件加 1，作为事件信封的 aggregateVersion；与乐观锁 version 分开';
COMMENT ON COLUMN organization_workforce.legal_entity_attributions.created_at IS '创建时间';
COMMENT ON COLUMN organization_workforce.legal_entity_attributions.updated_at IS '更新时间';

CREATE TABLE organization_workforce.org_unit_legal_entities (
    org_unit_legal_entity_id uuid   NOT NULL,
    org_unit_id         uuid        NOT NULL,
    effective_from_date date        NOT NULL,
    effective_to_date   date,
    legal_entity_id     uuid        NOT NULL,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_org_unit_legal_entities PRIMARY KEY (org_unit_legal_entity_id),
    CONSTRAINT uk_org_unit_legal_entities_effective_from UNIQUE (org_unit_id, effective_from_date),
    CONSTRAINT fk_org_unit_legal_entities_org_unit_id FOREIGN KEY (org_unit_id) REFERENCES organization_workforce.org_units (id),
    CONSTRAINT fk_org_unit_legal_entities_legal_entity_id FOREIGN KEY (legal_entity_id) REFERENCES organization_workforce.legal_entities (id),
    CONSTRAINT fk_org_unit_legal_entities_root_org_unit_id FOREIGN KEY (org_unit_id) REFERENCES organization_workforce.legal_entity_attributions (org_unit_id),
    CONSTRAINT ck_org_unit_legal_entities_range CHECK (effective_to_date IS NULL OR effective_to_date > effective_from_date),
    CONSTRAINT ex_org_unit_legal_entities_overlap EXCLUDE USING gist (org_unit_id WITH =, daterange(effective_from_date, effective_to_date) WITH &&)
);
COMMENT ON TABLE organization_workforce.org_unit_legal_entities IS '部门法人对照：部门默认归属的法人，子部门继承，可在子树上覆盖；生效区间维度，只追加（开放期间只能被关闭，已关闭的期间不改，结束日写错时用更正记录）；粒度：每个部门的每个归属期间一行，同一部门的期间不重叠';
COMMENT ON COLUMN organization_workforce.org_unit_legal_entities.org_unit_legal_entity_id IS '组织单元法人归属期间行的代理主键（UUIDv7，不是法人 ID）；自然键为组织单元 ID + 生效日';
COMMENT ON COLUMN organization_workforce.org_unit_legal_entities.org_unit_id IS '组织单元 ID：被归属的部门';
COMMENT ON COLUMN organization_workforce.org_unit_legal_entities.effective_from_date IS '生效日（含）';
COMMENT ON COLUMN organization_workforce.org_unit_legal_entities.effective_to_date IS '失效日（不含）；为空表示至今';
COMMENT ON COLUMN organization_workforce.org_unit_legal_entities.legal_entity_id IS '法人主体 ID：归属的法人';
COMMENT ON COLUMN organization_workforce.org_unit_legal_entities.created_at IS '创建时间';
COMMENT ON COLUMN organization_workforce.org_unit_legal_entities.updated_at IS '更新时间';

CREATE TABLE organization_workforce.employee_legal_entities (
    employee_legal_entity_id uuid   NOT NULL,
    employee_id         uuid        NOT NULL,
    effective_from_date date        NOT NULL,
    effective_to_date   date,
    legal_entity_id     uuid        NOT NULL,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_employee_legal_entities PRIMARY KEY (employee_legal_entity_id),
    CONSTRAINT uk_employee_legal_entities_effective_from UNIQUE (employee_id, effective_from_date),
    CONSTRAINT fk_employee_legal_entities_employee_id FOREIGN KEY (employee_id) REFERENCES organization_workforce.employees (id),
    CONSTRAINT fk_employee_legal_entities_legal_entity_id FOREIGN KEY (legal_entity_id) REFERENCES organization_workforce.legal_entities (id),
    CONSTRAINT fk_employee_legal_entities_root_employee_id FOREIGN KEY (employee_id) REFERENCES organization_workforce.legal_entity_attributions (employee_id),
    CONSTRAINT ck_employee_legal_entities_range CHECK (effective_to_date IS NULL OR effective_to_date > effective_from_date),
    CONSTRAINT ex_employee_legal_entities_overlap EXCLUDE USING gist (employee_id WITH =, daterange(effective_from_date, effective_to_date) WITH &&)
);
COMMENT ON TABLE organization_workforce.employee_legal_entities IS '员工法人归属：个别员工单独指定的法人，优先于部门对照；生效区间维度，只追加（开放期间只能被关闭，已关闭的期间不改，结束日写错时用更正记录）；粒度：每个员工的每个归属期间一行，同一员工的期间不重叠';
COMMENT ON COLUMN organization_workforce.employee_legal_entities.employee_legal_entity_id IS '员工法人归属期间行的代理主键（UUIDv7，不是法人 ID）；自然键为员工 ID + 生效日';
COMMENT ON COLUMN organization_workforce.employee_legal_entities.employee_id IS '员工 ID：被归属的员工';
COMMENT ON COLUMN organization_workforce.employee_legal_entities.effective_from_date IS '生效日（含）';
COMMENT ON COLUMN organization_workforce.employee_legal_entities.effective_to_date IS '失效日（不含）；为空表示至今';
COMMENT ON COLUMN organization_workforce.employee_legal_entities.legal_entity_id IS '法人主体 ID：归属的法人';
COMMENT ON COLUMN organization_workforce.employee_legal_entities.created_at IS '创建时间';
COMMENT ON COLUMN organization_workforce.employee_legal_entities.updated_at IS '更新时间';

CREATE TABLE organization_workforce.org_unit_versions (
    org_unit_version_id             uuid        NOT NULL,
    org_unit_id                     uuid        NOT NULL,
    effective_from_date                  date        NOT NULL,
    effective_to_date                    date,
    code                            text,
    name                            text        NOT NULL,
    parent_org_unit_id              uuid,
    status                          text        NOT NULL CONSTRAINT ck_org_unit_versions_status CHECK (status IN ('ACTIVE', 'DISABLED', 'REVOKED')),
    head_employee_id                uuid,
    regional_receivables_manager_id uuid,
    region_kind                 text CONSTRAINT ck_org_unit_versions_region_kind CHECK (region_kind IN ('HEADQUARTERS', 'REGIONAL')),
    is_delivery_unit                boolean     NOT NULL,
    created_at                      timestamptz NOT NULL DEFAULT now(),
    updated_at                      timestamptz NOT NULL DEFAULT now(),
    revision_number                 integer     NOT NULL DEFAULT 0,
    CONSTRAINT pk_org_unit_versions PRIMARY KEY (org_unit_version_id),
    CONSTRAINT uk_org_unit_versions_revision UNIQUE (org_unit_id, effective_from_date, revision_number),
    CONSTRAINT fk_org_unit_versions_org_unit_id FOREIGN KEY (org_unit_id) REFERENCES organization_workforce.org_units (id),
    CONSTRAINT ck_org_unit_versions_name CHECK (char_length(name) <= 500 AND btrim(name) <> ''),
    CONSTRAINT ck_org_unit_versions_code CHECK (code IS NULL OR (char_length(code) <= 500 AND btrim(code) <> '')),
    CONSTRAINT ck_org_unit_versions_range CHECK (effective_to_date IS NULL OR effective_to_date >= effective_from_date),
    CONSTRAINT ex_org_unit_versions_overlap EXCLUDE USING gist (org_unit_id WITH =, daterange(effective_from_date, effective_to_date) WITH &&)
);
COMMENT ON TABLE organization_workforce.org_unit_versions IS '组织单元版本：组织单元的变化历史，只追加（开放版本只能被关闭，同一天的再次变化关闭当天版本并追加下一修订）；粒度：每个组织单元的每个生效区间每个修订一行；与快照在同一事务写入';
COMMENT ON COLUMN organization_workforce.org_unit_versions.org_unit_version_id IS '组织单元版本 ID（UUIDv7）';
COMMENT ON COLUMN organization_workforce.org_unit_versions.org_unit_id IS '组织单元 ID';
COMMENT ON COLUMN organization_workforce.org_unit_versions.effective_from_date IS '生效日（含），按业务时区截日';
COMMENT ON COLUMN organization_workforce.org_unit_versions.effective_to_date IS '失效日（不含）；为空表示至今；等于生效日表示当天被同日再次变化取代的空区间';
COMMENT ON COLUMN organization_workforce.org_unit_versions.code IS '组织单元编码';
COMMENT ON COLUMN organization_workforce.org_unit_versions.name IS '组织单元名称';
COMMENT ON COLUMN organization_workforce.org_unit_versions.parent_org_unit_id IS '上级组织单元 ID；根为空';
COMMENT ON COLUMN organization_workforce.org_unit_versions.status IS '状态：ACTIVE 有效 / DISABLED 停用 / REVOKED 已撤销';
COMMENT ON COLUMN organization_workforce.org_unit_versions.head_employee_id IS '部门负责人（员工 ID）';
COMMENT ON COLUMN organization_workforce.org_unit_versions.regional_receivables_manager_id IS '区域应收负责人（员工 ID）';
COMMENT ON COLUMN organization_workforce.org_unit_versions.region_kind IS '总部 / 区域类别';
COMMENT ON COLUMN organization_workforce.org_unit_versions.is_delivery_unit IS '是否交付部门';
COMMENT ON COLUMN organization_workforce.org_unit_versions.created_at IS '创建时间';
COMMENT ON COLUMN organization_workforce.org_unit_versions.updated_at IS '更新时间';
COMMENT ON COLUMN organization_workforce.org_unit_versions.revision_number IS '修订序号：同一生效日内的序号，同日再次变化时关闭当天版本（空区间）并以加 1 的序号追加新行';

CREATE TABLE organization_workforce.assignment_versions (
    assignment_version_id  uuid        NOT NULL,
    employee_id            uuid        NOT NULL,
    effective_from_date         date        NOT NULL,
    effective_to_date           date,
    org_unit_id            uuid        NOT NULL,
    position_id            uuid,
    reports_to_employee_id uuid,
    created_at             timestamptz NOT NULL DEFAULT now(),
    updated_at             timestamptz NOT NULL DEFAULT now(),
    revision_number        integer     NOT NULL DEFAULT 0,
    CONSTRAINT pk_assignment_versions PRIMARY KEY (assignment_version_id),
    CONSTRAINT uk_assignment_versions_revision UNIQUE (employee_id, effective_from_date, revision_number),
    CONSTRAINT fk_assignment_versions_employee_id FOREIGN KEY (employee_id) REFERENCES organization_workforce.employees (id),
    CONSTRAINT fk_assignment_versions_org_unit_id FOREIGN KEY (org_unit_id) REFERENCES organization_workforce.org_units (id),
    CONSTRAINT ck_assignment_versions_range CHECK (effective_to_date IS NULL OR effective_to_date >= effective_from_date),
    CONSTRAINT ex_assignment_versions_overlap EXCLUDE USING gist (employee_id WITH =, daterange(effective_from_date, effective_to_date) WITH &&)
);
COMMENT ON TABLE organization_workforce.assignment_versions IS '任职版本：员工所在部门、岗位与汇报线的变化历史，只追加（开放版本只能被关闭，同一天的再次变化关闭当天版本并追加下一修订）；粒度：每个员工的每个生效区间每个修订一行；与快照在同一事务写入';
COMMENT ON COLUMN organization_workforce.assignment_versions.assignment_version_id IS '任职版本 ID（UUIDv7）';
COMMENT ON COLUMN organization_workforce.assignment_versions.employee_id IS '员工 ID';
COMMENT ON COLUMN organization_workforce.assignment_versions.effective_from_date IS '生效日（含），按业务时区截日';
COMMENT ON COLUMN organization_workforce.assignment_versions.effective_to_date IS '失效日（不含）；为空表示至今；等于生效日表示当天被同日再次变化取代的空区间';
COMMENT ON COLUMN organization_workforce.assignment_versions.org_unit_id IS '组织单元 ID：员工所在的组织单元';
COMMENT ON COLUMN organization_workforce.assignment_versions.position_id IS '岗位 ID';
COMMENT ON COLUMN organization_workforce.assignment_versions.reports_to_employee_id IS '直属领导（员工 ID）';
COMMENT ON COLUMN organization_workforce.assignment_versions.created_at IS '创建时间';
COMMENT ON COLUMN organization_workforce.assignment_versions.updated_at IS '更新时间';
COMMENT ON COLUMN organization_workforce.assignment_versions.revision_number IS '修订序号：同一生效日内的序号，同日再次变化时关闭当天版本（空区间）并以加 1 的序号追加新行';

-- 数据打分卡 K4：法人的代码、名称、状态按生效区间保留历史，只追加；与法人主表在同一事务写入，同日再次变化关闭当天版本并追加下一修订。
CREATE TABLE organization_workforce.legal_entity_versions (
    legal_entity_version_id uuid    NOT NULL,
    legal_entity_id     uuid        NOT NULL,
    effective_from_date date        NOT NULL,
    revision_number     integer     NOT NULL DEFAULT 0,
    effective_to_date   date,
    code                text        NOT NULL,
    name                text        NOT NULL,
    status              text        NOT NULL CONSTRAINT ck_legal_entity_versions_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_legal_entity_versions PRIMARY KEY (legal_entity_version_id),
    CONSTRAINT uk_legal_entity_versions_revision UNIQUE (legal_entity_id, effective_from_date, revision_number),
    CONSTRAINT fk_legal_entity_versions_legal_entity_id FOREIGN KEY (legal_entity_id) REFERENCES organization_workforce.legal_entities (id),
    CONSTRAINT ck_legal_entity_versions_code_format CHECK (code ~ '^[A-Z]{2,10}$'),
    CONSTRAINT ck_legal_entity_versions_name CHECK (char_length(name) <= 100 AND btrim(name) <> ''),
    CONSTRAINT ck_legal_entity_versions_range CHECK (effective_to_date IS NULL OR effective_to_date >= effective_from_date),
    CONSTRAINT ex_legal_entity_versions_overlap EXCLUDE USING gist (legal_entity_id WITH =, daterange(effective_from_date, effective_to_date) WITH &&)
);
COMMENT ON TABLE organization_workforce.legal_entity_versions IS '法人版本：法人代码、名称、状态的变化历史，只追加；粒度：每个法人的每个生效区间每个修订一行';
COMMENT ON COLUMN organization_workforce.legal_entity_versions.legal_entity_version_id IS '法人版本 ID（UUIDv7）';
COMMENT ON COLUMN organization_workforce.legal_entity_versions.legal_entity_id IS '法人主体 ID';
COMMENT ON COLUMN organization_workforce.legal_entity_versions.effective_from_date IS '生效日（含），按业务时区截日';
COMMENT ON COLUMN organization_workforce.legal_entity_versions.revision_number IS '修订序号：同一生效日内的序号，同日再次变化时关闭当天版本（空区间）并以加 1 的序号追加新行';
COMMENT ON COLUMN organization_workforce.legal_entity_versions.effective_to_date IS '失效日（不含）；为空表示至今；等于生效日表示当天被同日再次变化取代的空区间';
COMMENT ON COLUMN organization_workforce.legal_entity_versions.code IS '法人代码';
COMMENT ON COLUMN organization_workforce.legal_entity_versions.name IS '法人名称';
COMMENT ON COLUMN organization_workforce.legal_entity_versions.status IS '状态：ACTIVE 启用 / INACTIVE 停用';
COMMENT ON COLUMN organization_workforce.legal_entity_versions.created_at IS '创建时间';
COMMENT ON COLUMN organization_workforce.legal_entity_versions.updated_at IS '更新时间';

-- RULE-385：已关闭的法人归属期间不改写；结束日写错时追加一条更正记录。本表只追加，触发器拒绝修改、删除和清空。
CREATE TABLE organization_workforce.legal_entity_attribution_corrections (
    correction_id                uuid        NOT NULL,
    correction_number            bigint      GENERATED ALWAYS AS IDENTITY,
    org_unit_id                  uuid,
    employee_id                  uuid,
    period_effective_from_date   date        NOT NULL,
    previous_effective_to_date   date        NOT NULL,
    new_effective_to_date        date        NOT NULL,
    correction_reason            text        NOT NULL,
    corrected_by_user_id         uuid        NOT NULL,
    created_at                   timestamptz NOT NULL DEFAULT now(),
    updated_at                   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_legal_entity_attribution_corrections PRIMARY KEY (correction_id),
    CONSTRAINT uk_legal_entity_attribution_corrections_correction_number UNIQUE (correction_number),
    CONSTRAINT ck_legal_entity_attribution_corrections_range CHECK (new_effective_to_date > period_effective_from_date),
    CONSTRAINT ck_legal_entity_attribution_corrections_reason CHECK (char_length(correction_reason) <= 200 AND btrim(correction_reason) <> ''),
    CONSTRAINT fk_legal_entity_attribution_corrections_org_unit_id FOREIGN KEY (org_unit_id) REFERENCES organization_workforce.org_units (id),
    CONSTRAINT fk_legal_entity_attribution_corrections_employee_id FOREIGN KEY (employee_id) REFERENCES organization_workforce.employees (id),
    CONSTRAINT fk_legal_entity_attribution_corrections_root_org_unit_id FOREIGN KEY (org_unit_id) REFERENCES organization_workforce.legal_entity_attributions (org_unit_id),
    CONSTRAINT fk_legal_entity_attribution_corrections_root_employee_id FOREIGN KEY (employee_id) REFERENCES organization_workforce.legal_entity_attributions (employee_id),
    CONSTRAINT ck_legal_entity_attribution_corrections_changes CHECK (new_effective_to_date <> previous_effective_to_date),
    CONSTRAINT ck_legal_entity_attribution_corrections_subject CHECK (num_nonnulls(org_unit_id, employee_id) = 1)
);
CREATE INDEX ix_legal_entity_attribution_corrections_org_unit_id ON organization_workforce.legal_entity_attribution_corrections
    (org_unit_id, period_effective_from_date, correction_number DESC);
CREATE INDEX ix_legal_entity_attribution_corrections_employee_id ON organization_workforce.legal_entity_attribution_corrections
    (employee_id, period_effective_from_date, correction_number DESC);
COMMENT ON TABLE organization_workforce.legal_entity_attribution_corrections IS '法人归属更正记录：只追加；解析法人时，期间的结束日取该期间最新一条更正的新结束日；粒度：每次更正一行';
COMMENT ON COLUMN organization_workforce.legal_entity_attribution_corrections.correction_id IS '更正记录 ID';
COMMENT ON COLUMN organization_workforce.legal_entity_attribution_corrections.correction_number IS '更正序号：全表递增，同一期间多次更正时序号最大的为准';
COMMENT ON COLUMN organization_workforce.legal_entity_attribution_corrections.org_unit_id IS '组织单元 ID：被更正的是部门的归属期间时填；为空表示归属对象是另一类（员工）；与 employee_id 恰有一个非空';
COMMENT ON COLUMN organization_workforce.legal_entity_attribution_corrections.employee_id IS '员工 ID：被更正的是员工的归属期间时填；为空表示归属对象是另一类（部门）；与 org_unit_id 恰有一个非空';
COMMENT ON COLUMN organization_workforce.legal_entity_attribution_corrections.period_effective_from_date IS '被更正期间的生效日（含）：与期间表中该期间的生效日相同';
COMMENT ON COLUMN organization_workforce.legal_entity_attribution_corrections.previous_effective_to_date IS '更正前的结束日（不含）';
COMMENT ON COLUMN organization_workforce.legal_entity_attribution_corrections.new_effective_to_date IS '更正后的结束日（不含）';
COMMENT ON COLUMN organization_workforce.legal_entity_attribution_corrections.correction_reason IS '更正原因：操作人填写，不得为空，最多 200 个字符';
COMMENT ON COLUMN organization_workforce.legal_entity_attribution_corrections.corrected_by_user_id IS '操作人用户 ID：来自登录会话';
COMMENT ON COLUMN organization_workforce.legal_entity_attribution_corrections.created_at IS '创建时间';
COMMENT ON COLUMN organization_workforce.legal_entity_attribution_corrections.updated_at IS '更新时间（行不可更新，恒等于创建时间）';

CREATE TABLE organization_workforce.sync_run_held_separations (
    sync_run_held_separation_id uuid NOT NULL,
    sync_run_id uuid        NOT NULL,
    employee_id uuid        NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_sync_run_held_separations PRIMARY KEY (sync_run_held_separation_id),
    CONSTRAINT uk_sync_run_held_separations_sync_run_id_employee_id UNIQUE (sync_run_id, employee_id),
    CONSTRAINT fk_sync_run_held_separations_sync_run_id FOREIGN KEY (sync_run_id) REFERENCES organization_workforce.sync_runs (id) ON DELETE CASCADE,
    CONSTRAINT fk_sync_run_held_separations_employee_id FOREIGN KEY (employee_id) REFERENCES organization_workforce.employees (id)
);
CREATE INDEX ix_sync_run_held_separations_employee_id ON organization_workforce.sync_run_held_separations (employee_id);
COMMENT ON TABLE organization_workforce.sync_run_held_separations IS '被暂停的同步运行当时的离职名单：放行重跑时比对是否出现新的管理员离职，并供有放行权限的人只读查看。粒度：每个暂停的运行的每个待离职员工一行';
COMMENT ON COLUMN organization_workforce.sync_run_held_separations.sync_run_held_separation_id IS '暂停同步待离职员工 ID（UUIDv7）';
COMMENT ON COLUMN organization_workforce.sync_run_held_separations.sync_run_id IS '同步记录 ID：被暂停的同步';
COMMENT ON COLUMN organization_workforce.sync_run_held_separations.employee_id IS '员工 ID：待离职';
COMMENT ON COLUMN organization_workforce.sync_run_held_separations.created_at IS '创建时间';
COMMENT ON COLUMN organization_workforce.sync_run_held_separations.updated_at IS '更新时间';

-- 数据打分卡 K6：同一 schema 内的引用补外键。快照按「部门、人员」的顺序写入，引用在提交时才检查（DEFERRABLE INITIALLY DEFERRED）。
ALTER TABLE organization_workforce.org_units
    ADD CONSTRAINT fk_org_units_parent_org_unit_id FOREIGN KEY (parent_org_unit_id)
        REFERENCES organization_workforce.org_units (id) DEFERRABLE INITIALLY DEFERRED,
    ADD CONSTRAINT fk_org_units_head_employee_id FOREIGN KEY (head_employee_id)
        REFERENCES organization_workforce.employees (id) DEFERRABLE INITIALLY DEFERRED,
    ADD CONSTRAINT fk_org_units_regional_receivables_manager_id FOREIGN KEY (regional_receivables_manager_id)
        REFERENCES organization_workforce.employees (id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE organization_workforce.assignments
    ADD CONSTRAINT fk_assignments_reports_to_employee_id FOREIGN KEY (reports_to_employee_id)
        REFERENCES organization_workforce.employees (id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE organization_workforce.org_unit_versions
    ADD CONSTRAINT fk_org_unit_versions_parent_org_unit_id FOREIGN KEY (parent_org_unit_id)
        REFERENCES organization_workforce.org_units (id) DEFERRABLE INITIALLY DEFERRED,
    ADD CONSTRAINT fk_org_unit_versions_head_employee_id FOREIGN KEY (head_employee_id)
        REFERENCES organization_workforce.employees (id) DEFERRABLE INITIALLY DEFERRED,
    ADD CONSTRAINT fk_org_unit_versions_regional_receivables_manager_id FOREIGN KEY (regional_receivables_manager_id)
        REFERENCES organization_workforce.employees (id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE organization_workforce.assignment_versions
    ADD CONSTRAINT fk_assignment_versions_position_id FOREIGN KEY (position_id)
        REFERENCES organization_workforce.positions (id) DEFERRABLE INITIALLY DEFERRED,
    ADD CONSTRAINT fk_assignment_versions_reports_to_employee_id FOREIGN KEY (reports_to_employee_id)
        REFERENCES organization_workforce.employees (id) DEFERRABLE INITIALLY DEFERRED;

CREATE INDEX ix_org_units_head_employee_id ON organization_workforce.org_units (head_employee_id);
CREATE INDEX ix_org_units_regional_receivables_manager_id ON organization_workforce.org_units (regional_receivables_manager_id);
CREATE INDEX ix_org_unit_legal_entities_legal_entity_id ON organization_workforce.org_unit_legal_entities (legal_entity_id);
CREATE INDEX ix_employee_legal_entities_legal_entity_id ON organization_workforce.employee_legal_entities (legal_entity_id);
CREATE INDEX ix_assignment_versions_org_unit_id ON organization_workforce.assignment_versions (org_unit_id, effective_from_date);
CREATE INDEX ix_assignments_reports_to_employee_id ON organization_workforce.assignments (reports_to_employee_id);
CREATE INDEX ix_org_unit_versions_parent_org_unit_id ON organization_workforce.org_unit_versions (parent_org_unit_id);
CREATE INDEX ix_org_unit_versions_head_employee_id ON organization_workforce.org_unit_versions (head_employee_id);
CREATE INDEX ix_org_unit_versions_regional_receivables_manager_id ON organization_workforce.org_unit_versions (regional_receivables_manager_id);
CREATE INDEX ix_assignment_versions_position_id ON organization_workforce.assignment_versions (position_id);
CREATE INDEX ix_assignment_versions_reports_to_employee_id ON organization_workforce.assignment_versions (reports_to_employee_id);

CREATE FUNCTION organization_workforce.reject_attribution_correction_change() RETURNS trigger
    LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
BEGIN
    RAISE EXCEPTION 'organization_workforce.legal_entity_attribution_corrections is append-only: % is not allowed', TG_OP
        USING ERRCODE = 'restrict_violation';
END;
$$;

CREATE TRIGGER legal_entity_attribution_corrections_reject_update_delete
    BEFORE UPDATE OR DELETE ON organization_workforce.legal_entity_attribution_corrections
    FOR EACH ROW EXECUTE FUNCTION organization_workforce.reject_attribution_correction_change();

CREATE TRIGGER legal_entity_attribution_corrections_reject_truncate
    BEFORE TRUNCATE ON organization_workforce.legal_entity_attribution_corrections
    FOR EACH STATEMENT EXECUTE FUNCTION organization_workforce.reject_attribution_correction_change();

CREATE FUNCTION organization_workforce.refuse_version_rewrite() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
BEGIN
    IF TG_OP IN ('DELETE', 'TRUNCATE') THEN
        RAISE EXCEPTION 'version rows are append-only: % on %.% is refused', lower(TG_OP), TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
    END IF;
    IF OLD.effective_to_date IS NOT NULL THEN
        RAISE EXCEPTION 'a closed version row cannot change: %.%', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
    END IF;
    IF NEW.effective_to_date IS NULL OR (to_jsonb(NEW) - 'effective_to_date' - 'updated_at') <> (to_jsonb(OLD) - 'effective_to_date' - 'updated_at') THEN
        RAISE EXCEPTION 'an open version row can only be closed: %.%', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END $$;
COMMENT ON FUNCTION organization_workforce.refuse_version_rewrite() IS '版本表只追加的触发器：只允许关闭开放版本，已关闭的行不能改，任何行不能删';

-- 版本表与法人归属期间表只追加：开放行只能被关闭，已关闭的行不能改，任何行不能删或清空。
CREATE TRIGGER tg_org_unit_versions_append_only BEFORE UPDATE OR DELETE ON organization_workforce.org_unit_versions
    FOR EACH ROW EXECUTE FUNCTION organization_workforce.refuse_version_rewrite();
CREATE TRIGGER tg_org_unit_versions_append_only_truncate BEFORE TRUNCATE ON organization_workforce.org_unit_versions
    FOR EACH STATEMENT EXECUTE FUNCTION organization_workforce.refuse_version_rewrite();
CREATE TRIGGER tg_assignment_versions_append_only BEFORE UPDATE OR DELETE ON organization_workforce.assignment_versions
    FOR EACH ROW EXECUTE FUNCTION organization_workforce.refuse_version_rewrite();
CREATE TRIGGER tg_assignment_versions_append_only_truncate BEFORE TRUNCATE ON organization_workforce.assignment_versions
    FOR EACH STATEMENT EXECUTE FUNCTION organization_workforce.refuse_version_rewrite();
CREATE TRIGGER tg_legal_entity_versions_append_only BEFORE UPDATE OR DELETE ON organization_workforce.legal_entity_versions
    FOR EACH ROW EXECUTE FUNCTION organization_workforce.refuse_version_rewrite();
CREATE TRIGGER tg_legal_entity_versions_append_only_truncate BEFORE TRUNCATE ON organization_workforce.legal_entity_versions
    FOR EACH STATEMENT EXECUTE FUNCTION organization_workforce.refuse_version_rewrite();
CREATE TRIGGER tg_org_unit_legal_entities_append_only BEFORE UPDATE OR DELETE ON organization_workforce.org_unit_legal_entities
    FOR EACH ROW EXECUTE FUNCTION organization_workforce.refuse_version_rewrite();
CREATE TRIGGER tg_org_unit_legal_entities_append_only_truncate BEFORE TRUNCATE ON organization_workforce.org_unit_legal_entities
    FOR EACH STATEMENT EXECUTE FUNCTION organization_workforce.refuse_version_rewrite();
CREATE TRIGGER tg_employee_legal_entities_append_only BEFORE UPDATE OR DELETE ON organization_workforce.employee_legal_entities
    FOR EACH ROW EXECUTE FUNCTION organization_workforce.refuse_version_rewrite();
CREATE TRIGGER tg_employee_legal_entities_append_only_truncate BEFORE TRUNCATE ON organization_workforce.employee_legal_entities
    FOR EACH STATEMENT EXECUTE FUNCTION organization_workforce.refuse_version_rewrite();
