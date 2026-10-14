-- 审计在线 3 年后按批写入归档库（对象存储，WORM）：每批一行记录覆盖的条目范围、对象键与校验值，只追加，作为归档进度。
CREATE TABLE platform_audit.audit_archive_batches (
    id             bigint      GENERATED ALWAYS AS IDENTITY,
    first_entry_id bigint      NOT NULL,
    last_entry_id  bigint      NOT NULL,
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
COMMENT ON COLUMN platform_audit.audit_archive_batches.id IS '归档批次 ID';
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
