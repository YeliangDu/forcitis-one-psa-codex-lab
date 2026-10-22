-- 审批请求与发起轮次。Flowable 的 ACT_* 表由引擎自己管理（生产 flowable.database-schema-update=false，升级走专门手册）。
CREATE SCHEMA IF NOT EXISTS platform_approval;
COMMENT ON SCHEMA platform_approval IS '审批（权限流程与集成平台 BC · 流程引擎封装）';

CREATE TABLE platform_approval.approval_requests (
    id                  uuid        PRIMARY KEY,
    process_key         text        NOT NULL,
    document_id         uuid        NOT NULL,
    initiator_id        uuid        NOT NULL,
    current_round       integer     NOT NULL,
    current_instance_id uuid        NOT NULL,
    status              text        NOT NULL CHECK (status IN ('RUNNING', 'RETURNED', 'APPROVED', 'REJECTED', 'CANCELLED')),
    result_reason       text,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    version             bigint      NOT NULL DEFAULT 0,
    document_type       text        NOT NULL CONSTRAINT ck_approval_requests_document_type CHECK (document_type ~ '^[A-Z][A-Z0-9_]{1,39}$'),
    legal_entity_id     uuid        NOT NULL,
    event_count         bigint      NOT NULL DEFAULT 0,
    summary_text        text        NOT NULL,
    CONSTRAINT uq_approval_requests_document UNIQUE (process_key, document_id),
    CONSTRAINT ck_approval_requests_event_count CHECK (event_count >= 0),
    CONSTRAINT ck_approval_requests_current_round CHECK (current_round >= 1),
    CONSTRAINT ck_approval_requests_result_reason CHECK (
        (status IN ('REJECTED', 'RETURNED', 'CANCELLED')) = (result_reason IS NOT NULL)
        AND (result_reason IS NULL OR result_reason <> '')),
    CONSTRAINT ck_approval_requests_summary_text CHECK (char_length(summary_text) BETWEEN 1 AND 200 AND summary_text ~ '\S')
);
CREATE UNIQUE INDEX uk_approval_requests_current_instance_id ON platform_approval.approval_requests (current_instance_id);
COMMENT ON TABLE platform_approval.approval_requests IS '审批请求：聚合根；粒度：每个流程类型的每张业务单据一行，记录当前轮次、当前流程实例与状态，本轮的任务属于它';
COMMENT ON COLUMN platform_approval.approval_requests.id IS '主键（UUIDv7）';
COMMENT ON COLUMN platform_approval.approval_requests.process_key IS '流程标识（ProcessKey 值，{子域}_{bc}_{名称} 小写，等于流程定义的 id）；与单据 ID 一起唯一，保证幂等发起（RULE-264/266）';
COMMENT ON COLUMN platform_approval.approval_requests.document_id IS '单据 ID：拥有该单据的业务模块的自有 ID，与单据类型一起定位单据';
COMMENT ON COLUMN platform_approval.approval_requests.initiator_id IS '发起人（员工 ID）：由拥有该单据的业务模块传入的当前用户；只有该人能开新一轮（I-5）';
COMMENT ON COLUMN platform_approval.approval_requests.current_round IS '当前轮次：从 1 开始，每次重新发起加 1（RULE-278）';
COMMENT ON COLUMN platform_approval.approval_requests.current_instance_id IS '当前（或最近一轮）流程实例 ID（UUID）；每一轮有自己的实例，历史按轮次保留；同一实例至多属于一个审批请求；不为空，指向发起轮次表中该请求最大轮次那一行的实例';
COMMENT ON COLUMN platform_approval.approval_requests.status IS '审批状态：RUNNING / RETURNED（本轮已退回并结束，重提开新一轮）/ APPROVED / REJECTED / CANCELLED（RULE-289），用枚举不靠文案';
COMMENT ON COLUMN platform_approval.approval_requests.result_reason IS '驳回、退回、撤销的原因；为空表示状态为运行中或已通过';
COMMENT ON COLUMN platform_approval.approval_requests.created_at IS '创建时间';
COMMENT ON COLUMN platform_approval.approval_requests.updated_at IS '更新时间';
COMMENT ON COLUMN platform_approval.approval_requests.version IS '乐观锁版本';
COMMENT ON COLUMN platform_approval.approval_requests.document_type IS '单据类型码：与流程标识一一对应；是否已登记由写入前经编号模块接口校验（单据类型登记在 platform_numbering.document_types，不建跨 schema 外键）';
COMMENT ON COLUMN platform_approval.approval_requests.legal_entity_id IS '法人主体（法人 ID）：由发起方传入，缺失即失败，不设默认';
COMMENT ON COLUMN platform_approval.approval_requests.event_count IS '事件序号：该聚合已记录的领域事件数，每个事件加一，下一个事件取其加一作信封的聚合版本；与乐观锁版本 version（每次保存加一）是两个计数';
COMMENT ON COLUMN platform_approval.approval_requests.summary_text IS '业务摘要：1 至 200 字符，发起时由拥有单据的业务模块给出，卡片显示它而不回查单据（本轮）；目前内容为单据编号、工作日、工时，写入方新增内容须先评估个人信息';

