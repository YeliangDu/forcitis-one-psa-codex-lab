-- Idempotency-Key：同一用户、同一键、同一请求在 24 小时内得到第一次的应答；同一键换了请求体是 422。
CREATE SCHEMA IF NOT EXISTS platform_idempotency;
COMMENT ON SCHEMA platform_idempotency IS '幂等键模块：同一写请求重复发送时的应答存储';

CREATE TABLE platform_idempotency.idempotent_responses (
    user_id         bigint      NOT NULL,
    idempotency_key text        NOT NULL,
    request_hash    text        NOT NULL,
    response_number integer,
    response_name   text,
    response_text   text,
    expires_at      timestamptz NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_idempotent_responses PRIMARY KEY (user_id, idempotency_key)
);
CREATE INDEX ix_idempotent_responses_expires_at ON platform_idempotency.idempotent_responses (expires_at);
COMMENT ON TABLE platform_idempotency.idempotent_responses IS '幂等应答：粒度：每个用户的每个幂等键一行，到期（24 小时）后清除';
COMMENT ON COLUMN platform_idempotency.idempotent_responses.user_id IS '用户 ID：发出请求的已登录用户';
COMMENT ON COLUMN platform_idempotency.idempotent_responses.idempotency_key IS '幂等键：请求头 Idempotency-Key 的值（至多 128 个可见字符）';
COMMENT ON COLUMN platform_idempotency.idempotent_responses.request_hash IS '请求哈希：方法、路径、查询和请求体的 SHA-256 十六进制；同键不同哈希是误用';
COMMENT ON COLUMN platform_idempotency.idempotent_responses.response_number IS '应答状态码：请求仍在处理时为空';
COMMENT ON COLUMN platform_idempotency.idempotent_responses.response_name IS '应答内容类型名称，如 application/json';
COMMENT ON COLUMN platform_idempotency.idempotent_responses.response_text IS '应答正文（UTF-8 文本）';
COMMENT ON COLUMN platform_idempotency.idempotent_responses.expires_at IS '到期时间：创建后 24 小时';
COMMENT ON COLUMN platform_idempotency.idempotent_responses.created_at IS '创建时间';
COMMENT ON COLUMN platform_idempotency.idempotent_responses.updated_at IS '更新时间';
