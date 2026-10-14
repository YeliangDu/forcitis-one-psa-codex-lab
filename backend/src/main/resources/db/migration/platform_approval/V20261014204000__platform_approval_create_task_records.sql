-- 任务办理记录：只追加（D-1a-90），认领、释放、办理、转交、改派都在审批请求保存的同一事务里写一行；已办从这里查。
-- 审批任务补节点名称，办理记录不回引擎取。新增的必填列没有默认值：库里若已有行则迁移失败，而不是给旧行补一个猜测的值（1a 修复批前不部署）。
ALTER TABLE platform_approval.approval_tasks ADD COLUMN node_name text NOT NULL;
COMMENT ON COLUMN platform_approval.approval_tasks.node_name IS '节点名称：任务创建时流程图里该节点的显示名；办理记录与已办列表直接用它';

CREATE TABLE platform_approval.approval_task_records (
    record_id        uuid        NOT NULL,
    process_key      text        NOT NULL,
    document_key     text        NOT NULL,
    round_number     integer     NOT NULL CHECK (round_number >= 1),
    task_id          text        NOT NULL,
    instance_id      text        NOT NULL,
    node_key         text        NOT NULL,
    node_name        text        NOT NULL,
    action_kind      text        NOT NULL CHECK (action_kind IN ('CLAIMED', 'RELEASED', 'APPROVED', 'REJECTED', 'RETURNED', 'ADVISED', 'PASSED_AUTOMATICALLY', 'TRANSFERRED', 'REASSIGNED')),
    actor_id         bigint      NOT NULL,
    on_behalf_of_id  bigint,
    assignee_id      bigint,
    record_reason    text,
    decided_at       timestamptz NOT NULL,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_approval_task_records PRIMARY KEY (record_id),
    CONSTRAINT fk_approval_task_records_task_id FOREIGN KEY (task_id) REFERENCES platform_approval.approval_tasks (task_id),
    CONSTRAINT fk_approval_task_records_document_key FOREIGN KEY (process_key, document_key)
        REFERENCES platform_approval.approval_requests (process_key, document_key)
);
COMMENT ON TABLE platform_approval.approval_task_records IS '任务办理记录：只追加；粒度：对任务做的每一次认领、释放、办理、转交、改派一行；已办列表与逐轮历史从这里查，不更新、不删除';
COMMENT ON COLUMN platform_approval.approval_task_records.record_id IS '记录标识：按时间有序的 UUID，列表翻页的游标取它';
COMMENT ON COLUMN platform_approval.approval_task_records.process_key IS '流程标识（ProcessKey 枚举名）';
COMMENT ON COLUMN platform_approval.approval_task_records.document_key IS '单据键';
COMMENT ON COLUMN platform_approval.approval_task_records.round_number IS '轮次：做这件事时单据所在的审批轮次';
COMMENT ON COLUMN platform_approval.approval_task_records.task_id IS '任务标识（引擎任务 ID）';
COMMENT ON COLUMN platform_approval.approval_task_records.instance_id IS '所属流程实例 ID';
COMMENT ON COLUMN platform_approval.approval_task_records.node_key IS '节点键：流程图中的节点标识';
COMMENT ON COLUMN platform_approval.approval_task_records.node_name IS '节点名称：做这件事时节点的显示名';
COMMENT ON COLUMN platform_approval.approval_task_records.action_kind IS '办理动作：CLAIMED 认领 / RELEASED 释放 / APPROVED 通过 / REJECTED 驳回 / RETURNED 退回 / ADVISED 加签建议 / PASSED_AUTOMATICALLY 系统自动通过 / TRANSFERRED 转交 / REASSIGNED 改派，用枚举不靠文案';
COMMENT ON COLUMN platform_approval.approval_task_records.actor_id IS '操作人（人员 ID）：做这件事的人；系统自动通过时是被自动通过的审批人';
COMMENT ON COLUMN platform_approval.approval_task_records.on_behalf_of_id IS '被代理人（人员 ID）：操作人替谁办（流程管理员改派时是原处理人）；不是代办就为空';
COMMENT ON COLUMN platform_approval.approval_task_records.assignee_id IS '处理人（人员 ID）：转交、改派之后持有任务的人；其他动作为空';
COMMENT ON COLUMN platform_approval.approval_task_records.record_reason IS '意见或原因：办理人填写的；没填就为空，不补默认值（RULE-280）';
COMMENT ON COLUMN platform_approval.approval_task_records.created_at IS '创建时间';
COMMENT ON COLUMN platform_approval.approval_task_records.updated_at IS '更新时间';
COMMENT ON COLUMN platform_approval.approval_task_records.decided_at IS '办理时刻：这件事发生的时间';

CREATE INDEX ix_approval_task_records_actor_id ON platform_approval.approval_task_records (actor_id, decided_at DESC, record_id DESC)
    WHERE action_kind IN ('APPROVED', 'REJECTED', 'RETURNED', 'ADVISED', 'PASSED_AUTOMATICALLY');
CREATE INDEX ix_approval_task_records_task_id ON platform_approval.approval_task_records (task_id);
CREATE INDEX ix_approval_task_records_process_key_document_key ON platform_approval.approval_task_records (process_key, document_key, round_number);

CREATE FUNCTION platform_approval.reject_task_record_change() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'platform_approval.approval_task_records is append-only: % is not allowed', TG_OP
        USING ERRCODE = 'restrict_violation';
END;
$$;

CREATE TRIGGER approval_task_records_reject_update_delete
    BEFORE UPDATE OR DELETE ON platform_approval.approval_task_records
    FOR EACH ROW EXECUTE FUNCTION platform_approval.reject_task_record_change();
