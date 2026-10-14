-- A8 组织快照：只读，A8 为源（RULE-357/359/368）。表中不含任何金额列（GUIDING_PRINCIPLES §9a）。
CREATE SCHEMA IF NOT EXISTS organization_workforce;
COMMENT ON SCHEMA organization_workforce IS '组织人事 BC：A8 组织快照（只读，来源可替换）';

CREATE TABLE organization_workforce.positions (
    id         bigint      PRIMARY KEY,
    code       text,
    name       text        NOT NULL,
    sort_order integer,
    status     text        NOT NULL CHECK (status IN ('ACTIVE', 'DISABLED', 'DELETED')),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    version    bigint      NOT NULL DEFAULT 0
);
COMMENT ON TABLE organization_workforce.positions IS '岗位：A8 ORG_POST 的快照，停用与删除同步（RULE-367）';
COMMENT ON COLUMN organization_workforce.positions.id IS '岗位 ID（沿用 A8，有符号 bigint）';
COMMENT ON COLUMN organization_workforce.positions.code IS '岗位编码';
COMMENT ON COLUMN organization_workforce.positions.name IS '岗位名称';
COMMENT ON COLUMN organization_workforce.positions.sort_order IS '显示顺序';
COMMENT ON COLUMN organization_workforce.positions.status IS '状态：ACTIVE 启用 / DISABLED 停用 / DELETED 已删除（A8 删除或不再返回）';
COMMENT ON COLUMN organization_workforce.positions.created_at IS '创建时间';
COMMENT ON COLUMN organization_workforce.positions.updated_at IS '更新时间';
COMMENT ON COLUMN organization_workforce.positions.version IS '乐观锁版本';

CREATE TABLE organization_workforce.org_units (
    id                              bigint      PRIMARY KEY,
    code                            text,
    name                            text        NOT NULL,
    parent_org_unit_id              bigint,
    path                            text        NOT NULL,
    sort_order                      integer,
    status                          text        NOT NULL CHECK (status IN ('ACTIVE', 'DISABLED', 'REVOKED')),
    head_employee_id                bigint,
    regional_receivables_manager_id bigint,
    region_category                 text        CHECK (region_category IN ('HEADQUARTERS', 'REGIONAL')),
    is_delivery_unit                boolean     NOT NULL DEFAULT false,
    short_name                      text,
    revoked_at                      timestamptz,
    last_sync_run_id                bigint,
    created_at                      timestamptz NOT NULL DEFAULT now(),
    updated_at                      timestamptz NOT NULL DEFAULT now(),
    version                         bigint      NOT NULL DEFAULT 0
);
CREATE INDEX org_units_parent_org_unit_id_idx ON organization_workforce.org_units (parent_org_unit_id);
CREATE INDEX org_units_path_idx ON organization_workforce.org_units (path text_pattern_ops);
COMMENT ON TABLE organization_workforce.org_units IS '组织单元：A8 ORG_UNIT 的快照；停用（DISABLED）与撤销（REVOKED，A8 不再返回）分开（RULE-355/357）';
COMMENT ON COLUMN organization_workforce.org_units.id IS '组织单元 ID（沿用 A8，有符号 bigint）';
COMMENT ON COLUMN organization_workforce.org_units.code IS '组织单元编码';
COMMENT ON COLUMN organization_workforce.org_units.name IS '组织单元名称';
COMMENT ON COLUMN organization_workforce.org_units.parent_org_unit_id IS '组织单元的上级（由 A8 PATH 去掉末 4 位得到，RULE-354）；根为空';
COMMENT ON COLUMN organization_workforce.org_units.path IS '路径：A8 层级路径，每级 4 位，根为 00000001';
COMMENT ON COLUMN organization_workforce.org_units.sort_order IS '同级显示顺序';
COMMENT ON COLUMN organization_workforce.org_units.status IS '状态：ACTIVE 有效 / DISABLED 停用（A8 停用标志，NULL 视为有效）/ REVOKED 已撤销（A8 不再返回）';
COMMENT ON COLUMN organization_workforce.org_units.head_employee_id IS '部门负责人（员工 ID，A8 关系；多名取 ID 最小者，RULE-356）';
COMMENT ON COLUMN organization_workforce.org_units.regional_receivables_manager_id IS '区域应收负责人（员工 ID，老系统「区域 AR」，A8 关系）';
COMMENT ON COLUMN organization_workforce.org_units.region_category IS '总部 / 区域类别：HEADQUARTERS 总部、REGIONAL 区域；A8 未维护为空';
COMMENT ON COLUMN organization_workforce.org_units.is_delivery_unit IS '是否交付部门（A8 扩展属性）';
COMMENT ON COLUMN organization_workforce.org_units.short_name IS '部门简称：A8 视图中没有，每天从老系统只读刷新';
COMMENT ON COLUMN organization_workforce.org_units.revoked_at IS '撤销时间（A8 不再返回该组织单元的时间）';
COMMENT ON COLUMN organization_workforce.org_units.last_sync_run_id IS '最近一次改动它的同步运行';
COMMENT ON COLUMN organization_workforce.org_units.created_at IS '创建时间';
COMMENT ON COLUMN organization_workforce.org_units.updated_at IS '更新时间';
COMMENT ON COLUMN organization_workforce.org_units.version IS '乐观锁版本';