CREATE TABLE platform_approval.start_rounds (
    id               uuid        PRIMARY KEY,
    approval_request_id uuid     NOT NULL,
    round_number     integer     NOT NULL CHECK (round_number >= 1),
    initiator_id     uuid        NOT NULL,
    instance_id      uuid        NOT NULL,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    trigger_event_id uuid        NOT NULL,
    CONSTRAINT uq_start_rounds_round UNIQUE (approval_request_id, round_number),
    CONSTRAINT uk_start_rounds_trigger_event_id UNIQUE (trigger_event_id),
    CONSTRAINT uk_start_rounds_instance_id UNIQUE (instance_id),
    CONSTRAINT fk_start_rounds_approval_request_id FOREIGN KEY (approval_request_id) REFERENCES platform_approval.approval_requests (id)
);
COMMENT ON TABLE platform_approval.start_rounds IS '发起轮次：只追加；粒度：每个审批请求的每一轮一行；唯一约束消除并发重号（RULE-278）';
COMMENT ON COLUMN platform_approval.start_rounds.id IS '主键（UUIDv7）';
COMMENT ON COLUMN platform_approval.start_rounds.approval_request_id IS '审批请求 ID：这一轮属于哪个审批请求';
COMMENT ON COLUMN platform_approval.start_rounds.round_number IS '轮次：同一单据内从 1 递增';
COMMENT ON COLUMN platform_approval.start_rounds.initiator_id IS '发起人：该轮发起人（员工 ID）';
COMMENT ON COLUMN platform_approval.start_rounds.instance_id IS '该轮的流程实例 ID（UUID）；同一实例只属于一轮';
COMMENT ON COLUMN platform_approval.start_rounds.created_at IS '创建时间';
COMMENT ON COLUMN platform_approval.start_rounds.updated_at IS '更新时间';
COMMENT ON COLUMN platform_approval.start_rounds.trigger_event_id IS '触发该轮发起的事件 ID：同一事件重复投递只开一轮（幂等）';

CREATE FUNCTION platform_approval.reject_start_round_change() RETURNS trigger
    LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
BEGIN
    RAISE EXCEPTION 'platform_approval.start_rounds is append-only: % is not allowed', TG_OP USING ERRCODE = 'restrict_violation';
END;
$$;
CREATE TRIGGER start_rounds_reject_update_delete BEFORE UPDATE OR DELETE ON platform_approval.start_rounds
    FOR EACH ROW EXECUTE FUNCTION platform_approval.reject_start_round_change();
CREATE TRIGGER start_rounds_reject_truncate BEFORE TRUNCATE ON platform_approval.start_rounds
    FOR EACH STATEMENT EXECUTE FUNCTION platform_approval.reject_start_round_change();

-- 请求先于首轮写入，同一事务提交时才检查
ALTER TABLE platform_approval.approval_requests ADD CONSTRAINT fk_approval_requests_current_instance_id
    FOREIGN KEY (current_instance_id) REFERENCES platform_approval.start_rounds (instance_id) DEFERRABLE INITIALLY DEFERRED;

