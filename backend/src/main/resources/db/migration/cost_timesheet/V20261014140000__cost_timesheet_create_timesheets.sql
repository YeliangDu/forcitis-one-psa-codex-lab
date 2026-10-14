CREATE SCHEMA IF NOT EXISTS cost_timesheet;
COMMENT ON SCHEMA cost_timesheet IS '工时（成本 BC · 工时填报与审批结果）';

CREATE TABLE cost_timesheet.timesheets (
    id                   bigint        NOT NULL,
    employee_id          bigint        NOT NULL,
    legal_entity_id      bigint        NOT NULL,
    work_package_id      bigint        NOT NULL,
    work_date            date          NOT NULL,
    submitted_hours      numeric(10,2) NOT NULL,
    project_manager_id   bigint        NOT NULL,
    status               text          NOT NULL CHECK (status IN ('DRAFT', 'SUBMITTED', 'RETURNED', 'APPROVED', 'REJECTED')),
    round_number         integer       NOT NULL DEFAULT 0,
    approval_instance_id text,
    event_count          bigint        NOT NULL DEFAULT 0,
    created_at           timestamptz   NOT NULL DEFAULT now(),
    updated_at           timestamptz   NOT NULL DEFAULT now(),
    version              bigint        NOT NULL DEFAULT 0,
    CONSTRAINT pk_timesheets PRIMARY KEY (id),
    CONSTRAINT ck_timesheets_hours CHECK (submitted_hours >= 0),
    CONSTRAINT ck_timesheets_round CHECK (round_number >= 0),
    CONSTRAINT ck_timesheets_event_count CHECK (event_count >= 0)
);
COMMENT ON TABLE cost_timesheet.timesheets IS '工时单：状态机在聚合内，审批结果经审批完成事件回写';
COMMENT ON COLUMN cost_timesheet.timesheets.id IS '主键：工时单标识';
COMMENT ON COLUMN cost_timesheet.timesheets.employee_id IS '员工（人员 ID）：填报人，只有填报人能提交、撤回、撤销';
COMMENT ON COLUMN cost_timesheet.timesheets.legal_entity_id IS '法人主体（法人 ID）：由提交人所属法人传入，缺失即失败，不设默认';
COMMENT ON COLUMN cost_timesheet.timesheets.work_package_id IS '工作包（工作包 ID）：工时记在哪一项工作上';
COMMENT ON COLUMN cost_timesheet.timesheets.work_date IS '工作日期：工时所属的业务日期，事件的业务日期取它';
COMMENT ON COLUMN cost_timesheet.timesheets.submitted_hours IS '填报工时：本单填报的工时，非负，两位小数';
COMMENT ON COLUMN cost_timesheet.timesheets.project_manager_id IS '项目经理（人员 ID）：工时审批的第一道审批人，Phase 2 前由请求给出';
COMMENT ON COLUMN cost_timesheet.timesheets.status IS '状态：DRAFT 草稿 / SUBMITTED 已提交审批中 / RETURNED 已退回待修改 / APPROVED 已通过 / REJECTED 已驳回，用枚举不靠文案';
COMMENT ON COLUMN cost_timesheet.timesheets.round_number IS '轮次：已提交审批的次数，与审批请求的轮次一致；0 表示从未提交';
COMMENT ON COLUMN cost_timesheet.timesheets.approval_instance_id IS '审批实例（流程实例 ID）：当前或最近一轮';
COMMENT ON COLUMN cost_timesheet.timesheets.event_count IS '事件计数：该聚合已记录的领域事件数，下一个事件取其加一（信封的聚合版本）';
COMMENT ON COLUMN cost_timesheet.timesheets.created_at IS '创建时间';
COMMENT ON COLUMN cost_timesheet.timesheets.updated_at IS '更新时间';
COMMENT ON COLUMN cost_timesheet.timesheets.version IS '乐观锁版本';

CREATE TABLE cost_timesheet.consumed_events (
    event_id      uuid        NOT NULL,
    consumer_name text        NOT NULL,
    consumed_at   timestamptz NOT NULL DEFAULT now(),
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_consumed_events PRIMARY KEY (event_id, consumer_name)
);
COMMENT ON TABLE cost_timesheet.consumed_events IS '已处理的事件：消费方按事件标识去重，事件至少投递一次，重复投递不重复处理';
COMMENT ON COLUMN cost_timesheet.consumed_events.event_id IS '事件标识：事件信封的事件 ID，兼作幂等键';
COMMENT ON COLUMN cost_timesheet.consumed_events.consumer_name IS '消费者名称：处理该事件的监听者，同一事件可被多个监听者各处理一次';
COMMENT ON COLUMN cost_timesheet.consumed_events.consumed_at IS '处理时间';
COMMENT ON COLUMN cost_timesheet.consumed_events.created_at IS '创建时间';
COMMENT ON COLUMN cost_timesheet.consumed_events.updated_at IS '更新时间';