CREATE TABLE organization_workforce.employees (
    id                bigint      PRIMARY KEY,
    employee_code     text        NOT NULL,
    display_name      text        NOT NULL,
    login_name        text,
    email             text,
    gender            text        CHECK (gender IN ('MALE', 'FEMALE')),
    wecom_user_id     text,
    employment_status text        NOT NULL CHECK (employment_status IN ('EMPLOYED', 'SEPARATED')),
    is_fee_earner     boolean     NOT NULL DEFAULT false,
    separated_at      timestamptz,
    last_sync_run_id  bigint,
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    version           bigint      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX employees_wecom_user_id_employed_key ON organization_workforce.employees (wecom_user_id)
    WHERE employment_status = 'EMPLOYED' AND wecom_user_id IS NOT NULL;
CREATE INDEX employees_employee_code_idx ON organization_workforce.employees (employee_code);
COMMENT ON TABLE organization_workforce.employees IS '员工：A8 ORG_MEMBER 的快照；没有 A8 登录名即离职（RULE-363），不含生日等敏感信息';
COMMENT ON COLUMN organization_workforce.employees.id IS '员工 ID（沿用 A8 ORG_MEMBER.ID，有符号 bigint）';
COMMENT ON COLUMN organization_workforce.employees.employee_code IS '员工编号（工号）';
COMMENT ON COLUMN organization_workforce.employees.display_name IS '姓名';
COMMENT ON COLUMN organization_workforce.employees.login_name IS 'A8 登录名；为空（null 或空串）表示已离职（RULE-363）';
COMMENT ON COLUMN organization_workforce.employees.email IS '企业邮箱';
COMMENT ON COLUMN organization_workforce.employees.gender IS '性别：MALE / FEMALE；A8 未维护或无法识别为空（不再默认写成女）';
COMMENT ON COLUMN organization_workforce.employees.wecom_user_id IS '企业微信 userid（企业微信登录与通知的唯一键，建档时写入，RULE-362）；在职员工中唯一';
COMMENT ON COLUMN organization_workforce.employees.employment_status IS '在职状态：EMPLOYED 在职 / SEPARATED 离职（含不在 A8 结果中的离职候选，RULE-366）';
COMMENT ON COLUMN organization_workforce.employees.is_fee_earner IS '是否收费员工：A8 人事分类（FIELD0244），只是可计费性判定的输入之一，不等于可计费';
COMMENT ON COLUMN organization_workforce.employees.separated_at IS '离职时间（首次同步到离职的时间）';
COMMENT ON COLUMN organization_workforce.employees.last_sync_run_id IS '最近一次改动它的同步运行';
COMMENT ON COLUMN organization_workforce.employees.created_at IS '创建时间';
COMMENT ON COLUMN organization_workforce.employees.updated_at IS '更新时间';
COMMENT ON COLUMN organization_workforce.employees.version IS '乐观锁版本';

CREATE TABLE organization_workforce.assignments (
    employee_id            bigint      PRIMARY KEY REFERENCES organization_workforce.employees (id),
    org_unit_id            bigint      NOT NULL REFERENCES organization_workforce.org_units (id),
    position_id            bigint      REFERENCES organization_workforce.positions (id),
    reports_to_employee_id bigint,
    created_at             timestamptz NOT NULL DEFAULT now(),
    updated_at             timestamptz NOT NULL DEFAULT now(),
    version                bigint      NOT NULL DEFAULT 0
);
CREATE INDEX assignments_org_unit_id_idx ON organization_workforce.assignments (org_unit_id);
COMMENT ON TABLE organization_workforce.assignments IS '任职：员工所在组织单元、岗位与直属领导（汇报线），A8 为源';
COMMENT ON COLUMN organization_workforce.assignments.employee_id IS '员工 ID';
COMMENT ON COLUMN organization_workforce.assignments.org_unit_id IS '组织单元 ID（所属部门）';
COMMENT ON COLUMN organization_workforce.assignments.position_id IS '岗位 ID；A8 未给岗位的行在同步时被隔离，因此正常数据不为空';
COMMENT ON COLUMN organization_workforce.assignments.reports_to_employee_id IS '直属领导（员工 ID）；A8 清空即清空；不存在、已离职、本人或成环的领导不写入（RULE-372）';
COMMENT ON COLUMN organization_workforce.assignments.created_at IS '创建时间';
COMMENT ON COLUMN organization_workforce.assignments.updated_at IS '更新时间';
COMMENT ON COLUMN organization_workforce.assignments.version IS '乐观锁版本';
