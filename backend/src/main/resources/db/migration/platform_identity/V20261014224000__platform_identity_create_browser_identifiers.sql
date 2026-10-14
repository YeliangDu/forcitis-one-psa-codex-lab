-- C-M7：浏览器标识。单点登录或外部票据登录的会话，只有认得这个浏览器时才免于「是否本人」确认。
CREATE TABLE platform_identity.browser_identifiers (
    browser_identifier_id uuid        NOT NULL,
    user_id               bigint      NOT NULL,
    identifier_hash       text        NOT NULL,
    last_login_at         timestamptz NOT NULL,
    retired_at            timestamptz,
    created_at            timestamptz NOT NULL DEFAULT now(),
    updated_at            timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_browser_identifiers PRIMARY KEY (browser_identifier_id),
    CONSTRAINT fk_browser_identifiers_user_id FOREIGN KEY (user_id) REFERENCES platform_identity.user_accounts (user_id) ON DELETE CASCADE,
    CONSTRAINT uk_browser_identifiers_identifier_hash_user_id UNIQUE (identifier_hash, user_id)
);
CREATE INDEX ix_browser_identifiers_user_id ON platform_identity.browser_identifiers (user_id);
COMMENT ON TABLE platform_identity.browser_identifiers IS '浏览器标识：某个账号在某个浏览器上已确认过本人的凭据摘要；每次免确认登录换新值，旧值留作重放检测；粒度：每个账号的每个标识值一行';
COMMENT ON COLUMN platform_identity.browser_identifiers.browser_identifier_id IS '浏览器标识 ID';
COMMENT ON COLUMN platform_identity.browser_identifiers.user_id IS '用户 ID：标识所属账号';
COMMENT ON COLUMN platform_identity.browser_identifiers.identifier_hash IS '标识摘要：浏览器所持随机值的 SHA-256，库内不存原值';
COMMENT ON COLUMN platform_identity.browser_identifiers.last_login_at IS '最近登录时间：该标识最近一次被用于登录；超过保留天数视为过期';
COMMENT ON COLUMN platform_identity.browser_identifiers.retired_at IS '换新时间：已被新值取代的时间；为空表示仍有效。已换新的旧值再次出现即为重放';
COMMENT ON COLUMN platform_identity.browser_identifiers.created_at IS '创建时间';
COMMENT ON COLUMN platform_identity.browser_identifiers.updated_at IS '更新时间';
