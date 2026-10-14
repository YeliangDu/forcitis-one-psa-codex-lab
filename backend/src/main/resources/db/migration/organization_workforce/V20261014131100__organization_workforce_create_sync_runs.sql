-- 同步运行与差异（RULE-358/366/373）：每 5 分钟一次，缩水时 HELD，管理员可放行。
CREATE TABLE organization_workforce.sync_runs (
    id                             bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
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
    released_by                    bigint,
    released_at                    timestamptz,
    released_by_run_id             bigint,
    created_at                     timestamptz NOT NULL DEFAULT now(),
    updated_at                     timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX sync_runs_started_at_idx ON organization_workforce.sync_runs (started_at DESC);
CREATE INDEX sync_runs_status_idx ON organization_workforce.sync_runs (status);
COMMENT ON TABLE organization_workforce.sync_runs IS '同步运行：A8 组织快照每次同步的记录与缩水保护结果';
COMMENT ON COLUMN organization_workforce.sync_runs.id IS '同步运行 ID';
COMMENT ON COLUMN organization_workforce.sync_runs.trigger_kind IS '触发方式：SCHEDULED 定时 / MANUAL 管理员放行';
COMMENT ON COLUMN organization_workforce.sync_runs.status IS '状态：RUNNING 运行中 / SUCCEEDED 成功 / HELD 因缩水保护中止 / RELEASED 已被管理员放行 / FAILED 失败';
COMMENT ON COLUMN organization_workforce.sync_runs.started_at IS '开始时间';
COMMENT ON COLUMN organization_workforce.sync_runs.finished_at IS '结束时间';
COMMENT ON COLUMN organization_workforce.sync_runs.source_org_unit_count IS 'A8 返回的组织单元行数';
COMMENT ON COLUMN organization_workforce.sync_runs.source_employee_count IS 'A8 返回的人员行数（同步范围内）';
COMMENT ON COLUMN organization_workforce.sync_runs.incoming_active_org_unit_count IS 'A8 返回的有效组织单元数（缩水保护的分子）';
COMMENT ON COLUMN organization_workforce.sync_runs.incoming_employed_count IS 'A8 返回的在职人数（缩水保护的分子）';
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
COMMENT ON COLUMN organization_workforce.sync_runs.released_by IS '放行人（员工 ID）';
COMMENT ON COLUMN organization_workforce.sync_runs.released_at IS '放行时间';
COMMENT ON COLUMN organization_workforce.sync_runs.released_by_run_id IS '放行后实际执行的同步运行';
COMMENT ON COLUMN organization_workforce.sync_runs.created_at IS '创建时间';
COMMENT ON COLUMN organization_workforce.sync_runs.updated_at IS '更新时间';

CREATE TABLE organization_workforce.sync_diffs (
    id             bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sync_run_id    bigint      NOT NULL REFERENCES organization_workforce.sync_runs (id) ON DELETE CASCADE,
    entity_kind    text        NOT NULL CHECK (entity_kind IN ('ORG_UNIT', 'EMPLOYEE', 'POSITION')),
    subject_key    text        NOT NULL,
    change_kind    text        NOT NULL CHECK (change_kind IN ('CREATED', 'UPDATED', 'SEPARATED', 'REHIRED', 'REVOKED', 'REVIVED', 'ISSUE')),
    changed_fields text,
    issue_kind     text,
    detail         text,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX sync_diffs_sync_run_id_idx ON organization_workforce.sync_diffs (sync_run_id);
COMMENT ON TABLE organization_workforce.sync_diffs IS '同步差异：每次同步改动的对象与字段，以及数据例外（ISSUE）';
COMMENT ON COLUMN organization_workforce.sync_diffs.id IS '差异 ID';
COMMENT ON COLUMN organization_workforce.sync_diffs.sync_run_id IS '同步运行 ID';
COMMENT ON COLUMN organization_workforce.sync_diffs.entity_kind IS '对象类别：ORG_UNIT / EMPLOYEE / POSITION';
COMMENT ON COLUMN organization_workforce.sync_diffs.subject_key IS '对象键，如 employee:2001';
COMMENT ON COLUMN organization_workforce.sync_diffs.change_kind IS '变更类别：CREATED / UPDATED / SEPARATED / REHIRED / REVOKED / REVIVED / ISSUE（数据例外）';
COMMENT ON COLUMN organization_workforce.sync_diffs.changed_fields IS '变更的同步字段（逗号分隔，与组织变更事件使用同一清单，RULE-373）';
COMMENT ON COLUMN organization_workforce.sync_diffs.issue_kind IS '数据例外类别（仅 ISSUE 行）';
COMMENT ON COLUMN organization_workforce.sync_diffs.detail IS '说明';
COMMENT ON COLUMN organization_workforce.sync_diffs.created_at IS '创建时间';
COMMENT ON COLUMN organization_workforce.sync_diffs.updated_at IS '更新时间';