-- 审批任务属于审批请求聚合：随请求加锁、保存，不单独发事件；候选人仍由引擎持有。
CREATE TABLE platform_approval.approval_tasks (
    task_id         uuid        NOT NULL,
    instance_id     uuid        NOT NULL,
    approval_request_id uuid    NOT NULL,
    node_key        text        NOT NULL,
    status          text        NOT NULL CHECK (status IN ('PENDING', 'CLAIMED', 'COMPLETED', 'CLOSED')),
    assignee_id     uuid,
    due_at          timestamptz,
    escalate_at     timestamptz,
    overdue_at      timestamptz,
    escalated_at    timestamptz,
    approved_hash   text,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    remind_at       timestamptz,
    claimed_at      timestamptz,
    completed_at    timestamptz,
    outcome_kind    text        CHECK (outcome_kind IN ('APPROVED', 'REJECTED', 'RETURNED')),
    effect_kind     text        CHECK (effect_kind IN ('BINDING', 'ADVISORY', 'AUTOMATIC')),
    result_reason   text,
    node_name       text        NOT NULL,
    CONSTRAINT pk_approval_tasks PRIMARY KEY (task_id),
    CONSTRAINT ck_approval_tasks_deadline
        CHECK ((due_at IS NULL) = (remind_at IS NULL) AND (due_at IS NULL) = (escalate_at IS NULL)),
    CONSTRAINT ck_approval_tasks_claimed
        CHECK (status <> 'CLAIMED' OR (assignee_id IS NOT NULL AND claimed_at IS NOT NULL)),
    CONSTRAINT ck_approval_tasks_completed
        CHECK (status <> 'COMPLETED' OR (completed_at IS NOT NULL AND outcome_kind IS NOT NULL AND effect_kind IS NOT NULL)),
    CONSTRAINT fk_approval_tasks_approval_request_id FOREIGN KEY (approval_request_id) REFERENCES platform_approval.approval_requests (id),
    CONSTRAINT fk_approval_tasks_instance_id FOREIGN KEY (instance_id) REFERENCES platform_approval.start_rounds (instance_id),
    CONSTRAINT uk_approval_tasks_task_id_approval_request_id UNIQUE (task_id, approval_request_id),
    CONSTRAINT ck_approval_tasks_pending_unassigned CHECK (status <> 'PENDING' OR (assignee_id IS NULL AND claimed_at IS NULL)),
    CONSTRAINT ck_approval_tasks_outcome_effect CHECK ((outcome_kind IS NULL) = (effect_kind IS NULL)),
    CONSTRAINT ck_approval_tasks_automatic CHECK (effect_kind IS DISTINCT FROM 'AUTOMATIC' OR outcome_kind = 'APPROVED'),
    CONSTRAINT ck_approval_tasks_approved_hash CHECK (approved_hash IS NULL OR (outcome_kind = 'APPROVED' AND effect_kind = 'BINDING')),
    CONSTRAINT ck_approval_tasks_overdue CHECK (overdue_at IS NULL OR due_at IS NOT NULL),
    CONSTRAINT ck_approval_tasks_escalated CHECK (escalated_at IS NULL OR overdue_at IS NOT NULL),
    CONSTRAINT ck_approval_tasks_not_completed CHECK (status = 'COMPLETED' OR (completed_at IS NULL AND outcome_kind IS NULL
        AND effect_kind IS NULL AND result_reason IS NULL AND approved_hash IS NULL)),
    CONSTRAINT ck_approval_tasks_result_reason CHECK (
        (outcome_kind IS NULL OR outcome_kind NOT IN ('REJECTED', 'RETURNED') OR result_reason IS NOT NULL)
        AND (result_reason IS NULL OR result_reason <> ''))
);
CREATE INDEX ix_approval_tasks_instance_id ON platform_approval.approval_tasks (instance_id);
CREATE INDEX ix_approval_tasks_due_at ON platform_approval.approval_tasks (due_at) WHERE status IN ('PENDING', 'CLAIMED');
CREATE INDEX ix_approval_tasks_approval_request_id ON platform_approval.approval_tasks (approval_request_id);
CREATE INDEX ix_approval_tasks_open ON platform_approval.approval_tasks (created_at, task_id) WHERE status IN ('PENDING', 'CLAIMED');
CREATE INDEX ix_approval_tasks_escalate_at ON platform_approval.approval_tasks (escalate_at) WHERE status IN ('PENDING', 'CLAIMED') AND overdue_at IS NOT NULL AND escalated_at IS NULL;
CREATE INDEX ix_approval_tasks_remind_at ON platform_approval.approval_tasks (remind_at) WHERE status IN ('PENDING', 'CLAIMED');
CREATE INDEX ix_approval_tasks_assignee_id ON platform_approval.approval_tasks (assignee_id, completed_at DESC) WHERE status = 'COMPLETED';
COMMENT ON TABLE platform_approval.approval_tasks IS '审批任务：属于审批请求聚合；粒度：每个引擎任务一行，记录办理状态、办理结果、审批时限与提醒、升级时点；任务不单独加锁、不单独发事件';
COMMENT ON COLUMN platform_approval.approval_tasks.task_id IS '主键（UUIDv7）：任务 ID，由 ID 生成器产出，流程引擎沿用它作自己的任务 ID';
COMMENT ON COLUMN platform_approval.approval_tasks.instance_id IS '所属流程实例 ID（UUID）';
COMMENT ON COLUMN platform_approval.approval_tasks.approval_request_id IS '审批请求 ID：任务属于哪个审批请求';
COMMENT ON COLUMN platform_approval.approval_tasks.node_key IS '节点键：流程图中的节点标识';
COMMENT ON COLUMN platform_approval.approval_tasks.status IS '任务状态：PENDING 待认领 / CLAIMED 已认领 / COMPLETED 已办理 / CLOSED 本轮已结束而关闭，用枚举不靠文案';
COMMENT ON COLUMN platform_approval.approval_tasks.assignee_id IS '处理人（用户 ID）：认领后唯一可以办理的人；未认领为空';
COMMENT ON COLUMN platform_approval.approval_tasks.due_at IS '截止时刻：任务创建所在工作日之后第 n 个工作日 23:59；无审批时限的节点为空（D-1a-111）';
COMMENT ON COLUMN platform_approval.approval_tasks.escalate_at IS '升级时刻：提醒日之后第 n 个工作日 09:00，到点仍未办理就升级（D-1a-112、113）';
COMMENT ON COLUMN platform_approval.approval_tasks.overdue_at IS '超时记录时刻：已发出超时提醒的时间；为空表示尚未超时';
COMMENT ON COLUMN platform_approval.approval_tasks.escalated_at IS '升级记录时刻：已升级的时间；为空表示尚未升级';
COMMENT ON COLUMN platform_approval.approval_tasks.approved_hash IS '通过时的内容摘要：同一审批人在同一节点再次出现且内容未变时，据此自动通过（RULE-302）；只在正式通过时记录';
COMMENT ON COLUMN platform_approval.approval_tasks.created_at IS '创建时间';
COMMENT ON COLUMN platform_approval.approval_tasks.updated_at IS '更新时间';
COMMENT ON COLUMN platform_approval.approval_tasks.remind_at IS '提醒时刻：截止后下一个工作日 09:00；到点仍未办理就提醒审批人（D-1a-112）；无审批时限的节点为空';
COMMENT ON COLUMN platform_approval.approval_tasks.claimed_at IS '认领时刻：当前处理人认领的时间；回到候选组后清空';
COMMENT ON COLUMN platform_approval.approval_tasks.completed_at IS '办理时刻：任务办结的时间（含系统自动通过）；审批时效据此统计';
COMMENT ON COLUMN platform_approval.approval_tasks.outcome_kind IS '办理结果：APPROVED 通过 / REJECTED 驳回 / RETURNED 退回；未办结为空，用枚举不靠文案';
COMMENT ON COLUMN platform_approval.approval_tasks.effect_kind IS '办理效力：BINDING 正式 / ADVISORY 加签人的建议 / AUTOMATIC 系统自动通过（同一审批人同一节点内容未变）';
COMMENT ON COLUMN platform_approval.approval_tasks.result_reason IS '办理意见：审批人填写的意见；为空表示没填（通过时可不填，驳回、退回必填），不补默认值（RULE-280）';
COMMENT ON COLUMN platform_approval.approval_tasks.node_name IS '节点名称：任务创建时流程图里该节点的显示名；办理记录与已办列表直接用它';


