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
ALTER TABLE organization_workforce.employees ADD CONSTRAINT uk_employees_employee_code UNIQUE (employee_code);
COMMENT ON TABLE organization_workforce.employees IS '员工：组织源人员的快照；没有登录名即离职（RULE-363），不含生日等敏感信息；粒度：每个员工一行';
COMMENT ON COLUMN organization_workforce.employees.id IS '员工 ID（UUIDv7；组织源人员经来源对照映射）';
COMMENT ON COLUMN organization_workforce.employees.employee_code IS '员工编号（工号）；全局唯一，复职沿用原工号（用户 2026-10-21，W2 设计 3.1）';
COMMENT ON COLUMN organization_workforce.employees.display_name IS '姓名';
COMMENT ON COLUMN organization_workforce.employees.login_name IS '登录名；为空（null 或空串）表示已离职（RULE-363）';
COMMENT ON COLUMN organization_workforce.employees.email IS '企业邮箱';
COMMENT ON COLUMN organization_workforce.employees.gender IS '性别：MALE / FEMALE；组织源未维护或无法识别为空（不再默认写成女）';
COMMENT ON COLUMN organization_workforce.employees.wecom_user_id IS '企业微信 userid（企业微信登录与通知的唯一键，建档时写入，RULE-362）；在职员工中唯一';
COMMENT ON COLUMN organization_workforce.employees.employment_status IS '在职状态：EMPLOYED 在职 / SEPARATED 离职（含不在组织源结果中的离职候选，RULE-366）';
COMMENT ON COLUMN organization_workforce.employees.is_fee_earner IS '是否收费员工：组织源的人事分类，只是可计费性判定的输入之一，不等于可计费';
COMMENT ON COLUMN organization_workforce.employees.separated_at IS '离职时间（首次同步到离职的时间）';
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
    change_channel_kind    text        NOT NULL DEFAULT 'MIGRATION',
    change_command_id      uuid,
    version_audit_entry_id uuid,
    closed_at              timestamptz,
    superseded_at          timestamptz,
    superseding_command_id uuid,
    corrected_from_version_id uuid,
    CONSTRAINT pk_assignment_versions PRIMARY KEY (assignment_version_id),
    CONSTRAINT uk_assignment_versions_revision UNIQUE (employee_id, effective_from_date, revision_number),
    CONSTRAINT fk_assignment_versions_employee_id FOREIGN KEY (employee_id) REFERENCES organization_workforce.employees (id),
    CONSTRAINT fk_assignment_versions_org_unit_id FOREIGN KEY (org_unit_id) REFERENCES organization_workforce.org_units (id),
    CONSTRAINT fk_assignment_versions_corrected_from_version_id FOREIGN KEY (corrected_from_version_id) REFERENCES organization_workforce.assignment_versions (assignment_version_id) ON DELETE RESTRICT,
    CONSTRAINT ck_assignment_versions_range CHECK (effective_to_date IS NULL OR effective_to_date >= effective_from_date),
    CONSTRAINT ck_assignment_versions_finite CHECK (isfinite(effective_from_date) AND (effective_to_date IS NULL OR isfinite(effective_to_date))),
    CONSTRAINT ck_assignment_versions_change_channel_kind CHECK (change_channel_kind IN ('COMMAND', 'MIGRATION')),
    CONSTRAINT ck_assignment_versions_command CHECK ((change_channel_kind = 'COMMAND') = (change_command_id IS NOT NULL)),
    CONSTRAINT ck_assignment_versions_audit CHECK (change_channel_kind <> 'COMMAND' OR version_audit_entry_id IS NOT NULL),
    CONSTRAINT ck_assignment_versions_command_position CHECK (change_channel_kind <> 'COMMAND' OR position_id IS NOT NULL),
    CONSTRAINT ck_assignment_versions_closed CHECK ((effective_to_date IS NULL) = (closed_at IS NULL)),
    CONSTRAINT ck_assignment_versions_superseded CHECK ((superseded_at IS NULL) = (superseding_command_id IS NULL)),
    CONSTRAINT ck_assignment_versions_reports_to_not_self CHECK (reports_to_employee_id IS DISTINCT FROM employee_id),
    CONSTRAINT ex_assignment_versions_overlap EXCLUDE USING gist (employee_id WITH =, daterange(effective_from_date, effective_to_date) WITH &&) WHERE (superseded_at IS NULL)
);
COMMENT ON TABLE organization_workforce.assignment_versions IS '任职版本：员工所在部门、岗位与汇报线的变化历史，只追加（开放版本只能被关闭，同一天的再次变化关闭当天版本并追加下一修订；作废只能由空变非空一次）；生效依据为人事业务生效日（DECLARED）；粒度：每个员工的每个生效区间每个修订一行；与快照在同一事务写入';
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
COMMENT ON COLUMN organization_workforce.assignment_versions.change_channel_kind IS '写入渠道：COMMAND 人事命令 / MIGRATION 迁入；缺省 MIGRATION，同步写入的行（旧系统来源）取缺省，人事命令写入的行必须显式写 COMMAND，忘写时由命令列的检查约束拒绝';
COMMENT ON COLUMN organization_workforce.assignment_versions.change_command_id IS '写入本行的人事命令 ID；为空表示迁入行';
COMMENT ON COLUMN organization_workforce.assignment_versions.version_audit_entry_id IS '写入本行的同一事务里写下的审计条目 ID（跨 schema 只存 ID）；迁入行可空';
COMMENT ON COLUMN organization_workforce.assignment_versions.closed_at IS '关闭时刻：写失效日的同时写入，由空变非空一次；为空表示开放版本';
COMMENT ON COLUMN organization_workforce.assignment_versions.superseded_at IS '作废时刻：被更正或补登替换的行，由空变非空一次，开放行与已关闭行都可作废；为空表示当前有效的事实';
COMMENT ON COLUMN organization_workforce.assignment_versions.superseding_command_id IS '作废它的人事命令 ID；为空表示未被作废';
COMMENT ON COLUMN organization_workforce.assignment_versions.corrected_from_version_id IS '重新追加行的来源：指向被作废的行；为空表示不是重新追加的行';

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
CREATE FUNCTION organization_workforce.refuse_assignment_version_rewrite() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
DECLARE
    column_name text;
