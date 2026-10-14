CREATE TABLE platform_integration.outbound_deliveries (
    id                 bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    deduplication_key  text        NOT NULL UNIQUE,
    recipients         text        NOT NULL,
    skipped_recipients text,
    title              text        NOT NULL,
    description        text,
    target_url         text        NOT NULL,
    status             text        NOT NULL CHECK (status IN ('PENDING', 'SENT', 'SKIPPED', 'FAILED', 'DEAD_LETTER')),
    attempt_count      integer     NOT NULL DEFAULT 0,
    next_attempt_at    timestamptz,
    last_error         text,
    response_code      text,
    sent_at            timestamptz,
    handled_note       text,
    handled_result     text,
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX outbound_deliveries_due_idx ON platform_integration.outbound_deliveries (next_attempt_at) WHERE status = 'PENDING';
CREATE INDEX outbound_deliveries_dead_idx ON platform_integration.outbound_deliveries (status) WHERE status = 'DEAD_LETTER';
COMMENT ON TABLE platform_integration.outbound_deliveries IS '外发投递：企业微信文本卡片的待发队列与结果，事务提交后由投递作业发送，失败退避重试，超过次数进死信';
COMMENT ON COLUMN platform_integration.outbound_deliveries.id IS '投递 ID';
COMMENT ON COLUMN platform_integration.outbound_deliveries.deduplication_key IS '去重键：同一键只投递一次，也用于事后把卡片改为已处理，如 approval-task:42';
COMMENT ON COLUMN platform_integration.outbound_deliveries.recipients IS '收件人：企业微信用户 ID，以竖线分隔；只用精确用户投递，不用部门、标签或全员';
COMMENT ON COLUMN platform_integration.outbound_deliveries.skipped_recipients IS '被跳过的收件人：非生产环境不在白名单内的企业微信用户 ID，以竖线分隔';
COMMENT ON COLUMN platform_integration.outbound_deliveries.title IS '卡片标题';
COMMENT ON COLUMN platform_integration.outbound_deliveries.description IS '卡片说明';
COMMENT ON COLUMN platform_integration.outbound_deliveries.target_url IS '卡片点击后的跳转链接';
COMMENT ON COLUMN platform_integration.outbound_deliveries.status IS '投递状态：PENDING 待发送 / SENT 已发送 / SKIPPED 按规则跳过 / FAILED 无法发送（无收件人）/ DEAD_LETTER 重试耗尽进死信';
COMMENT ON COLUMN platform_integration.outbound_deliveries.attempt_count IS '已尝试发送次数';
COMMENT ON COLUMN platform_integration.outbound_deliveries.next_attempt_at IS '下次尝试时间；认领后为认领租约到期时间';
COMMENT ON COLUMN platform_integration.outbound_deliveries.last_error IS '最近一次失败或跳过的原因';
COMMENT ON COLUMN platform_integration.outbound_deliveries.response_code IS '企业微信发送后返回的卡片更新码，用于把卡片改为已处理；未返回则为空';
COMMENT ON COLUMN platform_integration.outbound_deliveries.sent_at IS '发送成功时间';
COMMENT ON COLUMN platform_integration.outbound_deliveries.handled_note IS '已处理说明，如“已由某人处理”';
COMMENT ON COLUMN platform_integration.outbound_deliveries.handled_result IS '把卡片改为已处理的结果：UPDATED 已更新 / NOT_APPLIED 未处理（附原因）/ FAILED 调用失败（附原因）';
COMMENT ON COLUMN platform_integration.outbound_deliveries.created_at IS '创建时间';
COMMENT ON COLUMN platform_integration.outbound_deliveries.updated_at IS '更新时间';
