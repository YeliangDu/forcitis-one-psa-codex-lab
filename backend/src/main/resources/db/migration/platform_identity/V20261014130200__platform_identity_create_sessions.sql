CREATE TABLE platform_identity.sessions (
    primary_id            char(36)    NOT NULL,
    session_id            char(36)    NOT NULL,
    creation_time         bigint      NOT NULL,
    last_access_time      bigint      NOT NULL,
    max_inactive_interval integer     NOT NULL,
    expiry_time           bigint      NOT NULL,
    principal_name        varchar(100),
    created_at            timestamptz NOT NULL DEFAULT now(),
    updated_at            timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_sessions PRIMARY KEY (primary_id),
    CONSTRAINT uq_sessions_session UNIQUE (session_id)
);
CREATE INDEX ix_sessions_expiry ON platform_identity.sessions (expiry_time);
CREATE INDEX ix_sessions_principal ON platform_identity.sessions (principal_name);
COMMENT ON TABLE platform_identity.sessions IS '会话：服务端会话（Spring Session JDBC，表结构由 Flyway 建立，initialize-schema=never）；列名由框架固定';
COMMENT ON COLUMN platform_identity.sessions.primary_id IS '会话主键（内部）';
COMMENT ON COLUMN platform_identity.sessions.session_id IS '会话 ID（Cookie 值，登录时更换）';
COMMENT ON COLUMN platform_identity.sessions.creation_time IS '创建时间（毫秒）';
COMMENT ON COLUMN platform_identity.sessions.last_access_time IS '最近访问时间（毫秒）';
COMMENT ON COLUMN platform_identity.sessions.max_inactive_interval IS '空闲超时（秒）';
COMMENT ON COLUMN platform_identity.sessions.expiry_time IS '过期时间（毫秒）';
COMMENT ON COLUMN platform_identity.sessions.principal_name IS '主体名：用户 ID 的十进制字符串，用于吊销该用户全部会话';
COMMENT ON COLUMN platform_identity.sessions.created_at IS '创建时间';
COMMENT ON COLUMN platform_identity.sessions.updated_at IS '更新时间';

CREATE TABLE platform_identity.sessions_attributes (
    session_primary_id char(36)     NOT NULL,
    attribute_name     varchar(200) NOT NULL,
    attribute_bytes    bytea        NOT NULL,
    created_at         timestamptz  NOT NULL DEFAULT now(),
    updated_at         timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT pk_sessions_attributes PRIMARY KEY (session_primary_id, attribute_name),
    CONSTRAINT fk_sessions_attributes_session FOREIGN KEY (session_primary_id) REFERENCES platform_identity.sessions (primary_id) ON DELETE CASCADE
);
COMMENT ON TABLE platform_identity.sessions_attributes IS '会话属性（序列化的安全上下文等）';
COMMENT ON COLUMN platform_identity.sessions_attributes.session_primary_id IS '所属会话主键';
COMMENT ON COLUMN platform_identity.sessions_attributes.attribute_name IS '属性名';
COMMENT ON COLUMN platform_identity.sessions_attributes.attribute_bytes IS '属性值（序列化字节）';
COMMENT ON COLUMN platform_identity.sessions_attributes.created_at IS '创建时间';
COMMENT ON COLUMN platform_identity.sessions_attributes.updated_at IS '更新时间';