BEGIN
    IF TG_OP IN ('DELETE', 'TRUNCATE') THEN
        RAISE EXCEPTION 'version rows are append-only: % on %.% is refused', lower(TG_OP), TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
    END IF;
    FOREACH column_name IN ARRAY ARRAY['effective_to_date', 'closed_at', 'superseded_at', 'superseding_command_id'] LOOP
        IF (to_jsonb(OLD) -> column_name) <> 'null'::jsonb AND (to_jsonb(NEW) -> column_name) IS DISTINCT FROM (to_jsonb(OLD) -> column_name) THEN
            RAISE EXCEPTION 'column % of %.% is set once and cannot change', column_name, TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
        END IF;
    END LOOP;
    IF (to_jsonb(NEW) - 'effective_to_date' - 'closed_at' - 'superseded_at' - 'superseding_command_id' - 'updated_at')
            <> (to_jsonb(OLD) - 'effective_to_date' - 'closed_at' - 'superseded_at' - 'superseding_command_id' - 'updated_at') THEN
        IF OLD.effective_to_date IS NOT NULL THEN
            RAISE EXCEPTION 'a closed version row cannot change: %.%', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
        END IF;
        RAISE EXCEPTION 'an open version row can only be closed: %.%', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END $$;
COMMENT ON FUNCTION organization_workforce.refuse_assignment_version_rewrite() IS '任职版本只追加的触发器：关闭列与作废列各只放行由空变非空一次，作废开放行与已关闭行都放行，其余列除更新时间外不变，任何行不能删或清空';
CREATE TRIGGER tg_assignment_versions_append_only BEFORE UPDATE OR DELETE ON organization_workforce.assignment_versions
    FOR EACH ROW EXECUTE FUNCTION organization_workforce.refuse_assignment_version_rewrite();
CREATE TRIGGER tg_assignment_versions_append_only_truncate BEFORE TRUNCATE ON organization_workforce.assignment_versions
    FOR EACH STATEMENT EXECUTE FUNCTION organization_workforce.refuse_assignment_version_rewrite();
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

-- W2 PR-B（one-data 签发 reviews/W2-PR-B.md）：人事命令与员工版本。命令表先于版本表建（版本表对它有真外键）。
CREATE TABLE organization_workforce.personnel_commands (
    personnel_command_id          uuid        NOT NULL,
    employee_id                   uuid        NOT NULL,
    command_kind                  text        NOT NULL,
    effective_date                date        NOT NULL,
    status                        text        NOT NULL,
    target_org_unit_id            uuid,
    target_position_id            uuid,
    target_reports_to_employee_id uuid,
    is_target_fee_earner          boolean,
    target_employment_phase_kind  text,
    is_target_on_unpaid_leave     boolean,
    org_structure_command_id      uuid,
    applied_at                    timestamptz,
    withdrawn_at                  timestamptz,
    withdrawn_by                  uuid,
    withdrawal_reason             text,
    created_at                    timestamptz NOT NULL,
    created_by                    uuid        NOT NULL,
    updated_at                    timestamptz NOT NULL,
    version                       bigint      NOT NULL DEFAULT 0,
    CONSTRAINT pk_personnel_commands PRIMARY KEY (personnel_command_id),
    CONSTRAINT uk_personnel_commands_id_employee UNIQUE (personnel_command_id, employee_id),
    CONSTRAINT fk_personnel_commands_employee_id FOREIGN KEY (employee_id) REFERENCES organization_workforce.employees (id) ON DELETE RESTRICT,
    CONSTRAINT fk_personnel_commands_target_org_unit_id FOREIGN KEY (target_org_unit_id) REFERENCES organization_workforce.org_units (id) ON DELETE RESTRICT,
    CONSTRAINT fk_personnel_commands_target_position_id FOREIGN KEY (target_position_id) REFERENCES organization_workforce.positions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_personnel_commands_target_reports_to FOREIGN KEY (target_reports_to_employee_id) REFERENCES organization_workforce.employees (id) ON DELETE RESTRICT,
    CONSTRAINT ck_personnel_commands_command_kind CHECK (command_kind IN ('HIRE', 'REGULARIZE', 'TRANSFER', 'SEPARATE', 'REINSTATE', 'CORRECTION', 'UNPAID_LEAVE_START', 'UNPAID_LEAVE_END')),
    CONSTRAINT ck_personnel_commands_status CHECK (status IN ('PENDING', 'HELD', 'EFFECTIVE', 'WITHDRAWN')),
    CONSTRAINT ck_personnel_commands_held_kind CHECK (status <> 'HELD' OR command_kind = 'SEPARATE'),
    CONSTRAINT ck_personnel_commands_correction_immediate CHECK (command_kind <> 'CORRECTION' OR status = 'EFFECTIVE'),
    CONSTRAINT ck_personnel_commands_applied CHECK ((status = 'EFFECTIVE') = (applied_at IS NOT NULL)),
    CONSTRAINT ck_personnel_commands_withdrawn CHECK ( (status = 'WITHDRAWN') = (withdrawn_at IS NOT NULL) AND (withdrawn_at IS NULL) = (withdrawn_by IS NULL) AND (withdrawn_at IS NULL) = (withdrawal_reason IS NULL)),
    CONSTRAINT ck_personnel_commands_withdrawal_reason CHECK (withdrawal_reason IS NULL OR (char_length(withdrawal_reason) BETWEEN 1 AND 500 AND btrim(withdrawal_reason) <> '')),
    CONSTRAINT ck_personnel_commands_effective_date_finite CHECK (isfinite(effective_date)),
    CONSTRAINT ck_personnel_commands_employment_phase_kind CHECK (target_employment_phase_kind IS NULL OR target_employment_phase_kind IN ('PROBATION', 'REGULAR')),
    CONSTRAINT ck_personnel_commands_reports_to_not_self CHECK (target_reports_to_employee_id IS DISTINCT FROM employee_id),
    CONSTRAINT ck_personnel_commands_hire_columns CHECK (command_kind <> 'HIRE' OR ( target_org_unit_id IS NOT NULL AND target_position_id IS NOT NULL AND is_target_fee_earner IS NOT NULL AND target_employment_phase_kind IS NOT NULL)),
    CONSTRAINT ck_personnel_commands_reinstate_columns CHECK (command_kind <> 'REINSTATE' OR ( target_org_unit_id IS NOT NULL AND target_position_id IS NOT NULL AND is_target_fee_earner IS NOT NULL AND target_employment_phase_kind IS NOT NULL)),
    CONSTRAINT ck_personnel_commands_transfer_columns CHECK (command_kind <> 'TRANSFER' OR ( target_employment_phase_kind IS NULL AND num_nonnulls(target_org_unit_id, target_position_id, target_reports_to_employee_id, is_target_fee_earner) >= 1)),
    CONSTRAINT ck_personnel_commands_contentless_columns CHECK (command_kind NOT IN ('REGULARIZE', 'SEPARATE') OR num_nonnulls(target_org_unit_id, target_position_id, target_reports_to_employee_id, is_target_fee_earner, target_employment_phase_kind) = 0),
    CONSTRAINT ck_personnel_commands_unpaid_leave CHECK ( (command_kind = 'UNPAID_LEAVE_START') = (is_target_on_unpaid_leave IS TRUE) AND (command_kind = 'UNPAID_LEAVE_END') = (is_target_on_unpaid_leave IS FALSE)),
    CONSTRAINT ck_personnel_commands_unpaid_leave_columns CHECK ( command_kind NOT IN ('UNPAID_LEAVE_START', 'UNPAID_LEAVE_END') OR num_nonnulls(target_org_unit_id, target_position_id, target_reports_to_employee_id, is_target_fee_earner, target_employment_phase_kind) = 0),
    CONSTRAINT ck_personnel_commands_org_structure_command CHECK (org_structure_command_id IS NULL OR command_kind = 'TRANSFER')
);
CREATE UNIQUE INDEX uk_personnel_commands_open_per_day
    ON organization_workforce.personnel_commands (employee_id, effective_date)
    WHERE status IN ('PENDING', 'HELD');