-- 任务办理记录：只追加（D-1a-90），认领、释放、办理、转交、改派都在审批请求保存的同一事务里写一行；已办从这里查。节点名称随任务保存，办理记录不回引擎取。
CREATE TABLE platform_approval.approval_task_records (
    record_id        uuid        NOT NULL,
    approval_request_id uuid     NOT NULL,
    record_number    integer     NOT NULL CHECK (record_number >= 1),
    round_number     integer     NOT NULL CHECK (round_number >= 1),
    task_id          uuid        NOT NULL,
    instance_id      uuid        NOT NULL,
    node_key         text        NOT NULL,
    node_name        text        NOT NULL,
    action_kind      text        NOT NULL CHECK (action_kind IN ('CLAIMED', 'RELEASED', 'APPROVED', 'REJECTED', 'RETURNED', 'ADVISED', 'PASSED_AUTOMATICALLY', 'TRANSFERRED', 'REASSIGNED')),
    actor_id         uuid        NOT NULL,
    on_behalf_of_id  uuid,
    assignee_id      uuid,
    record_reason    text,
    decided_at       timestamptz NOT NULL,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_approval_task_records PRIMARY KEY (record_id),
    CONSTRAINT fk_approval_task_records_task_id FOREIGN KEY (task_id, approval_request_id)
        REFERENCES platform_approval.approval_tasks (task_id, approval_request_id),
    CONSTRAINT fk_approval_task_records_approval_request_id FOREIGN KEY (approval_request_id) REFERENCES platform_approval.approval_requests (id),
    CONSTRAINT fk_approval_task_records_instance_id FOREIGN KEY (instance_id) REFERENCES platform_approval.start_rounds (instance_id),
    CONSTRAINT uk_approval_task_records_record_number UNIQUE (approval_request_id, record_number),
    CONSTRAINT ck_approval_task_records_record_reason CHECK (
        (action_kind NOT IN ('REJECTED', 'RETURNED', 'TRANSFERRED', 'REASSIGNED') OR record_reason IS NOT NULL)
        AND (record_reason IS NULL OR record_reason <> '')),
    CONSTRAINT ck_approval_task_records_assignee CHECK ((action_kind IN ('TRANSFERRED', 'REASSIGNED')) = (assignee_id IS NOT NULL)),
    CONSTRAINT ck_approval_task_records_on_behalf_of CHECK (on_behalf_of_id IS NULL OR action_kind = 'REASSIGNED')
);
COMMENT ON TABLE platform_approval.approval_task_records IS '任务办理记录：只追加；粒度：每次对任务的认领、释放、办理、转交、改派一行；已办列表与逐轮历史从这里查，不更新、不删除';
COMMENT ON COLUMN platform_approval.approval_task_records.record_id IS '记录标识（UUIDv7）；不表示先后，先后看办理记录序号';
COMMENT ON COLUMN platform_approval.approval_task_records.approval_request_id IS '审批请求 ID：记录属于哪个审批请求';
COMMENT ON COLUMN platform_approval.approval_task_records.record_number IS '办理记录序号：同一审批请求内的递增序号，从 1 起，历史按它排列；与单据编号的流水号无关';
COMMENT ON COLUMN platform_approval.approval_task_records.round_number IS '轮次：做这件事时单据所在的审批轮次';
COMMENT ON COLUMN platform_approval.approval_task_records.task_id IS '任务 ID：做这件事的任务';
COMMENT ON COLUMN platform_approval.approval_task_records.instance_id IS '所属流程实例 ID（UUID）';
COMMENT ON COLUMN platform_approval.approval_task_records.node_key IS '节点键：流程图中的节点标识';
COMMENT ON COLUMN platform_approval.approval_task_records.node_name IS '节点名称：做这件事时节点的显示名';
COMMENT ON COLUMN platform_approval.approval_task_records.action_kind IS '办理动作：CLAIMED 认领 / RELEASED 释放 / APPROVED 通过 / REJECTED 驳回 / RETURNED 退回 / ADVISED 加签建议 / PASSED_AUTOMATICALLY 系统自动通过 / TRANSFERRED 转交 / REASSIGNED 改派，用枚举不靠文案';
COMMENT ON COLUMN platform_approval.approval_task_records.actor_id IS '操作人（用户 ID）：做这件事的人；系统自动通过时是被自动通过的审批人';
COMMENT ON COLUMN platform_approval.approval_task_records.on_behalf_of_id IS '被代理人（用户 ID）：操作人替谁办（流程管理员改派时是原处理人）；不是代办就为空；为空表示不是改派，或改派时任务尚无处理人';
COMMENT ON COLUMN platform_approval.approval_task_records.assignee_id IS '处理人（用户 ID）：转交、改派之后持有任务的人；其他动作为空；为空表示动作不是转交、改派';
COMMENT ON COLUMN platform_approval.approval_task_records.record_reason IS '意见或原因：办理人填写的；为空表示没填（驳回、退回、转交、改派必填，其余可不填），不补默认值（RULE-280）';
COMMENT ON COLUMN platform_approval.approval_task_records.created_at IS '创建时间';
COMMENT ON COLUMN platform_approval.approval_task_records.updated_at IS '更新时间';
COMMENT ON COLUMN platform_approval.approval_task_records.decided_at IS '办理时刻：这件事发生的时间';

