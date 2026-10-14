-- 审批任务并入审批请求聚合：任务不再有自己的版本与事件序号，随请求加锁、保存；补认领 / 办理时刻、办理结果与意见、提醒时点（数据打分卡 K5、K6）。
-- 不给旧行补猜测的值：新增的必填列没有默认值、收紧的约束校验全部既有行，库里若已有审批任务则迁移失败。1a 未上线、没有生产数据；dev 库先清空审批与工时单业务表再部署（PREDEPLOY_CHECKLIST B-10）。
ALTER TABLE platform_approval.approval_requests ADD COLUMN summary_text text NOT NULL;
COMMENT ON COLUMN platform_approval.approval_requests.summary_text IS '业务摘要：发起时由拥有单据的业务模块给出，卡片显示它而不回查单据（本轮）';
COMMENT ON COLUMN platform_approval.approval_requests.event_count IS '事件序号：该聚合已记录的领域事件数，每个事件加一，下一个事件取其加一作信封的聚合版本；与乐观锁版本 version（每次保存加一）是两个计数';
COMMENT ON TABLE platform_approval.approval_requests IS '审批请求：聚合根；粒度：每个流程类型的每张业务单据一行，记录当前轮次、当前流程实例与状态，本轮的任务属于它';

ALTER TABLE platform_approval.start_rounds ADD COLUMN trigger_event_id uuid NOT NULL;
ALTER TABLE platform_approval.start_rounds ADD CONSTRAINT uk_start_rounds_trigger_event_id UNIQUE (trigger_event_id);
ALTER TABLE platform_approval.start_rounds ADD CONSTRAINT fk_start_rounds_document_key FOREIGN KEY (process_key, document_key)
    REFERENCES platform_approval.approval_requests (process_key, document_key);
COMMENT ON COLUMN platform_approval.start_rounds.trigger_event_id IS '触发该轮发起的事件 ID：同一事件重复投递只开一轮（幂等）';
COMMENT ON TABLE platform_approval.start_rounds IS '发起轮次：粒度：每个审批请求的每一轮一行；唯一约束消除并发重号（RULE-278）';

ALTER TABLE platform_approval.approval_tasks DROP COLUMN version;
ALTER TABLE platform_approval.approval_tasks DROP COLUMN event_count;
ALTER TABLE platform_approval.approval_tasks DROP COLUMN legal_entity_id;
ALTER TABLE platform_approval.approval_tasks DROP COLUMN document_type;
ALTER TABLE platform_approval.approval_tasks ADD COLUMN remind_at timestamptz;
ALTER TABLE platform_approval.approval_tasks ADD COLUMN claimed_at timestamptz;
ALTER TABLE platform_approval.approval_tasks ADD COLUMN completed_at timestamptz;
ALTER TABLE platform_approval.approval_tasks ADD COLUMN outcome_kind text CHECK (outcome_kind IN ('APPROVED', 'REJECTED', 'RETURNED'));
ALTER TABLE platform_approval.approval_tasks ADD COLUMN effect_kind text CHECK (effect_kind IN ('BINDING', 'ADVISORY', 'AUTOMATIC'));
ALTER TABLE platform_approval.approval_tasks ADD COLUMN result_reason text;
ALTER TABLE platform_approval.approval_tasks DROP CONSTRAINT ck_approval_tasks_deadline;
ALTER TABLE platform_approval.approval_tasks ADD CONSTRAINT ck_approval_tasks_deadline
    CHECK ((due_at IS NULL) = (remind_at IS NULL) AND (due_at IS NULL) = (escalate_at IS NULL));
ALTER TABLE platform_approval.approval_tasks ADD CONSTRAINT ck_approval_tasks_claimed
    CHECK (status <> 'CLAIMED' OR (assignee_id IS NOT NULL AND claimed_at IS NOT NULL));
ALTER TABLE platform_approval.approval_tasks ADD CONSTRAINT ck_approval_tasks_completed
    CHECK (status <> 'COMPLETED' OR (completed_at IS NOT NULL AND outcome_kind IS NOT NULL AND effect_kind IS NOT NULL));
ALTER TABLE platform_approval.approval_tasks ADD CONSTRAINT fk_approval_tasks_document_key FOREIGN KEY (process_key, document_key)
    REFERENCES platform_approval.approval_requests (process_key, document_key);

COMMENT ON TABLE platform_approval.approval_tasks IS '审批任务：属于审批请求聚合；粒度：每个引擎任务一行，记录办理状态、办理结果、审批时限与提醒、升级时点；任务不单独加锁、不单独发事件';
COMMENT ON COLUMN platform_approval.approval_tasks.remind_at IS '提醒时刻：截止后下一个工作日 09:00；到点仍未办理就提醒审批人（D-1a-112）；无审批时限的节点为空';
COMMENT ON COLUMN platform_approval.approval_tasks.due_at IS '截止时刻：任务创建所在工作日之后第 n 个工作日 23:59；无审批时限的节点为空（D-1a-111）';
COMMENT ON COLUMN platform_approval.approval_tasks.escalate_at IS '升级时刻：提醒日之后第 n 个工作日 09:00，到点仍未办理就升级（D-1a-112、113）';
COMMENT ON COLUMN platform_approval.approval_tasks.claimed_at IS '认领时刻：当前处理人认领的时间；回到候选组后清空';
COMMENT ON COLUMN platform_approval.approval_tasks.completed_at IS '办理时刻：任务办结的时间（含系统自动通过）；审批时效据此统计';
COMMENT ON COLUMN platform_approval.approval_tasks.outcome_kind IS '办理结果：APPROVED 通过 / REJECTED 驳回 / RETURNED 退回；未办结为空，用枚举不靠文案';
COMMENT ON COLUMN platform_approval.approval_tasks.effect_kind IS '办理效力：BINDING 正式 / ADVISORY 加签人的建议 / AUTOMATIC 系统自动通过（同一审批人同一节点内容未变）';
COMMENT ON COLUMN platform_approval.approval_tasks.result_reason IS '办理意见：审批人填写的意见；没填就为空，不补默认值（RULE-280）';

CREATE INDEX ix_approval_tasks_process_key_document_key ON platform_approval.approval_tasks (process_key, document_key);
CREATE INDEX ix_approval_tasks_remind_at ON platform_approval.approval_tasks (remind_at) WHERE status IN ('PENDING', 'CLAIMED');
CREATE INDEX ix_approval_tasks_assignee_id ON platform_approval.approval_tasks (assignee_id, completed_at DESC) WHERE status = 'COMPLETED';