CREATE INDEX ix_personnel_commands_due
    ON organization_workforce.personnel_commands (effective_date) WHERE status = 'PENDING';
CREATE INDEX ix_personnel_commands_employee_id
    ON organization_workforce.personnel_commands (employee_id, effective_date);
CREATE UNIQUE INDEX uk_personnel_commands_merge_employee
    ON organization_workforce.personnel_commands (org_structure_command_id, employee_id)
    WHERE org_structure_command_id IS NOT NULL;
CREATE INDEX ix_personnel_commands_target_org_unit_id
    ON organization_workforce.personnel_commands (target_org_unit_id) WHERE target_org_unit_id IS NOT NULL;
CREATE INDEX ix_personnel_commands_target_position_id
    ON organization_workforce.personnel_commands (target_position_id) WHERE target_position_id IS NOT NULL;
CREATE INDEX ix_personnel_commands_target_reports_to
    ON organization_workforce.personnel_commands (target_reports_to_employee_id)
    WHERE target_reports_to_employee_id IS NOT NULL;
COMMENT ON TABLE organization_workforce.personnel_commands IS '人事命令：入职、转正、调岗、离职、复职、更正等人事异动的第一手记录，只追加；生效日为人事业务生效日（ADR-0055 决定 2、ADR-0068 第 1 条）。粒度：每条人事命令一行';
COMMENT ON COLUMN organization_workforce.personnel_commands.personnel_command_id IS '人事命令 ID（UUIDv7，由应用层生成；所有种类共用一个 ID 空间）';
COMMENT ON COLUMN organization_workforce.personnel_commands.employee_id IS '员工 ID';
COMMENT ON COLUMN organization_workforce.personnel_commands.command_kind IS '命令种类：HIRE 入职 / REGULARIZE 转正 / TRANSFER 调岗 / SEPARATE 离职 / REINSTATE 复职 / CORRECTION 更正 / UNPAID_LEAVE_START 停薪留职开始 / UNPAID_LEAVE_END 停薪留职结束';
COMMENT ON COLUMN organization_workforce.personnel_commands.effective_date IS '生效日：人事业务生效日，即新状态的第一天';
COMMENT ON COLUMN organization_workforce.personnel_commands.status IS '状态：PENDING 待生效 / HELD 待放行（仅离职）/ EFFECTIVE 已生效 / WITHDRAWN 已撤回';
COMMENT ON COLUMN organization_workforce.personnel_commands.target_org_unit_id IS '目标组织单元 ID（入职、复职、调岗）';
COMMENT ON COLUMN organization_workforce.personnel_commands.target_position_id IS '目标岗位 ID（入职、复职、调岗）';
COMMENT ON COLUMN organization_workforce.personnel_commands.target_reports_to_employee_id IS '目标直属领导（员工 ID）（入职、复职、调岗）';
COMMENT ON COLUMN organization_workforce.personnel_commands.is_target_fee_earner IS '目标是否收费员工（入职、复职、调岗）';
COMMENT ON COLUMN organization_workforce.personnel_commands.target_employment_phase_kind IS '目标雇佣阶段：PROBATION 试用 / REGULAR 正式（入职、复职）';
COMMENT ON COLUMN organization_workforce.personnel_commands.is_target_on_unpaid_leave IS '目标是否停薪留职：开始恒为真，结束恒为假，其他种类为空';
COMMENT ON COLUMN organization_workforce.personnel_commands.org_structure_command_id IS '组织结构命令 ID：只用于合并产生的逐人调岗；外键随 org_structure_commands 建表批次补';
COMMENT ON COLUMN organization_workforce.personnel_commands.applied_at IS '生效落地时刻；有值当且仅当状态为已生效';
COMMENT ON COLUMN organization_workforce.personnel_commands.withdrawn_at IS '撤回时刻';
COMMENT ON COLUMN organization_workforce.personnel_commands.withdrawn_by IS '撤回人（用户账号 ID，跨 schema 只存 ID）';
COMMENT ON COLUMN organization_workforce.personnel_commands.withdrawal_reason IS '撤回原因（1 至 500 字）';
COMMENT ON COLUMN organization_workforce.personnel_commands.created_at IS '创建时间';
COMMENT ON COLUMN organization_workforce.personnel_commands.created_by IS '录入人（用户账号 ID，跨 schema 只存 ID）';
COMMENT ON COLUMN organization_workforce.personnel_commands.updated_at IS '更新时间';
COMMENT ON COLUMN organization_workforce.personnel_commands.version IS '乐观锁版本';

