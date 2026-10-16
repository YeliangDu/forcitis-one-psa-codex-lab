CREATE SCHEMA IF NOT EXISTS platform_idempotency;
COMMENT ON SCHEMA platform_idempotency IS '幂等键模块：同一写请求重复发送时的应答存储';

-- Idempotency-Key：同一用户、同一键、同一请求在 24 小时内得到第一次的应答；同一键换了请求体是 422。
CREATE TABLE platform_idempotency.idempotent_responses (
    idempotent_response_id uuid NOT NULL,
    user_id         uuid        NOT NULL,
    idempotency_key text        NOT NULL,
    request_hash    text        NOT NULL,
    response_number integer,
    response_name   text,
    response_text   text,
    response_header_text text,
    expires_at      timestamptz NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_idempotent_responses PRIMARY KEY (idempotent_response_id),
    CONSTRAINT uk_idempotent_responses_user_id_idempotency_key UNIQUE (user_id, idempotency_key),
    CONSTRAINT ck_idempotent_responses_idempotency_key CHECK (char_length(idempotency_key) BETWEEN 1 AND 128),
    CONSTRAINT ck_idempotent_responses_request_hash CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_idempotent_responses_response_number CHECK (response_number BETWEEN 100 AND 599),
    CONSTRAINT ck_idempotent_responses_response_text CHECK (char_length(response_text) <= 32768),
    CONSTRAINT ck_idempotent_responses_in_progress CHECK (response_number IS NOT NULL OR (response_name IS NULL AND response_text IS NULL AND response_header_text IS NULL))
);
CREATE INDEX ix_idempotent_responses_expires_at ON platform_idempotency.idempotent_responses (expires_at);
COMMENT ON TABLE platform_idempotency.idempotent_responses IS '幂等应答：粒度：每个用户的每个幂等键一行，到期（24 小时）后清除';
COMMENT ON COLUMN platform_idempotency.idempotent_responses.idempotent_response_id IS '幂等应答 ID（UUIDv7）';
COMMENT ON COLUMN platform_idempotency.idempotent_responses.user_id IS '用户 ID：发出请求的已登录用户';
COMMENT ON COLUMN platform_idempotency.idempotent_responses.idempotency_key IS '幂等键：请求头 Idempotency-Key 的值（至多 128 个可见字符）';
COMMENT ON COLUMN platform_idempotency.idempotent_responses.request_hash IS '请求哈希：方法、路径、查询和请求体的 SHA-256 十六进制；同键不同哈希是误用';
COMMENT ON COLUMN platform_idempotency.idempotent_responses.response_number IS '应答状态码：请求仍在处理时为空';
COMMENT ON COLUMN platform_idempotency.idempotent_responses.response_name IS '应答内容类型名称，如 application/json';
COMMENT ON COLUMN platform_idempotency.idempotent_responses.response_text IS '应答正文（UTF-8 文本，至多 32768 个字符；仅白名单接口，不含直接标识）';
COMMENT ON COLUMN platform_idempotency.idempotent_responses.response_header_text IS '应答头：要原样回放的头（ETag、Location）的 JSON 对象';
COMMENT ON COLUMN platform_idempotency.idempotent_responses.expires_at IS '到期时间：创建后 24 小时';
COMMENT ON COLUMN platform_idempotency.idempotent_responses.created_at IS '创建时间';
COMMENT ON COLUMN platform_idempotency.idempotent_responses.updated_at IS '更新时间';
