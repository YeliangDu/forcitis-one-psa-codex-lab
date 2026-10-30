-- IMP-1030-PAR-C1（#931）：放行待放行离职后剩 1 名超管的告警卡（ADR-0068 第 2.3 条）：对象类别 SEPARATION_HOLD，用途 SEPARATION_HOLD_ALERT。
ALTER TABLE platform_integration.outbound_deliveries DROP CONSTRAINT ck_outbound_deliveries_subject_kind;
ALTER TABLE platform_integration.outbound_deliveries ADD CONSTRAINT ck_outbound_deliveries_subject_kind CHECK (subject_kind IN ('APPROVAL_TASK', 'APPROVAL_INSTANCE', 'SEPARATION_HOLD'));
ALTER TABLE platform_integration.outbound_deliveries DROP CONSTRAINT ck_outbound_deliveries_purpose_kind;
ALTER TABLE platform_integration.outbound_deliveries ADD CONSTRAINT ck_outbound_deliveries_purpose_kind CHECK (purpose_kind IN ('TASK_ASSIGNED', 'TASK_TRANSFERRED', 'TASK_REASSIGNED', 'TASK_OVERDUE', 'TASK_ESCALATED', 'APPROVAL_COMPLETED', 'SEPARATION_HOLD_ALERT'));
COMMENT ON COLUMN platform_integration.outbound_deliveries.subject_kind IS '对象类别 / 对象 ID：被记录或被投递的业务对象的类别与自有 ID。对象类别：APPROVAL_TASK 审批任务 / APPROVAL_INSTANCE 审批实例 / SEPARATION_HOLD 待放行离职';
COMMENT ON COLUMN platform_integration.outbound_deliveries.purpose_kind IS '投递用途：一张卡为什么发。TASK_ASSIGNED 任务指派 / TASK_TRANSFERRED 任务转交 / TASK_REASSIGNED 任务改派 / TASK_OVERDUE 任务超时 / TASK_ESCALATED 任务升级 / APPROVAL_COMPLETED 审批完成 / SEPARATION_HOLD_ALERT 放行后仅剩 1 名超管的告警';