CREATE TABLE organization_workforce.employee_versions (
    employee_version_id         uuid        NOT NULL,
    employee_id                 uuid        NOT NULL,
    effective_from_date         date        NOT NULL,
    effective_to_date           date,
    revision_number             integer     NOT NULL DEFAULT 0,
    employment_status           text        NOT NULL,
    employment_phase_kind       text,
    is_on_unpaid_leave          boolean     NOT NULL,
    is_fee_earner               boolean     NOT NULL,
    employment_start_date       date,
    employment_start_basis_kind text,
    change_channel_kind         text        NOT NULL,
    change_command_id           uuid,
    version_audit_entry_id      uuid,
    closed_at                   timestamptz,
    superseded_at               timestamptz,
    superseding_command_id      uuid,
    corrected_from_version_id   uuid,
    created_at                  timestamptz NOT NULL,
    updated_at                  timestamptz NOT NULL,
    CONSTRAINT pk_employee_versions PRIMARY KEY (employee_version_id),
    CONSTRAINT fk_employee_versions_employee_id FOREIGN KEY (employee_id) REFERENCES organization_workforce.employees (id) ON DELETE RESTRICT,
    CONSTRAINT fk_employee_versions_change_command_id FOREIGN KEY (change_command_id) REFERENCES organization_workforce.personnel_commands (personnel_command_id) ON DELETE RESTRICT,
    CONSTRAINT fk_employee_versions_superseding_command_id FOREIGN KEY (superseding_command_id) REFERENCES organization_workforce.personnel_commands (personnel_command_id) ON DELETE RESTRICT,
    CONSTRAINT fk_employee_versions_corrected_from_version_id FOREIGN KEY (corrected_from_version_id) REFERENCES organization_workforce.employee_versions (employee_version_id) ON DELETE RESTRICT,
    CONSTRAINT uk_employee_versions_revision UNIQUE (employee_id, effective_from_date, revision_number),
    CONSTRAINT ck_employee_versions_range CHECK (effective_to_date IS NULL OR effective_to_date >= effective_from_date),
    CONSTRAINT ck_employee_versions_finite CHECK (isfinite(effective_from_date) AND (effective_to_date IS NULL OR isfinite(effective_to_date))),
    CONSTRAINT ck_employee_versions_employment_status CHECK (employment_status IN ('EMPLOYED', 'SEPARATED')),
    CONSTRAINT ck_employee_versions_employment_phase_kind CHECK (employment_phase_kind IN ('PROBATION', 'REGULAR')),
    CONSTRAINT ck_employee_versions_phase_status CHECK ((employment_status = 'EMPLOYED') = (employment_phase_kind IS NOT NULL)),
    CONSTRAINT ck_employee_versions_leave_employed CHECK (NOT is_on_unpaid_leave OR employment_status = 'EMPLOYED'),
    CONSTRAINT ck_employee_versions_revision_number CHECK (revision_number >= 0),
    CONSTRAINT ck_employee_versions_change_channel_kind CHECK (change_channel_kind IN ('COMMAND', 'MIGRATION')),
    CONSTRAINT ck_employee_versions_command CHECK ((change_channel_kind = 'COMMAND') = (change_command_id IS NOT NULL)),
    CONSTRAINT ck_employee_versions_audit CHECK (change_channel_kind <> 'COMMAND' OR version_audit_entry_id IS NOT NULL),
    CONSTRAINT ck_employee_versions_closed CHECK ((effective_to_date IS NULL) = (closed_at IS NULL)),
    CONSTRAINT ck_employee_versions_superseded CHECK ((superseded_at IS NULL) = (superseding_command_id IS NULL)),
    CONSTRAINT ck_employee_versions_employment_start_basis_kind CHECK (employment_start_basis_kind IS NULL OR employment_start_basis_kind IN ('DECLARED', 'HR_PROVIDED', 'LEGACY_HR_RECORD', 'LEGACY_ASSIGNMENT_START', 'UNKNOWN')),
    CONSTRAINT ck_employee_versions_employment_start_employed CHECK ((employment_status = 'EMPLOYED') = (employment_start_basis_kind IS NOT NULL)),
    CONSTRAINT ck_employee_versions_employment_start_known CHECK ( (employment_start_date IS NULL) = (employment_start_basis_kind IS NULL OR employment_start_basis_kind = 'UNKNOWN')),
    CONSTRAINT ck_employee_versions_employment_start_not_after CHECK (employment_start_date IS NULL OR employment_start_date <= effective_from_date),
    CONSTRAINT ex_employee_versions_overlap EXCLUDE USING gist (employee_id WITH =, daterange(effective_from_date, effective_to_date) WITH &&) WHERE (superseded_at IS NULL)
);
CREATE INDEX ix_employee_versions_change_command_id ON organization_workforce.employee_versions (change_command_id) WHERE change_command_id IS NOT NULL;
CREATE INDEX ix_employee_versions_superseding_command_id ON organization_workforce.employee_versions (superseding_command_id) WHERE superseding_command_id IS NOT NULL;
CREATE INDEX ix_employee_versions_corrected_from_version_id ON organization_workforce.employee_versions (corrected_from_version_id) WHERE corrected_from_version_id IS NOT NULL;
CREATE INDEX ix_employee_versions_separated_effective_from_date ON organization_workforce.employee_versions (effective_from_date) WHERE employment_status = 'SEPARATED' AND superseded_at IS NULL;
CREATE INDEX ix_employee_versions_employment_start_month_day
    ON organization_workforce.employee_versions ((EXTRACT(MONTH FROM employment_start_date)), (EXTRACT(DAY FROM employment_start_date)))
    WHERE employment_status = 'EMPLOYED' AND superseded_at IS NULL AND employment_start_date IS NOT NULL;
