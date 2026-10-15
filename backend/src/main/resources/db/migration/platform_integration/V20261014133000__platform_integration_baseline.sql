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

CREATE TABLE platform_integration.outbound_deliveries (
    id                      bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    deduplication_key       text        NOT NULL UNIQUE,
    title                   text        NOT NULL,
    description             text,
    target_url              text        NOT NULL,
    status                  text        NOT NULL CHECK (status IN ('PENDING', 'SENT', 'SKIPPED', 'FAILED', 'DEAD_LETTER')),
    attempt_count           integer     NOT NULL DEFAULT 0,
    next_attempt_at         timestamptz,
    last_error              text,
    response_code           text,
    sent_at                 timestamptz,
    handled_note            text,
    handled_result          text,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    kind                    text        NOT NULL DEFAULT 'CARD' CHECK (kind IN ('CARD', 'HANDLED_NOTE')),
    target_key              text,
    version                 bigint      NOT NULL DEFAULT 0,
    correlation_id          uuid,
    event_count             bigint      NOT NULL DEFAULT 0,
    recipient_codes         text[]      NOT NULL,
    skipped_recipient_codes text[],
    CONSTRAINT outbound_deliveries_handled_note_target CHECK ((kind = 'HANDLED_NOTE') = (target_key IS NOT NULL))
);
CREATE INDEX outbound_deliveries_due_idx ON platform_integration.outbound_deliveries (next_attempt_at) WHERE status = 'PENDING';
CREATE INDEX outbound_deliveries_dead_idx ON platform_integration.outbound_deliveries (status) WHERE status = 'DEAD_LETTER';
COMMENT ON TABLE platform_integration.outbound_deliveries IS '外发投递：企业微信文本卡片的待发队列与结果，事务提交后由投递作业发送，失败退避重试，超过次数进死信；粒度：每张待发或已发的卡片一行（按去重键唯一）';
COMMENT ON COLUMN platform_integration.outbound_deliveries.id IS '投递 ID';
COMMENT ON COLUMN platform_integration.outbound_deliveries.deduplication_key IS '去重键：同一键只投递一次，也用于事后把卡片改为已处理，如 approval-task:42';
COMMENT ON COLUMN platform_integration.outbound_deliveries.title IS '卡片标题';
COMMENT ON COLUMN platform_integration.outbound_deliveries.description IS '卡片说明';
COMMENT ON COLUMN platform_integration.outbound_deliveries.target_url IS '卡片点击后的跳转链接';
COMMENT ON COLUMN platform_integration.outbound_deliveries.status IS '投递状态：PENDING 待发送 / SENT 已发送 / SKIPPED 按规则跳过 / FAILED 无法发送（无收件人）/ DEAD_LETTER 重试耗尽进死信';
COMMENT ON COLUMN platform_integration.outbound_deliveries.attempt_count IS '已尝试发送次数；也是认领编号：状态变更只在编号与认领时一致时生效，过期的认领写入被拒绝';
COMMENT ON COLUMN platform_integration.outbound_deliveries.next_attempt_at IS '下次尝试时间；认领后为认领租约到期时间';
COMMENT ON COLUMN platform_integration.outbound_deliveries.last_error IS '最近一次失败或跳过的原因';
COMMENT ON COLUMN platform_integration.outbound_deliveries.response_code IS '企业微信发送后返回的卡片更新码，用于把卡片改为已处理；未返回则为空';
COMMENT ON COLUMN platform_integration.outbound_deliveries.sent_at IS '发送成功时间';
COMMENT ON COLUMN platform_integration.outbound_deliveries.handled_note IS '已处理说明，如“已由某人处理”';
COMMENT ON COLUMN platform_integration.outbound_deliveries.handled_result IS '把卡片改为已处理的结果：UPDATED 已更新 / NOT_APPLIED 未处理（附原因）/ FAILED 调用失败（附原因）';
COMMENT ON COLUMN platform_integration.outbound_deliveries.created_at IS '创建时间';
COMMENT ON COLUMN platform_integration.outbound_deliveries.updated_at IS '更新时间';
COMMENT ON COLUMN platform_integration.outbound_deliveries.kind IS '投递种类：CARD 发送卡片 / HANDLED_NOTE 把已发送的卡片替换为已处理说明；两种共用同一套重试与死信';
COMMENT ON COLUMN platform_integration.outbound_deliveries.target_key IS '被替换卡片的去重键；仅 HANDLED_NOTE 投递有值，其说明文字存放在 description';
COMMENT ON COLUMN platform_integration.outbound_deliveries.version IS '乐观锁版本';
COMMENT ON COLUMN platform_integration.outbound_deliveries.correlation_id IS '关联号：产生这条投递的请求、作业或事件处理的关联号；投递作业恢复它，发出的请求带上它';
COMMENT ON COLUMN platform_integration.outbound_deliveries.event_count IS '事件序号：该聚合已发出的事件数，每发一个事件加 1，作为事件信封的 aggregateVersion；与乐观锁 version 分开';
COMMENT ON COLUMN platform_integration.outbound_deliveries.recipient_codes IS '收件人：企业微信用户 ID，每人一个元素；只用精确用户投递，不用部门、标签或全员';
COMMENT ON COLUMN platform_integration.outbound_deliveries.skipped_recipient_codes IS '被跳过的收件人：测试环境白名单之外的企业微信用户 ID，每人一个元素；没有则为空';

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
