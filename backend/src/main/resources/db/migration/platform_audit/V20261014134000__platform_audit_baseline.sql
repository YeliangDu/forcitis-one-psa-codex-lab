CREATE SCHEMA IF NOT EXISTS platform_audit;
COMMENT ON SCHEMA platform_audit IS '审计日志（权限流程与集成平台 BC）';

-- 审计订阅领域事件：事件号兼作幂等键，重复投递不产生重复条目；关联号把条目与同一操作的事件、日志串起来。
-- 登录与会话类条目的来源地址、结果、登录方式放在结构化列：员工查自己的登录记录只取这些列，不解析说明文字（意图 155、ADR-0027 第 3 项）。
CREATE TABLE platform_audit.audit_entries (
    id                uuid        PRIMARY KEY,
    action            text        NOT NULL,
    actor_id          uuid,
    on_behalf_of_id   uuid,
    subject_key       text        NOT NULL,
    detail            text,
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    event_id          uuid,
    correlation_id    uuid,
    change_reason     text,
    before_text       text,
    after_text        text,
    source_address    text,
    outcome_kind      text        CHECK (outcome_kind IN ('SUCCEEDED', 'FAILED')),
    login_method_kind text        CHECK (login_method_kind IN ('WECOM_WORKBENCH', 'WECOM_MINI_PROGRAM', 'WECOM_QR_CODE_LOGIN', 'SSO', 'SIGNED_TICKET', 'EMERGENCY'))
);
CREATE INDEX audit_entries_actor_idx ON platform_audit.audit_entries (actor_id, created_at DESC);
CREATE INDEX audit_entries_action_idx ON platform_audit.audit_entries (action, created_at DESC);
CREATE INDEX audit_entries_subject_idx ON platform_audit.audit_entries (subject_key, created_at DESC);
CREATE INDEX audit_entries_created_idx ON platform_audit.audit_entries (created_at DESC);
CREATE UNIQUE INDEX uk_audit_entries_event_id ON platform_audit.audit_entries (event_id);
COMMENT ON TABLE platform_audit.audit_entries IS '审计条目：只追加，数据库触发器禁止修改和删除（G34）；粒度：每个被审计的动作或领域事件一行';
COMMENT ON COLUMN platform_audit.audit_entries.id IS '审计条目 ID（UUIDv7）';
COMMENT ON COLUMN platform_audit.audit_entries.action IS '动作：{子域}.{限界上下文}.{对象}.{动词}，如 platform.identity.role.created';
COMMENT ON COLUMN platform_audit.audit_entries.actor_id IS '操作人用户 ID；系统动作（同步作业等）为空';
COMMENT ON COLUMN platform_audit.audit_entries.on_behalf_of_id IS '被代理人用户 ID；仅显式代理动作填写（RULE-270）';
COMMENT ON COLUMN platform_audit.audit_entries.subject_key IS '对象键：{类型}:{id}，如 approval-task:42';
COMMENT ON COLUMN platform_audit.audit_entries.detail IS '说明文字，不含密钥和个人敏感信息';
COMMENT ON COLUMN platform_audit.audit_entries.created_at IS '创建时间：即审计事实发生的时间，来自注入的时钟';
COMMENT ON COLUMN platform_audit.audit_entries.updated_at IS '更新时间（行不可更新，恒等于创建时间）';
COMMENT ON COLUMN platform_audit.audit_entries.event_id IS '事件号：由领域事件写入的条目才有；全表唯一，重复投递同一事件不产生第二条';
COMMENT ON COLUMN platform_audit.audit_entries.correlation_id IS '关联号：产生该条目的请求、作业或事件处理的关联号';
COMMENT ON COLUMN platform_audit.audit_entries.change_reason IS '变更说明：操作人填写的原因（放行、强制下线等必填原因的动作）';
COMMENT ON COLUMN platform_audit.audit_entries.before_text IS '变更前的值：只写业务值，不含密钥和个人敏感信息（L3 / L4 不进审计）';
COMMENT ON COLUMN platform_audit.audit_entries.after_text IS '变更后的值：规则同变更前';
COMMENT ON COLUMN platform_audit.audit_entries.source_address IS '来源地址：登录与会话类条目的客户端地址（只取受信代理解析后的地址）；其他条目为空';
COMMENT ON COLUMN platform_audit.audit_entries.outcome_kind IS '结果：SUCCEEDED / FAILED；登录与会话类条目才有，其他条目为空';
COMMENT ON COLUMN platform_audit.audit_entries.login_method_kind IS '登录方式：企业微信（工作台、小程序、扫码）、单点登录、签名票据、应急登录；其他条目为空';

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

-- 审计在线 3 年后按批写入归档库（对象存储，WORM）：每批一行记录覆盖的条目范围、对象键与校验值，只追加，作为归档进度。
CREATE TABLE platform_audit.audit_archive_batches (
    id             uuid        NOT NULL,
    first_entry_id uuid        NOT NULL,
    last_entry_id  uuid        NOT NULL,
    entry_count    integer     NOT NULL,
    object_key     text        NOT NULL,
    sha256_hash    text        NOT NULL,
    archived_at    timestamptz NOT NULL,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_audit_archive_batches PRIMARY KEY (id),
    CONSTRAINT uk_audit_archive_batches_last_entry_id UNIQUE (last_entry_id),
    CONSTRAINT ck_audit_archive_batches_range CHECK (first_entry_id <= last_entry_id AND entry_count > 0)
);
COMMENT ON TABLE platform_audit.audit_archive_batches IS '审计归档批次：已写入归档库的审计条目范围，只追加；粒度：每个归档批次一行';
COMMENT ON COLUMN platform_audit.audit_archive_batches.id IS '归档批次 ID（UUIDv7）';
COMMENT ON COLUMN platform_audit.audit_archive_batches.first_entry_id IS '本批第一条审计条目的 ID';
COMMENT ON COLUMN platform_audit.audit_archive_batches.last_entry_id IS '本批最后一条审计条目的 ID，也是归档进度：下一批从其后开始';
COMMENT ON COLUMN platform_audit.audit_archive_batches.entry_count IS '本批审计条目数';
COMMENT ON COLUMN platform_audit.audit_archive_batches.object_key IS '归档库中的对象键';
COMMENT ON COLUMN platform_audit.audit_archive_batches.sha256_hash IS '归档对象的 SHA-256（小写十六进制），归档库已确认一致';
COMMENT ON COLUMN platform_audit.audit_archive_batches.archived_at IS '归档完成时间';
COMMENT ON COLUMN platform_audit.audit_archive_batches.created_at IS '创建时间';
COMMENT ON COLUMN platform_audit.audit_archive_batches.updated_at IS '更新时间';

CREATE FUNCTION platform_audit.reject_archive_batch_change() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'platform_audit.audit_archive_batches is append-only: % is not allowed', TG_OP USING ERRCODE = 'restrict_violation';
END;
$$;
CREATE TRIGGER tg_audit_archive_batches_append_only BEFORE UPDATE OR DELETE ON platform_audit.audit_archive_batches
    FOR EACH ROW EXECUTE FUNCTION platform_audit.reject_archive_batch_change();
CREATE TRIGGER tg_audit_archive_batches_append_only_truncate BEFORE TRUNCATE ON platform_audit.audit_archive_batches
    FOR EACH STATEMENT EXECUTE FUNCTION platform_audit.reject_archive_batch_change();