COMMENT ON TABLE organization_workforce.employee_versions IS '员工版本：在职状态、雇佣阶段、停薪留职、收费员工与入职日期的变化历史，只追加（关闭、作废只能由空变非空一次）；生效依据为人事业务生效日（DECLARED）；粒度：每个员工的每个生效区间每个修订一行';
COMMENT ON COLUMN organization_workforce.employee_versions.employee_version_id IS '员工版本 ID（UUIDv7）';
COMMENT ON COLUMN organization_workforce.employee_versions.employee_id IS '员工 ID';
COMMENT ON COLUMN organization_workforce.employee_versions.effective_from_date IS '生效日（含），人事业务生效日';
COMMENT ON COLUMN organization_workforce.employee_versions.effective_to_date IS '失效日（不含）；为空表示至今';
COMMENT ON COLUMN organization_workforce.employee_versions.revision_number IS '修订序号：同一生效日内的序号，作废并替换时递增';
COMMENT ON COLUMN organization_workforce.employee_versions.employment_status IS '在职状态：EMPLOYED 在职 / SEPARATED 离职';
COMMENT ON COLUMN organization_workforce.employee_versions.employment_phase_kind IS '雇佣阶段：PROBATION 试用 / REGULAR 正式；只在在职版本有值';
COMMENT ON COLUMN organization_workforce.employee_versions.is_on_unpaid_leave IS '是否停薪留职；离职版本恒为假';
COMMENT ON COLUMN organization_workforce.employee_versions.is_fee_earner IS '是否收费员工';
COMMENT ON COLUMN organization_workforce.employee_versions.employment_start_date IS '入职日期：本段在职期间的入职日（复职取复职之日）；转正、调岗等版本原样复制；入职日期未知的迁入行为空';
COMMENT ON COLUMN organization_workforce.employee_versions.employment_start_basis_kind IS '入职日期依据：DECLARED 入职命令声明 / HR_PROVIDED 人事提供 / LEGACY_HR_RECORD 老系统人事档案 / LEGACY_ASSIGNMENT_START 老系统任职起点（低可信）/ UNKNOWN 未知；在职版本必填，离职版本为空';
COMMENT ON COLUMN organization_workforce.employee_versions.change_channel_kind IS '写入渠道：COMMAND 人事命令 / MIGRATION 迁入';
COMMENT ON COLUMN organization_workforce.employee_versions.change_command_id IS '写入本行的人事命令 ID；迁入行为空';
COMMENT ON COLUMN organization_workforce.employee_versions.version_audit_entry_id IS '写入本行的同一事务里写下的审计条目 ID（跨 schema 只存 ID）；迁入行可空';
COMMENT ON COLUMN organization_workforce.employee_versions.closed_at IS '关闭时刻：写失效日的同时写入，由空变非空一次';
COMMENT ON COLUMN organization_workforce.employee_versions.superseded_at IS '作废时刻：被更正或补登替换的行，由空变非空一次';
COMMENT ON COLUMN organization_workforce.employee_versions.superseding_command_id IS '作废它的人事命令 ID';
COMMENT ON COLUMN organization_workforce.employee_versions.corrected_from_version_id IS '重新追加行的来源：指向被作废的行';
COMMENT ON COLUMN organization_workforce.employee_versions.created_at IS '创建时间，即记录时刻';
COMMENT ON COLUMN organization_workforce.employee_versions.updated_at IS '更新时间';

-- 员工版本只追加：关闭列与作废列各只能由空变非空一次，其余列不变；DELETE 只放行表属主（离职满 10 年的清除函数，W2 设计第 8 节）。
CREATE FUNCTION organization_workforce.refuse_employee_version_rewrite() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
DECLARE
    owner_name name;
    column_name text;
BEGIN
    IF TG_OP = 'TRUNCATE' THEN
        RAISE EXCEPTION 'employee versions are append-only: truncate on %.% is refused', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
    END IF;
    IF TG_OP = 'DELETE' THEN
        SELECT pg_get_userbyid(relowner) INTO owner_name FROM pg_class WHERE oid = TG_RELID;
        IF current_user <> owner_name THEN
            RAISE EXCEPTION 'employee versions are append-only: delete on %.% is refused', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
        END IF;
        RETURN OLD;
    END IF;
    FOREACH column_name IN ARRAY ARRAY['effective_to_date', 'closed_at', 'superseded_at', 'superseding_command_id'] LOOP
        IF (to_jsonb(OLD) -> column_name) <> 'null'::jsonb AND (to_jsonb(NEW) -> column_name) IS DISTINCT FROM (to_jsonb(OLD) -> column_name) THEN
            RAISE EXCEPTION 'column % of %.% is set once and cannot change', column_name, TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
        END IF;
    END LOOP;
    IF (to_jsonb(NEW) - 'effective_to_date' - 'closed_at' - 'superseded_at' - 'superseding_command_id' - 'updated_at')
            <> (to_jsonb(OLD) - 'effective_to_date' - 'closed_at' - 'superseded_at' - 'superseding_command_id' - 'updated_at') THEN
        RAISE EXCEPTION 'only the closing and superseding columns of %.% can change', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END $$;
