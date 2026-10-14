-- 事件发布登记的归档表（Spring Modulith completion-mode=archive）：处理完成的发布从 event_publication 移入本表，保留 30 天后由清理作业删除。
-- 结构沿用框架的归档表，另加两个审计列（框架插入时指定列名，默认值生效）。
CREATE TABLE platform_integration.event_publication_archive (
    id                     uuid        PRIMARY KEY,
    listener_id            text        NOT NULL,
    event_type             text        NOT NULL,
    serialized_event       text        NOT NULL,
    publication_date       timestamptz NOT NULL,
    completion_date        timestamptz,
    status                 text        CHECK (status IN ('PUBLISHED', 'PROCESSING', 'COMPLETED', 'FAILED', 'RESUBMITTED')),
    completion_attempts    integer,
    last_resubmission_date timestamptz,
    created_at             timestamptz NOT NULL DEFAULT now(),
    updated_at             timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX event_publication_archive_serialized_event_hash_idx ON platform_integration.event_publication_archive USING hash (serialized_event);
CREATE INDEX event_publication_archive_by_completion_date_idx ON platform_integration.event_publication_archive (completion_date);
COMMENT ON TABLE platform_integration.event_publication_archive IS '事件发布归档：已处理完成的发布，保留 30 天供排查与对账，之后由清理作业删除';
COMMENT ON COLUMN platform_integration.event_publication_archive.id IS '主键';
COMMENT ON COLUMN platform_integration.event_publication_archive.listener_id IS '监听者标识（监听方法的全名）';
COMMENT ON COLUMN platform_integration.event_publication_archive.event_type IS '事件类型（事件类的全名）';
COMMENT ON COLUMN platform_integration.event_publication_archive.serialized_event IS '事件内容（JSON 文本）';
COMMENT ON COLUMN platform_integration.event_publication_archive.publication_date IS '发布时间';
COMMENT ON COLUMN platform_integration.event_publication_archive.completion_date IS '处理完成时间';
COMMENT ON COLUMN platform_integration.event_publication_archive.status IS '状态：与 event_publication 相同';
COMMENT ON COLUMN platform_integration.event_publication_archive.completion_attempts IS '处理尝试次数';
COMMENT ON COLUMN platform_integration.event_publication_archive.last_resubmission_date IS '最近一次重新投递时间';
COMMENT ON COLUMN platform_integration.event_publication_archive.created_at IS '创建时间';
COMMENT ON COLUMN platform_integration.event_publication_archive.updated_at IS '更新时间';
