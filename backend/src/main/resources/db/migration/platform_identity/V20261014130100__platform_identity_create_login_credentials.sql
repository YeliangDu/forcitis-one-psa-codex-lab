CREATE TABLE platform_identity.login_states (
    state_hash text        PRIMARY KEY,
    entry      text        NOT NULL CHECK (entry IN ('WORKBENCH', 'MINI_PROGRAM', 'WEB_SCAN')),
    expires_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE platform_identity.login_states IS '企业微信 OAuth state：只存哈希；一次性，消费时原子删除（I-9）';
COMMENT ON COLUMN platform_identity.login_states.state_hash IS 'state 的 SHA-256 十六进制';
COMMENT ON COLUMN platform_identity.login_states.entry IS '签发时声明的企业微信登录入口';
COMMENT ON COLUMN platform_identity.login_states.expires_at IS '过期时间（签发后 10 分钟）';
COMMENT ON COLUMN platform_identity.login_states.created_at IS '创建时间';
COMMENT ON COLUMN platform_identity.login_states.updated_at IS '更新时间';

CREATE TABLE platform_identity.sso_ticket_uses (
    ticket_source text        NOT NULL,
    ticket_hash   text        NOT NULL,
    expires_at    timestamptz NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (ticket_source, ticket_hash)
);
CREATE INDEX ix_sso_ticket_uses_expiry ON platform_identity.sso_ticket_uses (expires_at);
COMMENT ON TABLE platform_identity.sso_ticket_uses IS '已使用的一次性凭证：主键即唯一约束，并发重放只有一次插入成功（I-9，RULE-320）';
COMMENT ON COLUMN platform_identity.sso_ticket_uses.ticket_source IS '凭证来源：SSO（门户票据），或签发方编码（签名票据的 nonce）';
COMMENT ON COLUMN platform_identity.sso_ticket_uses.ticket_hash IS '票据或 nonce 的 SHA-256 十六进制，不存明文';
COMMENT ON COLUMN platform_identity.sso_ticket_uses.expires_at IS '记录保留到该时间后清理（门户票据 7 天，签名票据略长于票据有效期）';
COMMENT ON COLUMN platform_identity.sso_ticket_uses.created_at IS '创建时间';
COMMENT ON COLUMN platform_identity.sso_ticket_uses.updated_at IS '更新时间';

CREATE TABLE platform_identity.ticket_issuers (
    issuer_code        text        PRIMARY KEY,
    key_id             text        NOT NULL UNIQUE,
    key_reference      text        NOT NULL,
    is_enabled         boolean     NOT NULL DEFAULT false,
    allowed_role_codes text[]      NOT NULL DEFAULT '{}',
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_ticket_issuers_no_administrator CHECK (NOT ('super_admin' = ANY (allowed_role_codes)))
);
COMMENT ON TABLE platform_identity.ticket_issuers IS '票据签发方：外部签名票据的每方一把密钥（kid 区分），默认关闭；签发方必须在 architecture/ticket-issuers-allowlist.txt 中（I-2）';
COMMENT ON COLUMN platform_identity.ticket_issuers.issuer_code IS '签发方编码，如 km；老 PSA 后端永远不得出现（I-2）';
COMMENT ON COLUMN platform_identity.ticket_issuers.key_id IS '密钥标识 kid，票据头部携带，一方一把';
COMMENT ON COLUMN platform_identity.ticket_issuers.key_reference IS '密钥引用，如 env:NAME；密钥本身只在环境变量或 KMS，表中不存';
COMMENT ON COLUMN platform_identity.ticket_issuers.is_enabled IS '是否开启：调用方完成对接前保持关闭';
COMMENT ON COLUMN platform_identity.ticket_issuers.allowed_role_codes IS '允许进入的用户范围：持有其中任一启用角色；为空表示无人可进；不得含 super_admin';
COMMENT ON COLUMN platform_identity.ticket_issuers.created_at IS '创建时间';
COMMENT ON COLUMN platform_identity.ticket_issuers.updated_at IS '更新时间';

CREATE TABLE platform_identity.emergency_admins (
    user_id               bigint      PRIMARY KEY REFERENCES platform_identity.user_accounts (user_id),
    account_name            text        NOT NULL UNIQUE,
    password_hash         text        NOT NULL,
    totp_secret_encrypted text        NOT NULL,
    last_totp_step        bigint      NOT NULL DEFAULT 0,
    failed_attempts       integer     NOT NULL DEFAULT 0,
    locked_until          timestamptz,
    is_enabled            boolean     NOT NULL DEFAULT true,
    created_at            timestamptz NOT NULL DEFAULT now(),
    updated_at            timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE platform_identity.emergency_admins IS '应急管理员：独立凭据（Argon2id + TOTP），只能从内网登录；5 次失败锁 15 分钟（RULE-316/317）';
COMMENT ON COLUMN platform_identity.emergency_admins.user_id IS '用户 ID：对应的员工账号，会话与权限走该账号的角色';
COMMENT ON COLUMN platform_identity.emergency_admins.account_name IS '应急账号名';
COMMENT ON COLUMN platform_identity.emergency_admins.password_hash IS 'Argon2id 口令哈希';
COMMENT ON COLUMN platform_identity.emergency_admins.totp_secret_encrypted IS 'TOTP 密钥（AES-GCM 加密后的 Base64）';
COMMENT ON COLUMN platform_identity.emergency_admins.last_totp_step IS '最近一次接受的 TOTP 时间步，防止同一动态码重放';
COMMENT ON COLUMN platform_identity.emergency_admins.failed_attempts IS '连续失败次数；成功后清零';
COMMENT ON COLUMN platform_identity.emergency_admins.locked_until IS '锁定到该时间；为空表示未锁定';
COMMENT ON COLUMN platform_identity.emergency_admins.is_enabled IS '是否启用';
COMMENT ON COLUMN platform_identity.emergency_admins.created_at IS '创建时间';
COMMENT ON COLUMN platform_identity.emergency_admins.updated_at IS '更新时间';