COMMENT ON FUNCTION organization_workforce.refuse_employee_version_rewrite() IS '员工版本只追加的触发器：关闭列与作废列各只放行由空变非空一次，其余列不变，DELETE 只放行表属主，TRUNCATE 拒绝';
CREATE TRIGGER tg_employee_versions_append_only BEFORE UPDATE OR DELETE ON organization_workforce.employee_versions
    FOR EACH ROW EXECUTE FUNCTION organization_workforce.refuse_employee_version_rewrite();
CREATE TRIGGER tg_employee_versions_append_only_truncate BEFORE TRUNCATE ON organization_workforce.employee_versions
    FOR EACH STATEMENT EXECUTE FUNCTION organization_workforce.refuse_employee_version_rewrite();

-- 工号发号后禁改（ADR-0078 决定 3）：库层拒绝改写 employee_code，登记错的工号保留原值。
CREATE FUNCTION organization_workforce.refuse_employee_code_change() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
BEGIN
    RAISE EXCEPTION 'organization_workforce.employees.employee_code cannot be changed once issued' USING ERRCODE = 'restrict_violation';
END $$;
CREATE TRIGGER tg_employees_employee_code_fixed BEFORE UPDATE OF employee_code ON organization_workforce.employees
    FOR EACH ROW WHEN (OLD.employee_code IS DISTINCT FROM NEW.employee_code) EXECUTE FUNCTION organization_workforce.refuse_employee_code_change();

-- 人事命令只追加：状态只能前进，时刻列只能由空变非空一次，内容列不变；DELETE 只放行表属主。
CREATE FUNCTION organization_workforce.refuse_personnel_command_rewrite() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
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
            OR (OLD.status = 'HELD' AND NEW.status IN ('EFFECTIVE', 'WITHDRAWN'))) THEN
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
COMMENT ON FUNCTION organization_workforce.refuse_personnel_command_rewrite() IS '人事命令只追加的触发器：状态按 PENDING 到 EFFECTIVE、WITHDRAWN、HELD 以及 HELD 到 EFFECTIVE、WITHDRAWN 前进，时刻列只放行由空变非空一次，内容列不变，DELETE 只放行表属主，TRUNCATE 拒绝';
CREATE TRIGGER tg_personnel_commands_append_only BEFORE UPDATE OR DELETE ON organization_workforce.personnel_commands
    FOR EACH ROW EXECUTE FUNCTION organization_workforce.refuse_personnel_command_rewrite();
CREATE TRIGGER tg_personnel_commands_append_only_truncate BEFORE TRUNCATE ON organization_workforce.personnel_commands
    FOR EACH STATEMENT EXECUTE FUNCTION organization_workforce.refuse_personnel_command_rewrite();

