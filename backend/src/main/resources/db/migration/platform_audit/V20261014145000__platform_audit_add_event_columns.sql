-- 审计订阅领域事件：事件号兼作幂等键，重复投递不产生重复条目；关联号把条目与同一操作的事件、日志串起来。
ALTER TABLE platform_audit.audit_entries
    ADD COLUMN event_id uuid,
    ADD COLUMN correlation_id uuid;
CREATE UNIQUE INDEX uk_audit_entries_event_id ON platform_audit.audit_entries (event_id);
COMMENT ON COLUMN platform_audit.audit_entries.event_id IS '事件号：由领域事件写入的条目才有；全表唯一，重复投递同一事件不产生第二条';
COMMENT ON COLUMN platform_audit.audit_entries.correlation_id IS '关联号：产生该条目的请求、作业或事件处理的关联号';
