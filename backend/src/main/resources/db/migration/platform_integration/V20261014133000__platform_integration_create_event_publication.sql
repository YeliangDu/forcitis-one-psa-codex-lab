-- 事件发布登记（Spring Modulith 事件登记，JDBC，标准结构 v2）：模块事件至少投递一次（RULE-295/303/304）。
-- 表由 Flyway 创建并归 platform.integration 的 schema 所有；spring.modulith.events.jdbc.schema 指向本 schema，
-- 框架自带的建表（schema-initialization）关闭。结构沿用框架的表，另加两个审计列（框架插入时指定列名，默认值生效）。
CREATE SCHEMA IF NOT EXISTS platform_integration;
COMMENT ON SCHEMA platform_integration IS '权限流程与集成平台 BC · 集成：外部系统防腐层、出站投递与事件发布登记';

CREATE TABLE platform_integration.event_publication (
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
CREATE INDEX event_publication_serialized_event_hash_idx ON platform_integration.event_publication USING hash (serialized_event);
CREATE INDEX event_publication_by_completion_date_idx ON platform_integration.event_publication (completion_date);
COMMENT ON TABLE platform_integration.event_publication IS '事件发布登记：每个事件对每个监听者一行；与发布事件的业务事务同提交，未完成的行重新投递（至少一次）';
COMMENT ON COLUMN platform_integration.event_publication.id IS '主键';
COMMENT ON COLUMN platform_integration.event_publication.listener_id IS '监听者标识（监听方法的全名）';
COMMENT ON COLUMN platform_integration.event_publication.event_type IS '事件类型（事件类的全名）';
COMMENT ON COLUMN platform_integration.event_publication.serialized_event IS '事件内容（JSON 文本）';
COMMENT ON COLUMN platform_integration.event_publication.publication_date IS '发布时间';
COMMENT ON COLUMN platform_integration.event_publication.completion_date IS '处理完成时间；为空表示尚未完成';
COMMENT ON COLUMN platform_integration.event_publication.status IS '状态：PUBLISHED 已发布 / PROCESSING 处理中 / COMPLETED 已完成 / FAILED 失败 / RESUBMITTED 已重新投递';
COMMENT ON COLUMN platform_integration.event_publication.completion_attempts IS '处理尝试次数';
COMMENT ON COLUMN platform_integration.event_publication.last_resubmission_date IS '最近一次重新投递时间';
COMMENT ON COLUMN platform_integration.event_publication.created_at IS '创建时间';
COMMENT ON COLUMN platform_integration.event_publication.updated_at IS '更新时间';
