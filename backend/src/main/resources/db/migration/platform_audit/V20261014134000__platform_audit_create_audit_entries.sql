CREATE SCHEMA IF NOT EXISTS platform_audit;
COMMENT ON SCHEMA platform_audit IS '审计日志（权限流程与集成平台 BC）';

CREATE TABLE platform_audit.audit_entries (
    id              bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    action          text        NOT NULL,
    actor_id        bigint,
    on_behalf_of_id bigint,
    subject_key     text        NOT NULL,
    detail          text,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX audit_entries_actor_idx ON platform_audit.audit_entries (actor_id, created_at DESC);
CREATE INDEX audit_entries_action_idx ON platform_audit.audit_entries (action, created_at DESC);
CREATE INDEX audit_entries_subject_idx ON platform_audit.audit_entries (subject_key, created_at DESC);
CREATE INDEX audit_entries_created_idx ON platform_audit.audit_entries (created_at DESC);
COMMENT ON TABLE platform_audit.audit_entries IS '审计条目：只追加，数据库触发器禁止修改和删除（G34）';
COMMENT ON COLUMN platform_audit.audit_entries.id IS '审计条目 ID';
COMMENT ON COLUMN platform_audit.audit_entries.action IS '动作：{子域}.{限界上下文}.{对象}.{动词}，如 platform.identity.role.created';
COMMENT ON COLUMN platform_audit.audit_entries.actor_id IS '操作人 ID；系统动作（同步作业等）为空';
COMMENT ON COLUMN platform_audit.audit_entries.on_behalf_of_id IS '被代理人 ID；仅显式代理动作填写（RULE-270）';
COMMENT ON COLUMN platform_audit.audit_entries.subject_key IS '对象键：{类型}:{id}，如 approval-task:42';
COMMENT ON COLUMN platform_audit.audit_entries.detail IS '说明文字，不含密钥和个人敏感信息';
COMMENT ON COLUMN platform_audit.audit_entries.created_at IS '创建时间：即审计事实发生的时间，来自注入的时钟';
COMMENT ON COLUMN platform_audit.audit_entries.updated_at IS '更新时间（行不可更新，恒等于创建时间）';

CREATE FUNCTION platform_audit.reject_audit_entry_change() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'platform_audit.audit_entries is append-only: % is not allowed', TG_OP
        USING ERRCODE = 'restrict_violation';
END;
$$;

CREATE TRIGGER audit_entries_reject_update_delete
    BEFORE UPDATE OR DELETE ON platform_audit.audit_entries
    FOR EACH ROW EXECUTE FUNCTION platform_audit.reject_audit_entry_change();

CREATE TRIGGER audit_entries_reject_truncate
    BEFORE TRUNCATE ON platform_audit.audit_entries
    FOR EACH STATEMENT EXECUTE FUNCTION platform_audit.reject_audit_entry_change();