-- 任职版本对人事命令的引用：任职版本表建在人事命令表之前，外键在此补上（one-design 签发 #880 评论 6511470140）。
ALTER TABLE organization_workforce.assignment_versions
    ADD CONSTRAINT fk_assignment_versions_change_command_id FOREIGN KEY (change_command_id) REFERENCES organization_workforce.personnel_commands (personnel_command_id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_assignment_versions_superseding_command_id FOREIGN KEY (superseding_command_id) REFERENCES organization_workforce.personnel_commands (personnel_command_id) ON DELETE RESTRICT;
CREATE INDEX ix_assignment_versions_change_command_id ON organization_workforce.assignment_versions (change_command_id) WHERE change_command_id IS NOT NULL;
CREATE INDEX ix_assignment_versions_superseding_command_id ON organization_workforce.assignment_versions (superseding_command_id) WHERE superseding_command_id IS NOT NULL;
CREATE INDEX ix_assignment_versions_corrected_from_version_id ON organization_workforce.assignment_versions (corrected_from_version_id) WHERE corrected_from_version_id IS NOT NULL;

-- IMP-1030-PAR-B1（ADR-0068 第 1.5、2.1 条；one-design 签发 #870 评论 6511427973）：待放行离职。建在人事命令表之后（对它有复合外键）。
CREATE TABLE organization_workforce.separation_holds (
    separation_hold_id   uuid        NOT NULL,
    personnel_command_id uuid        NOT NULL,
    employee_id          uuid        NOT NULL,
    status               text        NOT NULL,
    released_at          timestamptz,
    released_by          uuid,
    release_reason       text,
    release_channel_kind      text,
    created_at           timestamptz NOT NULL,
    updated_at           timestamptz NOT NULL,
    version              bigint      NOT NULL DEFAULT 0,
    CONSTRAINT pk_separation_holds PRIMARY KEY (separation_hold_id),
    CONSTRAINT uk_separation_holds_personnel_command_id UNIQUE (personnel_command_id),
    CONSTRAINT fk_separation_holds_command FOREIGN KEY (personnel_command_id, employee_id)
        REFERENCES organization_workforce.personnel_commands (personnel_command_id, employee_id) ON DELETE RESTRICT,
    CONSTRAINT ck_separation_holds_status CHECK (status IN ('HELD', 'RELEASED', 'WITHDRAWN')),
    CONSTRAINT ck_separation_holds_released CHECK ((status = 'RELEASED') = (released_at IS NOT NULL)),
    CONSTRAINT ck_separation_holds_release_columns CHECK (
        (released_at IS NULL) = (release_reason IS NULL) AND (released_at IS NULL) = (release_channel_kind IS NULL)),
    CONSTRAINT ck_separation_holds_release_channel_kind
        CHECK (release_channel_kind IS NULL OR release_channel_kind IN ('INTERFACE', 'OPERATIONS_COMMAND')),
    CONSTRAINT ck_separation_holds_released_by CHECK (
        (release_channel_kind IS NOT DISTINCT FROM 'INTERFACE') = (released_by IS NOT NULL)),
    CONSTRAINT ck_separation_holds_release_reason CHECK (release_reason IS NULL
        OR (char_length(release_reason) BETWEEN 1 AND 500 AND btrim(release_reason) <> ''))
);
CREATE INDEX ix_separation_holds_held ON organization_workforce.separation_holds (employee_id) WHERE status = 'HELD';
COMMENT ON TABLE organization_workforce.separation_holds IS '待放行离职：管理员离职在入口检查时被留住的那条离职命令的放行状态，只追加；留住的集合是剩余超管计算的排除集合（ADR-0068 第 1.5、2.1 条）。粒度：每条被留住的离职命令一行';
COMMENT ON COLUMN organization_workforce.separation_holds.separation_hold_id IS '待放行离职 ID（UUIDv7，由应用层生成）';
COMMENT ON COLUMN organization_workforce.separation_holds.personnel_command_id IS '人事命令 ID：被留住的离职命令；一条命令至多一行';
COMMENT ON COLUMN organization_workforce.separation_holds.employee_id IS '员工 ID：与命令的员工一致，由复合外键保证';
COMMENT ON COLUMN organization_workforce.separation_holds.status IS '状态：HELD 待放行 / RELEASED 已放行 / WITHDRAWN 已撤回（命令被撤回时随之）；只能由 HELD 改为后两者';
COMMENT ON COLUMN organization_workforce.separation_holds.released_at IS '放行时刻；为空表示尚未放行（待放行或已撤回）；有值当且仅当状态为已放行';
COMMENT ON COLUMN organization_workforce.separation_holds.released_by IS '放行人（用户账号 ID，跨 schema 只存 ID）；为空表示经运维命令放行（操作人与见证人只在审计里，ADR-0068 第 2.1 条）或尚未放行；接口放行必填';
COMMENT ON COLUMN organization_workforce.separation_holds.release_reason IS '放行原因（1 至 500 字）；为空表示尚未放行';
COMMENT ON COLUMN organization_workforce.separation_holds.release_channel_kind IS '放行渠道：INTERFACE 接口 / OPERATIONS_COMMAND 运维命令；为空表示尚未放行';
COMMENT ON COLUMN organization_workforce.separation_holds.created_at IS '创建时间';
COMMENT ON COLUMN organization_workforce.separation_holds.updated_at IS '更新时间';
COMMENT ON COLUMN organization_workforce.separation_holds.version IS '乐观锁版本';

-- 待放行离职只追加：状态只能由 HELD 改为 RELEASED 或 WITHDRAWN，放行四列各只能由空变非空一次，其余列除更新时间与版本外不变；DELETE 只放行表属主。
CREATE FUNCTION organization_workforce.refuse_separation_hold_rewrite() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
DECLARE
    owner_name name;
    column_name text;
BEGIN
    IF TG_OP = 'TRUNCATE' THEN
        RAISE EXCEPTION 'separation holds are append-only: truncate on %.% is refused', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
    END IF;
    IF TG_OP = 'DELETE' THEN
        SELECT pg_get_userbyid(relowner) INTO owner_name FROM pg_class WHERE oid = TG_RELID;
        IF current_user <> owner_name THEN
            RAISE EXCEPTION 'separation holds are append-only: delete on %.% is refused', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
        END IF;
        RETURN OLD;
    END IF;
    IF NEW.status <> OLD.status AND NOT (OLD.status = 'HELD' AND NEW.status IN ('RELEASED', 'WITHDRAWN')) THEN
        RAISE EXCEPTION 'separation hold status cannot move from % to %', OLD.status, NEW.status USING ERRCODE = 'restrict_violation';
    END IF;
    FOREACH column_name IN ARRAY ARRAY['released_at', 'released_by', 'release_reason', 'release_channel_kind'] LOOP
        IF (to_jsonb(OLD) -> column_name) <> 'null'::jsonb AND (to_jsonb(NEW) -> column_name) IS DISTINCT FROM (to_jsonb(OLD) -> column_name) THEN
            RAISE EXCEPTION 'column % of %.% is set once and cannot change', column_name, TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
        END IF;
    END LOOP;
    IF (to_jsonb(NEW) - 'status' - 'released_at' - 'released_by' - 'release_reason' - 'release_channel_kind' - 'updated_at' - 'version')
            <> (to_jsonb(OLD) - 'status' - 'released_at' - 'released_by' - 'release_reason' - 'release_channel_kind' - 'updated_at' - 'version') THEN
        RAISE EXCEPTION 'only the status and release columns of %.% can change', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END $$;
COMMENT ON FUNCTION organization_workforce.refuse_separation_hold_rewrite() IS '待放行离职只追加的触发器：状态只放行 HELD 到 RELEASED、WITHDRAWN，放行四列只放行由空变非空一次，其余列除更新时间与版本外不变，DELETE 只放行表属主，TRUNCATE 拒绝';
CREATE TRIGGER tg_separation_holds_append_only BEFORE UPDATE OR DELETE ON organization_workforce.separation_holds
    FOR EACH ROW EXECUTE FUNCTION organization_workforce.refuse_separation_hold_rewrite();
CREATE TRIGGER tg_separation_holds_append_only_truncate BEFORE TRUNCATE ON organization_workforce.separation_holds
    FOR EACH STATEMENT EXECUTE FUNCTION organization_workforce.refuse_separation_hold_rewrite();

-- IMP-1030-PAR-B1（ADR-0058 决定 8、ADR-0075，ADR-0068 第 2.1 条）：人事命令与待放行离职的状态转换记录，形状同 organization_administration.state_transitions。
CREATE TABLE organization_workforce.state_transitions (
    id                      uuid        NOT NULL,
    aggregate_type_code     text        NOT NULL CONSTRAINT ck_state_transitions_aggregate_type_code CHECK (aggregate_type_code IN ('PersonnelCommand', 'SeparationHold')),
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
        REFERENCES organization_workforce.state_transitions (id, aggregate_id) ON DELETE RESTRICT,
    CONSTRAINT ck_state_transitions_from_status CHECK (from_status IN ('PENDING', 'HELD')),
    CONSTRAINT ck_state_transitions_to_status CHECK (to_status IN ('PENDING', 'HELD', 'EFFECTIVE', 'WITHDRAWN', 'RELEASED')),
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
        (aggregate_type_code = 'PersonnelCommand'
            AND ((from_status IS NULL AND to_status IN ('PENDING', 'HELD', 'EFFECTIVE'))
                 OR (from_status = 'PENDING' AND to_status IN ('EFFECTIVE', 'WITHDRAWN', 'HELD'))
                 OR (from_status = 'HELD' AND to_status IN ('EFFECTIVE', 'WITHDRAWN'))))
        OR (aggregate_type_code = 'SeparationHold'
            AND ((from_status IS NULL AND to_status = 'HELD')
                 OR (from_status = 'HELD' AND to_status IN ('RELEASED', 'WITHDRAWN')))), false)),
    CONSTRAINT ck_state_transitions_times CHECK (created_at = recorded_at AND updated_at = recorded_at)
);
CREATE INDEX ix_state_transitions_legal_entity_id_occurred_on_date ON organization_workforce.state_transitions (legal_entity_id, occurred_on_date) WHERE legal_entity_id IS NOT NULL;
CREATE INDEX ix_state_transitions_recorded_at ON organization_workforce.state_transitions (recorded_at, id);
CREATE FUNCTION organization_workforce.refuse_transition_rewrite() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
BEGIN
    IF TG_OP = 'DELETE' AND current_user = (SELECT pg_get_userbyid(c.relowner) FROM pg_class c WHERE c.oid = TG_RELID) THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'state transitions are append-only: % on %.% is refused', lower(TG_OP), TG_TABLE_SCHEMA, TG_TABLE_NAME
        USING ERRCODE = 'restrict_violation';
