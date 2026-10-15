CREATE SCHEMA IF NOT EXISTS cost_timesheet;
COMMENT ON SCHEMA cost_timesheet IS '工时（成本 BC · 工时填报与审批结果）';

CREATE TABLE cost_timesheet.timesheets (
    id                   uuid          NOT NULL,
    employee_id          uuid          NOT NULL,
    legal_entity_id      uuid          NOT NULL,
    work_date            date          NOT NULL,
    submitted_hours      numeric(10,2) NOT NULL,
    project_manager_id   uuid          NOT NULL,
    status               text          NOT NULL CHECK (status IN ('DRAFT', 'SUBMITTED', 'RETURNED', 'APPROVED', 'REJECTED')),
    round_number         integer       NOT NULL DEFAULT 0,
    event_count          bigint        NOT NULL DEFAULT 0,
    created_at           timestamptz   NOT NULL DEFAULT now(),
    updated_at           timestamptz   NOT NULL DEFAULT now(),
    version              bigint        NOT NULL DEFAULT 0,
    timesheet_number     text          NOT NULL,
    org_unit_id          uuid          NOT NULL,
    submitted_date       date,
    CONSTRAINT pk_timesheets PRIMARY KEY (id),
    CONSTRAINT ck_timesheets_hours CHECK (submitted_hours >= 0),
    CONSTRAINT ck_timesheets_round CHECK (round_number >= 0),
    CONSTRAINT ck_timesheets_event_count CHECK (event_count >= 0),
    CONSTRAINT uk_timesheets_timesheet_number UNIQUE (timesheet_number)
);
CREATE INDEX ix_timesheets_employee_id ON cost_timesheet.timesheets (employee_id, work_date);
CREATE INDEX ix_timesheets_org_unit_id ON cost_timesheet.timesheets (org_unit_id, work_date);
CREATE INDEX ix_timesheets_status ON cost_timesheet.timesheets (status, updated_at) WHERE status IN ('SUBMITTED', 'RETURNED');
COMMENT ON TABLE cost_timesheet.timesheets IS '工时单：聚合根；粒度：每张工时单一行；状态机在聚合内，审批结果经审批完成事件回写';
COMMENT ON COLUMN cost_timesheet.timesheets.id IS '主键：工时单标识（UUIDv7）';
COMMENT ON COLUMN cost_timesheet.timesheets.employee_id IS '员工（员工 ID）：填报人，只有填报人能提交、撤回、撤销';
COMMENT ON COLUMN cost_timesheet.timesheets.legal_entity_id IS '法人主体（法人 ID）：由提交人所属法人传入，缺失即失败，不设默认';
COMMENT ON COLUMN cost_timesheet.timesheets.work_date IS '工作日期：员工填写的、工时发生的那一天；创建时校验不得晚于今天，之后不再改写；事件的业务日期取它';
COMMENT ON COLUMN cost_timesheet.timesheets.submitted_hours IS '填报工时：本单填报的工时，非负，两位小数';
COMMENT ON COLUMN cost_timesheet.timesheets.project_manager_id IS '项目经理（员工 ID）：工时审批的第一道审批人，Phase 2 前由请求给出';
COMMENT ON COLUMN cost_timesheet.timesheets.status IS '状态：DRAFT 草稿 / SUBMITTED 已提交审批中 / RETURNED 已退回待修改 / APPROVED 已通过 / REJECTED 已驳回，用枚举不靠文案';
COMMENT ON COLUMN cost_timesheet.timesheets.round_number IS '轮次：已提交审批的次数，与审批请求的轮次一致；0 表示从未提交';
COMMENT ON COLUMN cost_timesheet.timesheets.event_count IS '事件序号：该聚合已记录的领域事件数，每个事件加一，下一个事件取其加一作信封的聚合版本；与乐观锁版本 version（每次保存加一）是两个计数';
COMMENT ON COLUMN cost_timesheet.timesheets.created_at IS '创建时间';
COMMENT ON COLUMN cost_timesheet.timesheets.updated_at IS '更新时间';
COMMENT ON COLUMN cost_timesheet.timesheets.version IS '乐观锁版本';
COMMENT ON COLUMN cost_timesheet.timesheets.timesheet_number IS '单据编号：创建时由编号服务取号（TIMESHEET），全局唯一；事件信封的单据编号落在业务行上';
COMMENT ON COLUMN cost_timesheet.timesheets.org_unit_id IS '组织单元（组织单元 ID）：创建工时单时填报人当前所在的部门，取自组织快照，不按工作日期回溯到当日的部门；创建之后不随调岗改变';
COMMENT ON COLUMN cost_timesheet.timesheets.submitted_date IS '提交日：员工提交审批的业务日期（Asia/Shanghai）；重新提交时更新；与工作日期是两件事，空表示从未提交';

CREATE TABLE cost_timesheet.consumed_events (
    consumed_event_id uuid    NOT NULL,
    event_id      uuid        NOT NULL,
    consumer_name text        NOT NULL,
    consumed_at   timestamptz NOT NULL DEFAULT now(),
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_consumed_events PRIMARY KEY (consumed_event_id),
    CONSTRAINT uk_consumed_events_event_id_consumer_name UNIQUE (event_id, consumer_name)
);
COMMENT ON TABLE cost_timesheet.consumed_events IS '已处理的事件：幂等记录；粒度：每个消费方每个事件标识一行，只追加；消费方按事件标识去重，事件至少投递一次';
COMMENT ON COLUMN cost_timesheet.consumed_events.consumed_event_id IS '已处理事件记录 ID（UUIDv7）';
COMMENT ON COLUMN cost_timesheet.consumed_events.event_id IS '事件标识：事件信封的事件 ID，兼作幂等键';
COMMENT ON COLUMN cost_timesheet.consumed_events.consumer_name IS '消费者名称：处理该事件的监听者，同一事件可被多个监听者各处理一次';
COMMENT ON COLUMN cost_timesheet.consumed_events.consumed_at IS '处理时间';
COMMENT ON COLUMN cost_timesheet.consumed_events.created_at IS '创建时间';
COMMENT ON COLUMN cost_timesheet.consumed_events.updated_at IS '更新时间';