CREATE INDEX ix_approval_task_records_actor_id ON platform_approval.approval_task_records (actor_id, decided_at DESC, record_id DESC)
    WHERE action_kind <> 'PASSED_AUTOMATICALLY';
CREATE INDEX ix_approval_task_records_task_id ON platform_approval.approval_task_records (task_id);
CREATE INDEX ix_approval_task_records_instance_id ON platform_approval.approval_task_records (instance_id);

CREATE FUNCTION platform_approval.reject_task_record_change() RETURNS trigger
    LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
BEGIN
    RAISE EXCEPTION 'platform_approval.approval_task_records is append-only: % is not allowed', TG_OP
        USING ERRCODE = 'restrict_violation';
END;
$$;

CREATE TRIGGER approval_task_records_reject_update_delete
    BEFORE UPDATE OR DELETE ON platform_approval.approval_task_records
    FOR EACH ROW EXECUTE FUNCTION platform_approval.reject_task_record_change();

CREATE TRIGGER approval_task_records_reject_truncate
    BEFORE TRUNCATE ON platform_approval.approval_task_records
    FOR EACH STATEMENT EXECUTE FUNCTION platform_approval.reject_task_record_change();

-- 已写入归档库、但引擎删除失败的流程实例：记下来，之后不再重复归档；只追加，仍在引擎里的由告警日志每天提醒，直到人工处理。
CREATE TABLE platform_approval.process_archive_skips (
    process_archive_skip_id uuid    NOT NULL,
    process_instance_id uuid        NOT NULL,
    object_key          text        NOT NULL,
    failure_message     text        NOT NULL,
    skipped_at          timestamptz NOT NULL,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_process_archive_skips PRIMARY KEY (process_archive_skip_id),
    CONSTRAINT uk_process_archive_skips_process_instance_id UNIQUE (process_instance_id),
    CONSTRAINT ck_process_archive_skips_failure_message CHECK (char_length(failure_message) BETWEEN 1 AND 2000)
);
COMMENT ON TABLE platform_approval.process_archive_skips IS '被跳过的流程实例：已写入归档库但引擎删除失败，不再重复归档，只追加；粒度：每个被跳过的流程实例一行';
COMMENT ON COLUMN platform_approval.process_archive_skips.process_archive_skip_id IS '主键（UUIDv7）';
COMMENT ON COLUMN platform_approval.process_archive_skips.process_instance_id IS '流程实例 ID（UUID）；同一实例只记一次跳过';
COMMENT ON COLUMN platform_approval.process_archive_skips.object_key IS '归档库中包含该实例的对象键';
COMMENT ON COLUMN platform_approval.process_archive_skips.failure_message IS '引擎删除失败的异常类名链（由外到内，至多 5 层），不含消息；消息可能带数据库服务端错误详情（GUIDING_PRINCIPLES §11 O-3）';
COMMENT ON COLUMN platform_approval.process_archive_skips.skipped_at IS '被跳过的时间';
COMMENT ON COLUMN platform_approval.process_archive_skips.created_at IS '创建时间';
COMMENT ON COLUMN platform_approval.process_archive_skips.updated_at IS '更新时间';

CREATE FUNCTION platform_approval.reject_process_archive_skip_change() RETURNS trigger
    LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
BEGIN
    RAISE EXCEPTION 'platform_approval.process_archive_skips is append-only: % is not allowed', TG_OP USING ERRCODE = 'restrict_violation';
END;
$$;
CREATE TRIGGER tg_process_archive_skips_append_only BEFORE UPDATE OR DELETE ON platform_approval.process_archive_skips
    FOR EACH ROW EXECUTE FUNCTION platform_approval.reject_process_archive_skip_change();
CREATE TRIGGER tg_process_archive_skips_append_only_truncate BEFORE TRUNCATE ON platform_approval.process_archive_skips
    FOR EACH STATEMENT EXECUTE FUNCTION platform_approval.reject_process_archive_skip_change();