END $$;
CREATE TRIGGER tg_state_transitions_append_only BEFORE UPDATE OR DELETE ON organization_workforce.state_transitions
    FOR EACH ROW EXECUTE FUNCTION organization_workforce.refuse_transition_rewrite();
CREATE TRIGGER tg_state_transitions_append_only_truncate BEFORE TRUNCATE ON organization_workforce.state_transitions
    FOR EACH STATEMENT EXECUTE FUNCTION organization_workforce.refuse_transition_rewrite();
COMMENT ON TABLE organization_workforce.state_transitions IS '状态转换记录：本 schema 各状态持有者每次状态变更一行，只追加，回退也正向记（ADR-0058、ADR-0075）。粒度：每次状态变更一行';
COMMENT ON COLUMN organization_workforce.state_transitions.id IS '状态转换 ID（UUIDv7）';
COMMENT ON COLUMN organization_workforce.state_transitions.aggregate_type_code IS '聚合类型代码：状态持有者的类型，本基线有 PersonnelCommand（人事命令）与 SeparationHold（待放行离职）；两者都是员工聚合内带自己状态机的实体，后续卡随各自状态机追加';
COMMENT ON COLUMN organization_workforce.state_transitions.aggregate_id IS '聚合 ID：状态持有者自己的 ID，PersonnelCommand 取 personnel_command_id，SeparationHold 取 separation_hold_id，无外键（多态主体）';
COMMENT ON COLUMN organization_workforce.state_transitions.transition_sequence_number IS '转换序号：本次变更之后该状态持有者行自己的 version，与该行同事务递增；严格递增、唯一、允许跳号（ADR-0075 决定 5）';
COMMENT ON COLUMN organization_workforce.state_transitions.from_status IS '原状态：为空表示新建或迁入的初始状态；取值为本 schema 各状态机的状态并集，合法流转见 ck_state_transitions_status_of_aggregate：PersonnelCommand 为空到待生效、待放行、已生效，待生效到已生效、已撤回、待放行，待放行到已生效、已撤回；SeparationHold 为空到待放行，待放行到已放行、已撤回';
COMMENT ON COLUMN organization_workforce.state_transitions.to_status IS '新状态：PENDING 待生效 / HELD 待放行 / EFFECTIVE 已生效 / WITHDRAWN 已撤回 / RELEASED 已放行（仅待放行离职）';
COMMENT ON COLUMN organization_workforce.state_transitions.occurred_on_date IS '发生日期：业务日期（Asia/Shanghai）';
COMMENT ON COLUMN organization_workforce.state_transitions.recorded_at IS '记录时间：注入时钟';
COMMENT ON COLUMN organization_workforce.state_transitions.legal_entity_id IS '法人主体 ID：为空表示该转换不携带法人主体（员工所属法人按日期从 employee_legal_entities 读取，不在本表重复）';
COMMENT ON COLUMN organization_workforce.state_transitions.actor_type IS '操作者类型：USER 用户 / SYSTEM 系统操作者（作业、事件监听按 system-actors.txt 登记的系统操作者）';
COMMENT ON COLUMN organization_workforce.state_transitions.actor_id IS '操作人 ID：用户 ID 或系统操作者 UUIDv5';
COMMENT ON COLUMN organization_workforce.state_transitions.cause_kind IS '起因：COMMAND / REVERSAL / CORRECTION / MIGRATION / SYSTEM_JOB';
COMMENT ON COLUMN organization_workforce.state_transitions.command_id IS '命令 ID：为空表示不是命令触发';
COMMENT ON COLUMN organization_workforce.state_transitions.event_id IS '事件 ID：为空表示不是事件触发';
COMMENT ON COLUMN organization_workforce.state_transitions.correlation_id IS '关联号：同一请求或作业的一串变更共用';
COMMENT ON COLUMN organization_workforce.state_transitions.reverses_transition_id IS '抵消的转换 ID：为空表示不是冲销或更正';
COMMENT ON COLUMN organization_workforce.state_transitions.created_at IS '创建时间（恒等于记录时间）';
COMMENT ON COLUMN organization_workforce.state_transitions.updated_at IS '更新时间（恒等于记录时间）';
