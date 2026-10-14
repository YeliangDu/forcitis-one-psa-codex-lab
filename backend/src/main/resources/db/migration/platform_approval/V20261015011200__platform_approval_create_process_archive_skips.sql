-- 已写入归档库、但引擎删除失败的流程实例：记下来，之后不再重复归档；只追加，仍在引擎里的由告警日志每天提醒，直到人工处理。
CREATE TABLE platform_approval.process_archive_skips (
    process_instance_id text        NOT NULL,
    object_key          text        NOT NULL,
    failure_message     text        NOT NULL,
    skipped_at          timestamptz NOT NULL,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_process_archive_skips PRIMARY KEY (process_instance_id)
);
COMMENT ON TABLE platform_approval.process_archive_skips IS '被跳过的流程实例：已写入归档库但引擎删除失败，不再重复归档，只追加；粒度：每个被跳过的流程实例一行';
COMMENT ON COLUMN platform_approval.process_archive_skips.process_instance_id IS '流程实例 ID（引擎的实例标识）';
COMMENT ON COLUMN platform_approval.process_archive_skips.object_key IS '归档库中包含该实例的对象键';
COMMENT ON COLUMN platform_approval.process_archive_skips.failure_message IS '引擎删除失败的原因';
COMMENT ON COLUMN platform_approval.process_archive_skips.skipped_at IS '被跳过的时间';
COMMENT ON COLUMN platform_approval.process_archive_skips.created_at IS '创建时间';
COMMENT ON COLUMN platform_approval.process_archive_skips.updated_at IS '更新时间';

CREATE FUNCTION platform_approval.reject_process_archive_skip_change() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'platform_approval.process_archive_skips is append-only: % is not allowed', TG_OP USING ERRCODE = 'restrict_violation';
END;
$$;
CREATE TRIGGER tg_process_archive_skips_append_only BEFORE UPDATE OR DELETE ON platform_approval.process_archive_skips
    FOR EACH ROW EXECUTE FUNCTION platform_approval.reject_process_archive_skip_change();
CREATE TRIGGER tg_process_archive_skips_append_only_truncate BEFORE TRUNCATE ON platform_approval.process_archive_skips
    FOR EACH STATEMENT EXECUTE FUNCTION platform_approval.reject_process_archive_skip_change();
