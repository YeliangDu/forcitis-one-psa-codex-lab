-- 审批任务聚合：引擎里的任务在审批模块这一侧的状态、审批时限和事件序号。候选人仍由引擎持有。
CREATE TABLE platform_approval.approval_tasks (
    task_id          text        NOT NULL,
    instance_id      text        NOT NULL,
    process_key      text        NOT NULL,
    document_type    text        NOT NULL CHECK (document_type IN ('QUOTE', 'TIMESHEET', 'JOURNAL_VOUCHER')),
    document_key     text        NOT NULL,
    legal_entity_id  bigint      NOT NULL,
    node_key         text        NOT NULL,
    status           text        NOT NULL CHECK (status IN ('PENDING', 'CLAIMED', 'COMPLETED', 'CLOSED')),
    assignee_id      bigint,
    due_at           timestamptz,
    escalate_at      timestamptz,
    overdue_at       timestamptz,
    escalated_at     timestamptz,
    approved_hash  text,
    event_count   bigint      NOT NULL DEFAULT 0 CHECK (event_count >= 0),
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    version          bigint      NOT NULL DEFAULT 0,
    CONSTRAINT pk_approval_tasks PRIMARY KEY (task_id),
    CONSTRAINT ck_approval_tasks_deadline CHECK ((due_at IS NULL) = (escalate_at IS NULL))
);
COMMENT ON TABLE platform_approval.approval_tasks IS '审批任务：每个引擎任务一行，记录办理状态、审批时限与提醒、升级时点';
COMMENT ON COLUMN platform_approval.approval_tasks.task_id IS '任务标识（引擎任务 ID）';
COMMENT ON COLUMN platform_approval.approval_tasks.instance_id IS '所属流程实例 ID';
COMMENT ON COLUMN platform_approval.approval_tasks.process_key IS '流程标识（ProcessKey 枚举名）';
COMMENT ON COLUMN platform_approval.approval_tasks.document_type IS '单据类型（DocumentType 枚举名）';
COMMENT ON COLUMN platform_approval.approval_tasks.document_key IS '单据键';
COMMENT ON COLUMN platform_approval.approval_tasks.legal_entity_id IS '法人主体（法人 ID）：审批时限按该法人的工作日历计算';
COMMENT ON COLUMN platform_approval.approval_tasks.node_key IS '节点键：流程图中的节点标识';
COMMENT ON COLUMN platform_approval.approval_tasks.status IS '任务状态：PENDING 待认领 / CLAIMED 已认领 / COMPLETED 已办理 / CLOSED 本轮已结束而关闭，用枚举不靠文案';
COMMENT ON COLUMN platform_approval.approval_tasks.assignee_id IS '处理人（人员 ID）：认领后唯一可以办理的人；未认领为空';
COMMENT ON COLUMN platform_approval.approval_tasks.due_at IS '截止时刻：提交日起第 n 个工作日结束；无审批时限的节点为空';
COMMENT ON COLUMN platform_approval.approval_tasks.escalate_at IS '升级时刻：截止后仍未处理，升级给审批人上级的时点';
COMMENT ON COLUMN platform_approval.approval_tasks.overdue_at IS '超时记录时刻：已发出超时提醒的时间；为空表示尚未超时';
COMMENT ON COLUMN platform_approval.approval_tasks.escalated_at IS '升级记录时刻：已升级的时间；为空表示尚未升级';
COMMENT ON COLUMN platform_approval.approval_tasks.approved_hash IS '通过时的内容摘要：同一审批人在同一节点再次出现且内容未变时，据此自动通过（RULE-302）；只在正式通过时记录';
COMMENT ON COLUMN platform_approval.approval_tasks.event_count IS '事件计数：该聚合已记录的领域事件数，下一个事件取其加一（信封的聚合版本）';
COMMENT ON COLUMN platform_approval.approval_tasks.created_at IS '创建时间';
COMMENT ON COLUMN platform_approval.approval_tasks.updated_at IS '更新时间';
COMMENT ON COLUMN platform_approval.approval_tasks.version IS '乐观锁版本';

CREATE INDEX ix_approval_tasks_instance_id ON platform_approval.approval_tasks (instance_id);
CREATE INDEX ix_approval_tasks_due_at ON platform_approval.approval_tasks (due_at) WHERE status IN ('PENDING', 'CLAIMED');
