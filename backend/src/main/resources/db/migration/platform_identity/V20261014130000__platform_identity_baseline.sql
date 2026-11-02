CREATE SCHEMA IF NOT EXISTS platform_identity;
COMMENT ON SCHEMA platform_identity IS '身份与权限（权限流程与集成平台 BC · 身份）：登录、会话、角色、导航';

-- 文件开头（CREATE SCHEMA 之后）：排他约束需要 btree_gist，由建库脚本预建，迁移只检查存在（ADR-0034 影响面、DDL-28）。
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'btree_gist') THEN
        RAISE EXCEPTION 'extension btree_gist is missing: the database setup script (one_db_roles_schemas.sql) creates it before the migrations run';
    END IF;
END $$;

CREATE TABLE platform_identity.user_accounts (
    user_id            uuid        PRIMARY KEY,
    employee_id        uuid        NOT NULL,
    status             text        NOT NULL DEFAULT 'ENABLED' CHECK (status IN ('ENABLED', 'DISABLED')),
    last_login_at      timestamptz,
    last_login_address text,
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),
    version            bigint      NOT NULL DEFAULT 0,
    event_count        bigint      NOT NULL DEFAULT 0,
    CONSTRAINT uk_user_accounts_employee_id UNIQUE (employee_id),
    CONSTRAINT ck_user_accounts_last_login CHECK (last_login_address IS NULL OR last_login_at IS NOT NULL),
    CONSTRAINT ck_user_accounts_last_login_address CHECK (char_length(last_login_address) BETWEEN 1 AND 64)
);
COMMENT ON TABLE platform_identity.user_accounts IS '用户账号：身份侧的启用开关与最近登录；员工姓名、部门、在职状态归组织人事模块（不在此冗余）。粒度：每个用户一行';
COMMENT ON COLUMN platform_identity.user_accounts.user_id IS '用户 ID（UUIDv7）：由身份模块分配，不等于员工 ID（ADR-0033 第 3 项第 1 条）';
COMMENT ON COLUMN platform_identity.user_accounts.employee_id IS '员工 ID：账号所属的员工，每个员工至多一个账号；跨 schema 引用组织人事的员工，不建外键';
COMMENT ON COLUMN platform_identity.user_accounts.status IS '账号状态：ENABLED / DISABLED；登录与每次解析会话时都与在职状态一起检查（RULE-326/338）';
COMMENT ON COLUMN platform_identity.user_accounts.last_login_at IS '最近一次登录成功时间（RULE-330）；为空表示从未成功登录';
COMMENT ON COLUMN platform_identity.user_accounts.last_login_address IS '最近一次登录成功的客户端地址（RULE-330）；为空表示从未成功登录，或已按保留期限清除（ADR-0043 U-12）；有值时 last_login_at 必有值，长度 1 至 64（不是地址的取值由应用置空并告警，不截断）';
COMMENT ON COLUMN platform_identity.user_accounts.created_at IS '创建时间';
COMMENT ON COLUMN platform_identity.user_accounts.updated_at IS '更新时间';
COMMENT ON COLUMN platform_identity.user_accounts.version IS '乐观锁版本';
COMMENT ON COLUMN platform_identity.user_accounts.event_count IS '事件序号：该聚合已发出的事件数，每发一个事件加 1，作为事件信封的 aggregateVersion；与乐观锁 version 分开';

CREATE TABLE platform_identity.roles (
    role_id           uuid        PRIMARY KEY,
    code              text        NOT NULL,
    kind              text        NOT NULL DEFAULT 'CUSTOM' CHECK (kind IN ('SYSTEM', 'CUSTOM')),
    is_administrative boolean     NOT NULL DEFAULT false,
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    version           bigint      NOT NULL DEFAULT 0,
    event_count       bigint      NOT NULL DEFAULT 0,
    CONSTRAINT ck_roles_reserved_code CHECK (lower(code) <> 'super_admin' OR kind = 'SYSTEM'),
    CONSTRAINT ck_roles_code CHECK (code ~ '^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$')
);
CREATE UNIQUE INDEX uq_roles_code ON platform_identity.roles (lower(code));
COMMENT ON TABLE platform_identity.roles IS '角色：只存不可变的身份（编码、类别、是否管理员角色）；名称、状态、范围类型与权限集合在角色版本（role_versions）；删除角色只关闭最后一个版本，角色行保留，编码不得复用（RULE-345 修订、ADR-0034）。粒度：每个角色一行';
COMMENT ON COLUMN platform_identity.roles.role_id IS '角色 ID（UUIDv7）';
COMMENT ON COLUMN platform_identity.roles.code IS '角色编码；super_admin 为预留码，只能由系统角色声明建立（RULE-340/345）';
COMMENT ON COLUMN platform_identity.roles.kind IS '角色类别：SYSTEM（冻结，不可改不可删）/ CUSTOM';
COMMENT ON COLUMN platform_identity.roles.is_administrative IS '是否管理员角色：外部签名票据不得让持有者进入（C1a-03）；写入后不可改';
COMMENT ON COLUMN platform_identity.roles.created_at IS '创建时间';
COMMENT ON COLUMN platform_identity.roles.updated_at IS '更新时间';
COMMENT ON COLUMN platform_identity.roles.version IS '乐观锁版本';
COMMENT ON COLUMN platform_identity.roles.event_count IS '事件序号：该聚合已发出的事件数，每发一个事件加 1，作为事件信封的 aggregateVersion；与乐观锁 version 分开';

CREATE FUNCTION platform_identity.refuse_role_identity_change() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
BEGIN
    IF NEW.role_id IS DISTINCT FROM OLD.role_id OR NEW.code IS DISTINCT FROM OLD.code OR NEW.kind IS DISTINCT FROM OLD.kind
       OR NEW.is_administrative IS DISTINCT FROM OLD.is_administrative THEN
        RAISE EXCEPTION 'a role keeps its id, code, kind and administrative marker: %', OLD.role_id USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END $$;
COMMENT ON FUNCTION platform_identity.refuse_role_identity_change() IS '角色的身份列不可改：编码、类别、是否管理员角色写入后不变（ADR-0034 第 1 项第 1 条）';
CREATE TRIGGER tg_roles_identity_immutable BEFORE UPDATE ON platform_identity.roles
    FOR EACH ROW EXECUTE FUNCTION platform_identity.refuse_role_identity_change();

CREATE TABLE platform_identity.navigation_items (
    item_id         uuid        PRIMARY KEY,
    parent_item_id  uuid        REFERENCES platform_identity.navigation_items (item_id),
    name            text        NOT NULL,
    route_path      text,
    permission_code text,
    sort_order      integer     NOT NULL DEFAULT 0,
    is_enabled      boolean     NOT NULL DEFAULT true,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_navigation_items_parent CHECK (parent_item_id IS NULL OR parent_item_id <> item_id),
    CONSTRAINT uk_navigation_items_parent_item_id_name UNIQUE NULLS NOT DISTINCT (parent_item_id, name)
);
COMMENT ON TABLE platform_identity.navigation_items IS '导航项：目录（无路由）与页面（有路由）；页面按所需权限码对持有者可见（RULE-348）。粒度：每个导航项一行';
COMMENT ON COLUMN platform_identity.navigation_items.item_id IS '导航项 ID（UUIDv7）';
COMMENT ON COLUMN platform_identity.navigation_items.parent_item_id IS '上级导航项 ID；顶层为空';
COMMENT ON COLUMN platform_identity.navigation_items.name IS '显示名称';
COMMENT ON COLUMN platform_identity.navigation_items.route_path IS '前端路由；目录为空';
COMMENT ON COLUMN platform_identity.navigation_items.permission_code IS '可见所需权限码；为空表示所有已登录用户可见';
COMMENT ON COLUMN platform_identity.navigation_items.sort_order IS '同级排序';
COMMENT ON COLUMN platform_identity.navigation_items.is_enabled IS '是否启用';
COMMENT ON COLUMN platform_identity.navigation_items.created_at IS '创建时间';
COMMENT ON COLUMN platform_identity.navigation_items.updated_at IS '更新时间';

CREATE TABLE platform_identity.login_states (
    login_state_id uuid      PRIMARY KEY,
    state_hash   text        NOT NULL,
    entry        text        NOT NULL CHECK (entry IN ('WORKBENCH', 'MINI_PROGRAM', 'QR_CODE_LOGIN')),
    expires_at   timestamptz NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),
    binding_hash text        NOT NULL,
    CONSTRAINT uk_login_states_state_hash UNIQUE (state_hash)
);
COMMENT ON TABLE platform_identity.login_states IS '企业微信 OAuth state：只存哈希；一次性，消费时原子删除（I-9）。粒度：每个尚未使用的 state 一行';
COMMENT ON COLUMN platform_identity.login_states.login_state_id IS '登录 state ID（UUIDv7）';
COMMENT ON COLUMN platform_identity.login_states.state_hash IS 'state 的 SHA-256 十六进制';
COMMENT ON COLUMN platform_identity.login_states.entry IS '签发时声明的企业微信登录入口';
COMMENT ON COLUMN platform_identity.login_states.expires_at IS '过期时间（签发后 10 分钟）';
COMMENT ON COLUMN platform_identity.login_states.created_at IS '创建时间';
COMMENT ON COLUMN platform_identity.login_states.updated_at IS '更新时间';
COMMENT ON COLUMN platform_identity.login_states.binding_hash IS '发起 state 的浏览器所持绑定值的 SHA-256 十六进制；消费 state 时必须出示同一绑定值，攻击者带自己的 state 诱导他人登录时对不上';

CREATE TABLE platform_identity.sso_ticket_uses (
    sso_ticket_use_id uuid    NOT NULL,
    ticket_source text        NOT NULL,
    ticket_hash   text        NOT NULL,
    expires_at    timestamptz NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_sso_ticket_uses PRIMARY KEY (sso_ticket_use_id),
    CONSTRAINT uk_sso_ticket_uses_ticket_source_ticket_hash UNIQUE (ticket_source, ticket_hash)
);
CREATE INDEX ix_sso_ticket_uses_expiry ON platform_identity.sso_ticket_uses (expires_at);
COMMENT ON TABLE platform_identity.sso_ticket_uses IS '已使用的一次性凭证：（凭证来源，摘要）的唯一约束保证并发重放只有一次插入成功（I-9，RULE-320）。粒度：每个已使用的一次性凭证一行';
COMMENT ON COLUMN platform_identity.sso_ticket_uses.sso_ticket_use_id IS '已使用凭证 ID（UUIDv7）';
COMMENT ON COLUMN platform_identity.sso_ticket_uses.ticket_source IS '凭证来源：SSO（门户票据），或签发方编码（签名票据的 nonce）';
COMMENT ON COLUMN platform_identity.sso_ticket_uses.ticket_hash IS '票据或 nonce 的 SHA-256 十六进制，不存明文';
COMMENT ON COLUMN platform_identity.sso_ticket_uses.expires_at IS '记录保留到该时间后清理（门户票据 7 天，签名票据略长于票据有效期）';
COMMENT ON COLUMN platform_identity.sso_ticket_uses.created_at IS '创建时间';
COMMENT ON COLUMN platform_identity.sso_ticket_uses.updated_at IS '更新时间';

CREATE TABLE platform_identity.ticket_issuers (
    ticket_issuer_id   uuid        PRIMARY KEY,
    issuer_code        text        NOT NULL,
    signing_key_code   text        NOT NULL UNIQUE,
    key_reference      text        NOT NULL,
    is_enabled         boolean     NOT NULL DEFAULT false,
    allowed_role_codes text[]      NOT NULL DEFAULT '{}',
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uk_ticket_issuers_issuer_code UNIQUE (issuer_code),
    CONSTRAINT ck_ticket_issuers_no_administrator CHECK (NOT ('super_admin' = ANY (allowed_role_codes)))
);
COMMENT ON TABLE platform_identity.ticket_issuers IS '票据签发方：外部签名票据的每方一把密钥（kid 区分），默认关闭；签发方必须在 architecture/ticket-issuers-allowlist.txt 中（I-2）。粒度：每个签发方一行';
COMMENT ON COLUMN platform_identity.ticket_issuers.ticket_issuer_id IS '票据签发方 ID（UUIDv7）';
COMMENT ON COLUMN platform_identity.ticket_issuers.issuer_code IS '签发方编码，如 km；老 PSA 后端永远不得出现（I-2）';
COMMENT ON COLUMN platform_identity.ticket_issuers.signing_key_code IS '签名密钥编码（票据头部的 kid），一方一把';
COMMENT ON COLUMN platform_identity.ticket_issuers.key_reference IS '密钥引用，如 env:NAME；密钥本身只在环境变量或 KMS，表中不存';
COMMENT ON COLUMN platform_identity.ticket_issuers.is_enabled IS '是否开启：调用方完成对接前保持关闭';
COMMENT ON COLUMN platform_identity.ticket_issuers.allowed_role_codes IS '允许进入的用户范围：持有其中任一启用角色；为空表示无人可进；不得含 super_admin';
COMMENT ON COLUMN platform_identity.ticket_issuers.created_at IS '创建时间';
COMMENT ON COLUMN platform_identity.ticket_issuers.updated_at IS '更新时间';

CREATE TABLE platform_identity.emergency_admins (
    user_id               uuid        PRIMARY KEY REFERENCES platform_identity.user_accounts (user_id),
    account_name            text        NOT NULL UNIQUE,
    password_hash         text        NOT NULL,
    totp_secret_encrypted text        NOT NULL,
    last_totp_step        bigint      NOT NULL DEFAULT 0,
    failed_attempts       integer     NOT NULL DEFAULT 0,
    locked_until          timestamptz,
    is_enabled            boolean     NOT NULL DEFAULT true,
    created_at            timestamptz NOT NULL DEFAULT now(),
    updated_at            timestamptz NOT NULL DEFAULT now(),
    version               bigint      NOT NULL DEFAULT 0,
    event_count           bigint      NOT NULL DEFAULT 0,
    CONSTRAINT ck_emergency_admins_account_name CHECK (account_name ~ '^[a-z][a-z0-9._-]{2,31}$'),
    CONSTRAINT ck_emergency_admins_failed_attempts CHECK (failed_attempts >= 0),
    CONSTRAINT ck_emergency_admins_last_totp_step CHECK (last_totp_step >= 0)
);
COMMENT ON TABLE platform_identity.emergency_admins IS '应急管理员：独立凭据（Argon2id + TOTP），只能从内网登录，5 次失败锁 15 分钟（RULE-316/317）；user_id 是绑定的超级管理员持有人，每人至多一个，登记至少两个（ADR-0027 第 1 项）。粒度：每个绑定的超级管理员持有人一个应急账号一行';
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
COMMENT ON COLUMN platform_identity.emergency_admins.version IS '乐观锁版本：启用状态与凭据的变更按它串行；失败计数、锁定、动态码步长是访问元数据，用条件原子更新，不动它';
COMMENT ON COLUMN platform_identity.emergency_admins.event_count IS '事件序号：该聚合已发出的事件数，每发一个事件加 1，作为事件信封的 aggregateVersion；与乐观锁 version 分开';

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
COMMENT ON COLUMN platform_identity.sessions.principal_name IS '主体名：用户 ID 的 uuid 文本（规范小写），用于吊销该用户全部会话';
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

-- C-M7：浏览器标识。单点登录或外部票据登录的会话，只有认得这个浏览器时才免于「是否本人」确认。
CREATE TABLE platform_identity.browser_identifiers (
    browser_identifier_id uuid        NOT NULL,
    user_id               uuid        NOT NULL,
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

-- 敏感权限的授予与含敏感权限的角色定义，须另一人确认后才生效（ADR-0026 第 6 项第 2 条，ADR-0027 第 3 项第 2 条；I-12）。
CREATE TABLE platform_identity.authorization_requests (
    request_id        uuid        NOT NULL,
    request_kind      text        NOT NULL CHECK (request_kind IN ('REPLACE_ROLES', 'CREATE_ROLE', 'UPDATE_ROLE')),
    requested_by      uuid        NOT NULL,
    requested_at      timestamptz NOT NULL,
    subject_user_id   uuid,
    role_id           uuid,
    role_codes        text[]      NOT NULL DEFAULT '{}',
    new_role_code     text,
    role_name         text,
    is_enabled        boolean,
    access_scope_kind text CHECK (access_scope_kind IN ('ALL', 'CUSTOM_ORG_UNITS', 'OWN_ORG_UNIT', 'OWN_ORG_UNIT_SUBTREE', 'SELF', 'SELF_AND_DIRECT_REPORTS')),
    permission_codes  text[]      NOT NULL DEFAULT '{}',
    base_account_version_number bigint,
    base_role_version_ids       uuid[]      NOT NULL DEFAULT '{}',
    request_status    text        NOT NULL,
    decided_by        uuid,
    decided_at        timestamptz,
    version           bigint      NOT NULL DEFAULT 0,
    event_count       bigint      NOT NULL DEFAULT 0,
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_authorization_requests PRIMARY KEY (request_id),
    CONSTRAINT ck_authorization_requests_request_status CHECK (request_status IN ('PENDING', 'CONFIRMED', 'REJECTED', 'VOIDED')),
    CONSTRAINT ck_authorization_requests_base CHECK ((request_kind = 'REPLACE_ROLES') = (base_account_version_number IS NOT NULL)
        AND (request_kind <> 'UPDATE_ROLE' OR cardinality(base_role_version_ids) = 1) AND (request_kind <> 'CREATE_ROLE' OR cardinality(base_role_version_ids) = 0)),
    CONSTRAINT ck_authorization_requests_two_people CHECK (decided_by IS NULL OR (decided_by <> requested_by AND decided_by IS DISTINCT FROM subject_user_id) OR request_status = 'REJECTED')
);
COMMENT ON TABLE platform_identity.authorization_requests IS '授权申请：授予敏感权限、或把角色改成含敏感权限，先建申请，另一人确认后由监听器执行。粒度：每个申请一行';
COMMENT ON COLUMN platform_identity.authorization_requests.request_id IS '申请 ID（UUIDv7）';
COMMENT ON COLUMN platform_identity.authorization_requests.request_kind IS '申请类型：REPLACE_ROLES 替换账号的角色集合 / CREATE_ROLE 新建角色 / UPDATE_ROLE 修改角色定义';
COMMENT ON COLUMN platform_identity.authorization_requests.requested_by IS '申请人用户 ID';
COMMENT ON COLUMN platform_identity.authorization_requests.requested_at IS '申请时间';
COMMENT ON COLUMN platform_identity.authorization_requests.subject_user_id IS '受益人用户 ID：REPLACE_ROLES 才有；受益人不能确认';
COMMENT ON COLUMN platform_identity.authorization_requests.role_id IS '角色 ID：被修改的角色，UPDATE_ROLE 才有';
COMMENT ON COLUMN platform_identity.authorization_requests.role_codes IS '申请后账号的完整角色编码集合：REPLACE_ROLES 才有';
COMMENT ON COLUMN platform_identity.authorization_requests.new_role_code IS '新角色编码：CREATE_ROLE 才有';
COMMENT ON COLUMN platform_identity.authorization_requests.role_name IS '角色名称：CREATE_ROLE、UPDATE_ROLE 才有';
COMMENT ON COLUMN platform_identity.authorization_requests.is_enabled IS '角色是否启用：CREATE_ROLE、UPDATE_ROLE 才有';
COMMENT ON COLUMN platform_identity.authorization_requests.access_scope_kind IS '角色数据范围类型；为空表示沿用';
COMMENT ON COLUMN platform_identity.authorization_requests.permission_codes IS '角色的完整权限码集合：CREATE_ROLE、UPDATE_ROLE 才有';
COMMENT ON COLUMN platform_identity.authorization_requests.base_account_version_number IS '基准账号版本：账号授权申请记申请时 user_accounts.version；为空表示不是账号授权申请';
COMMENT ON COLUMN platform_identity.authorization_requests.base_role_version_ids IS '基准角色版本：申请时相关角色的当前 role_version_id；账号授权申请为申请后集合与当时持有集合中每个角色，角色修改申请为该角色一个；新建角色为空数组（列名见待裁定 A-3）';
COMMENT ON COLUMN platform_identity.authorization_requests.request_status IS '申请状态：PENDING 待确认 / CONFIRMED 已确认并交监听器执行 / REJECTED 已拒绝 / VOIDED 已作废：确认后执行前发现基准（账号版本或相关角色版本）已变，什么都没有执行';
COMMENT ON COLUMN platform_identity.authorization_requests.decided_by IS '确认人或拒绝人用户 ID；确认人不是申请人，也不是受益人';
COMMENT ON COLUMN platform_identity.authorization_requests.decided_at IS '确认或拒绝的时间';
COMMENT ON COLUMN platform_identity.authorization_requests.version IS '乐观锁版本';
COMMENT ON COLUMN platform_identity.authorization_requests.event_count IS '事件序号：该聚合已发出的事件数，作为事件信封的 aggregateVersion；与乐观锁 version 分开';
COMMENT ON COLUMN platform_identity.authorization_requests.created_at IS '创建时间';
COMMENT ON COLUMN platform_identity.authorization_requests.updated_at IS '更新时间';

CREATE FUNCTION platform_identity.is_normalized_code_set(codes text[]) RETURNS boolean LANGUAGE sql IMMUTABLE STRICT SET search_path = pg_catalog, pg_temp AS $$
    SELECT (array_ndims(codes) IS NULL OR array_ndims(codes) = 1)
       AND array_position(codes, NULL) IS NULL
       AND codes = ARRAY(SELECT c FROM (SELECT DISTINCT c FROM unnest(codes) AS c) AS d ORDER BY c COLLATE "C")
$$;
COMMENT ON FUNCTION platform_identity.is_normalized_code_set(text[]) IS '代码集合是否已规范化：一维、无空元素、无重复、按 C 排序升序；应用写入前先规范化（ADR-0034 第 1 项第 2 条）';

-- 两张授权历史表共用：开放行只能被关闭一次（只改 TG_ARGV 所列的关闭列），已关闭的行不能改，任何行不能删或清空。
CREATE FUNCTION platform_identity.refuse_history_rewrite() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
BEGIN
    IF TG_OP IN ('DELETE', 'TRUNCATE') THEN
        RAISE EXCEPTION 'authorization history is append-only: % on %.% is refused', lower(TG_OP), TG_TABLE_SCHEMA, TG_TABLE_NAME
            USING ERRCODE = 'restrict_violation';
    END IF;
    IF OLD.effective_to_at IS NOT NULL THEN
        RAISE EXCEPTION 'a closed row cannot change: %.%', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
    END IF;
    IF NEW.effective_to_at IS NULL OR (to_jsonb(NEW) - TG_ARGV) <> (to_jsonb(OLD) - TG_ARGV) THEN
        RAISE EXCEPTION 'an open row can only be closed: %.%', TG_TABLE_SCHEMA, TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END $$;
COMMENT ON FUNCTION platform_identity.refuse_history_rewrite() IS '授权历史只追加：开放行只许一次性写入关闭列（参数列出），已关闭的行不能改，任何行不能删或清空（ADR-0034 第 1 项第 2 条、第 2 项第 2 条）';

CREATE TABLE platform_identity.role_versions (
    role_version_id             uuid        NOT NULL,
    role_id                     uuid        NOT NULL,
    version_number              integer     NOT NULL,
    effective_from_at           timestamptz NOT NULL,
    effective_to_at             timestamptz,
    role_name                   text        NOT NULL,
    role_status                 text        NOT NULL,
    access_scope_kind           text        NOT NULL,
    permission_codes            text[]      NOT NULL,
    change_channel_kind         text        NOT NULL,
    recorded_by                 uuid,
    change_reason               text        NOT NULL,
    authorization_request_id    uuid,
    declaration_revision_number integer,
    version_audit_entry_id      uuid        NOT NULL,
    closed_by                   uuid,
    closure_reason              text,
    closure_audit_entry_id      uuid,
    created_at                  timestamptz NOT NULL,
    updated_at                  timestamptz NOT NULL,
    CONSTRAINT pk_role_versions PRIMARY KEY (role_version_id),
    CONSTRAINT fk_role_versions_role_id FOREIGN KEY (role_id) REFERENCES platform_identity.roles (role_id) ON DELETE RESTRICT,
    CONSTRAINT fk_role_versions_authorization_request_id FOREIGN KEY (authorization_request_id)
        REFERENCES platform_identity.authorization_requests (request_id) ON DELETE RESTRICT,
    CONSTRAINT fk_role_versions_recorded_by FOREIGN KEY (recorded_by) REFERENCES platform_identity.user_accounts (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_role_versions_closed_by FOREIGN KEY (closed_by) REFERENCES platform_identity.user_accounts (user_id) ON DELETE RESTRICT,
    CONSTRAINT uk_role_versions_role_id_version_number UNIQUE (role_id, version_number),
    CONSTRAINT ck_role_versions_version_number CHECK (version_number >= 1),
    CONSTRAINT ck_role_versions_role_name CHECK (btrim(role_name) <> '' AND char_length(role_name) <= 100),
    CONSTRAINT ck_role_versions_role_status CHECK (role_status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_role_versions_access_scope_kind CHECK (access_scope_kind IN ('ALL', 'CUSTOM_ORG_UNITS', 'OWN_ORG_UNIT', 'OWN_ORG_UNIT_SUBTREE', 'SELF', 'SELF_AND_DIRECT_REPORTS')),
    CONSTRAINT ck_role_versions_permission_codes CHECK (platform_identity.is_normalized_code_set(permission_codes)),
    CONSTRAINT ck_role_versions_change_channel_kind CHECK (change_channel_kind IN ('SEED', 'MANUAL', 'MIGRATION')),
    CONSTRAINT ck_role_versions_change_reason CHECK (btrim(change_reason) <> '' AND char_length(change_reason) <= 500),
    CONSTRAINT ck_role_versions_seed CHECK ((change_channel_kind = 'SEED') = (declaration_revision_number IS NOT NULL)
        AND (change_channel_kind <> 'SEED' OR recorded_by IS NULL)),
    CONSTRAINT ck_role_versions_manual CHECK (change_channel_kind <> 'MANUAL' OR recorded_by IS NOT NULL),
    CONSTRAINT ck_role_versions_declaration_revision_number CHECK (declaration_revision_number IS NULL OR declaration_revision_number >= 1),
    CONSTRAINT ck_role_versions_closure CHECK ((closed_by IS NULL) = (closure_reason IS NULL)
        AND (closure_reason IS NULL) = (closure_audit_entry_id IS NULL)
        AND (closure_reason IS NULL OR effective_to_at IS NOT NULL)),
    CONSTRAINT ck_role_versions_closure_reason CHECK (closure_reason IS NULL OR (btrim(closure_reason) <> '' AND char_length(closure_reason) <= 500)),
    CONSTRAINT ck_role_versions_period CHECK (effective_to_at IS NULL OR effective_to_at >= effective_from_at),
    CONSTRAINT ck_role_versions_finite CHECK (isfinite(effective_from_at) AND (effective_to_at IS NULL OR isfinite(effective_to_at))),
    CONSTRAINT ex_role_versions_role_id_period EXCLUDE USING gist (role_id WITH =, tstzrange(effective_from_at, effective_to_at, '[)') WITH &&),
    CONSTRAINT ex_role_versions_role_name_period EXCLUDE USING gist (lower(role_name) WITH =, tstzrange(effective_from_at, effective_to_at, '[)') WITH &&)
);
CREATE INDEX ix_role_versions_role_id_current ON platform_identity.role_versions (role_id) WHERE effective_to_at IS NULL;
CREATE INDEX ix_role_versions_authorization_request_id ON platform_identity.role_versions (authorization_request_id) WHERE authorization_request_id IS NOT NULL;
CREATE INDEX ix_role_versions_recorded_by ON platform_identity.role_versions (recorded_by) WHERE recorded_by IS NOT NULL;
CREATE INDEX ix_role_versions_closed_by ON platform_identity.role_versions (closed_by) WHERE closed_by IS NOT NULL;
CREATE TRIGGER tg_role_versions_append_only BEFORE UPDATE OR DELETE ON platform_identity.role_versions
    FOR EACH ROW EXECUTE FUNCTION platform_identity.refuse_history_rewrite('effective_to_at', 'closed_by', 'closure_reason', 'closure_audit_entry_id', 'updated_at');
CREATE TRIGGER tg_role_versions_append_only_truncate BEFORE TRUNCATE ON platform_identity.role_versions
    FOR EACH STATEMENT EXECUTE FUNCTION platform_identity.refuse_history_rewrite();
COMMENT ON TABLE platform_identity.role_versions IS '角色版本：角色定义（名称、状态、范围类型、完整权限集合）的每次变化一行，只追加；当前版本 = effective_to_at 为空的那一行，每个角色至多一个；删除角色 = 关闭最后一个版本、不再开新版本（ADR-0034 第 1 项、RULE-345 修订）。粒度：每个角色的每个定义版本一行';
COMMENT ON COLUMN platform_identity.role_versions.role_version_id IS '角色版本 ID（UUIDv7，应用生成）';
COMMENT ON COLUMN platform_identity.role_versions.role_id IS '角色 ID';
COMMENT ON COLUMN platform_identity.role_versions.version_number IS '版本序号：同一角色从 1 起递增';
COMMENT ON COLUMN platform_identity.role_versions.effective_from_at IS '生效时刻（含）：greatest(注入时钟, 当前版本生效时刻, 该角色已有版本的最大失效时刻)';
COMMENT ON COLUMN platform_identity.role_versions.effective_to_at IS '失效时刻（不含）；为空表示当前版本。与生效时刻相等为零长区间，不出现在任何时刻的还原中';
COMMENT ON COLUMN platform_identity.role_versions.role_name IS '角色名称：同一时刻内不区分大小写唯一（排他约束），1 至 100 字';
COMMENT ON COLUMN platform_identity.role_versions.role_status IS '角色状态：ENABLED / DISABLED；只有 ENABLED 计入权限与数据范围（RULE-343/349）';
COMMENT ON COLUMN platform_identity.role_versions.access_scope_kind IS '数据范围类型：新角色默认 SELF（RULE-345）；CUSTOM_ORG_UNITS 的部门明细挂在本版本下（F-1）';
COMMENT ON COLUMN platform_identity.role_versions.permission_codes IS '完整权限集合：权限码 {子域}.{BC}:{资源}:{动作}，规范化存储（无空、无重复、C 排序升序）；按原文本保存，不随权限目录改写';
COMMENT ON COLUMN platform_identity.role_versions.change_channel_kind IS '变更渠道：SEED 系统角色声明对账 / MANUAL 角色管理界面 / MIGRATION 老角色迁入';
COMMENT ON COLUMN platform_identity.role_versions.recorded_by IS '记录人用户 ID；为空表示系统写入（SEED 必为空，MANUAL 必有）';
COMMENT ON COLUMN platform_identity.role_versions.change_reason IS '变更理由：SEED 写声明出处，MANUAL 写操作说明，1 至 500 字（来源见 ADR-0034-IMPL 待裁定 A-5）';
COMMENT ON COLUMN platform_identity.role_versions.authorization_request_id IS '授权申请 ID：经双人确认的版本必填；为空表示未经授权申请';
COMMENT ON COLUMN platform_identity.role_versions.declaration_revision_number IS '声明修订号：只有 SEED 版本有，单调递增；为空表示不是声明写入';
COMMENT ON COLUMN platform_identity.role_versions.version_audit_entry_id IS '写入本版本的同一事务中写下的审计条目 ID（跨 schema，只存 ID、不建外键）';
COMMENT ON COLUMN platform_identity.role_versions.closed_by IS '关闭人用户 ID：删除角色关闭最后版本时写入；为空表示未关闭，或被新版本接替（由接替版本记操作人）';
COMMENT ON COLUMN platform_identity.role_versions.closure_reason IS '关闭理由：删除角色时写入，1 至 500 字；为空表示未关闭或被新版本接替';
COMMENT ON COLUMN platform_identity.role_versions.closure_audit_entry_id IS '关闭（删除角色）的审计条目 ID（跨 schema）；为空表示未关闭或被新版本接替';
COMMENT ON COLUMN platform_identity.role_versions.created_at IS '创建时间（应用注入时钟）';
COMMENT ON COLUMN platform_identity.role_versions.updated_at IS '更新时间（应用注入时钟）：关闭时写入关闭时刻（见待裁定 A-4）';

CREATE TABLE platform_identity.account_role_grants (
    account_role_grant_id               uuid        NOT NULL,
    user_id                             uuid        NOT NULL,
    role_id                             uuid        NOT NULL,
    effective_from_at                   timestamptz NOT NULL,
    effective_to_at                     timestamptz,
    granted_by                          uuid,
    grant_channel_kind                  text        NOT NULL,
    grant_authorization_request_id      uuid,
    migration_batch_id                  uuid,
    grant_audit_entry_id                uuid        NOT NULL,
    revoked_by                          uuid,
    revocation_cause_kind               text,
    revocation_authorization_request_id uuid,
    revocation_separation_command_id    uuid,
    revocation_audit_entry_id           uuid,
    created_at                          timestamptz NOT NULL,
    updated_at                          timestamptz NOT NULL,
    CONSTRAINT pk_account_role_grants PRIMARY KEY (account_role_grant_id),
    CONSTRAINT fk_account_role_grants_user_id FOREIGN KEY (user_id) REFERENCES platform_identity.user_accounts (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_account_role_grants_role_id FOREIGN KEY (role_id) REFERENCES platform_identity.roles (role_id) ON DELETE RESTRICT,
    CONSTRAINT fk_account_role_grants_granted_by FOREIGN KEY (granted_by) REFERENCES platform_identity.user_accounts (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_account_role_grants_revoked_by FOREIGN KEY (revoked_by) REFERENCES platform_identity.user_accounts (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_account_role_grants_grant_authorization_request_id FOREIGN KEY (grant_authorization_request_id)
        REFERENCES platform_identity.authorization_requests (request_id) ON DELETE RESTRICT,
    CONSTRAINT fk_account_role_grants_revocation_authorization_request_id FOREIGN KEY (revocation_authorization_request_id)
        REFERENCES platform_identity.authorization_requests (request_id) ON DELETE RESTRICT,
    CONSTRAINT ck_account_role_grants_grant_channel_kind CHECK (grant_channel_kind IN ('MANUAL', 'DEFAULT_ROLE', 'OPERATIONS', 'MIGRATION')),
    CONSTRAINT ck_account_role_grants_revocation_cause_kind CHECK (revocation_cause_kind IS NULL
        OR revocation_cause_kind IN ('MANUAL', 'SEPARATION', 'OPERATIONS', 'MIGRATION_ROLLBACK')),
    CONSTRAINT ck_account_role_grants_grant CHECK ((grant_channel_kind = 'MANUAL') = (granted_by IS NOT NULL)
        AND (grant_channel_kind = 'MIGRATION') = (migration_batch_id IS NOT NULL)
        AND (grant_authorization_request_id IS NULL OR grant_channel_kind = 'MANUAL')),
    CONSTRAINT ck_account_role_grants_revocation CHECK ((effective_to_at IS NULL) = (revocation_cause_kind IS NULL)
        AND (revocation_cause_kind IS NULL) = (revocation_audit_entry_id IS NULL)
        AND (revocation_cause_kind IS DISTINCT FROM 'MANUAL' OR revoked_by IS NOT NULL)
        AND ((revocation_cause_kind IS NOT DISTINCT FROM 'SEPARATION') = (revocation_separation_command_id IS NOT NULL))
        AND (revocation_cause_kind IS DISTINCT FROM 'SEPARATION' OR revoked_by IS NULL)
        AND (revocation_authorization_request_id IS NULL OR revocation_cause_kind = 'MANUAL')
        AND (revoked_by IS NULL OR effective_to_at IS NOT NULL)),
    CONSTRAINT ck_account_role_grants_period CHECK (effective_to_at IS NULL OR effective_to_at >= effective_from_at),
    CONSTRAINT ck_account_role_grants_finite CHECK (isfinite(effective_from_at) AND (effective_to_at IS NULL OR isfinite(effective_to_at))),
    CONSTRAINT ex_account_role_grants_user_id_role_id_period EXCLUDE USING gist
        (user_id WITH =, role_id WITH =, tstzrange(effective_from_at, effective_to_at, '[)') WITH &&)
);
CREATE INDEX ix_account_role_grants_user_id_current ON platform_identity.account_role_grants (user_id) WHERE effective_to_at IS NULL;
CREATE INDEX ix_account_role_grants_role_id_current ON platform_identity.account_role_grants (role_id) WHERE effective_to_at IS NULL;
CREATE INDEX ix_account_role_grants_user_id_effective_from_at ON platform_identity.account_role_grants (user_id, effective_from_at);
CREATE INDEX ix_account_role_grants_migration_batch_id ON platform_identity.account_role_grants (migration_batch_id) WHERE migration_batch_id IS NOT NULL;
CREATE INDEX ix_account_role_grants_granted_by ON platform_identity.account_role_grants (granted_by) WHERE granted_by IS NOT NULL;
CREATE INDEX ix_account_role_grants_revoked_by ON platform_identity.account_role_grants (revoked_by) WHERE revoked_by IS NOT NULL;
CREATE INDEX ix_account_role_grants_grant_authorization_request_id ON platform_identity.account_role_grants (grant_authorization_request_id)
    WHERE grant_authorization_request_id IS NOT NULL;
CREATE INDEX ix_account_role_grants_revocation_authorization_request_id ON platform_identity.account_role_grants (revocation_authorization_request_id)
    WHERE revocation_authorization_request_id IS NOT NULL;

-- 已处理的人事命令：消费人事事件的监听器记下「这条命令已判定过」，重投时不再按当时状态重新判定（#939，数据设计签发 #939 修订）。只追加。
CREATE TABLE platform_identity.handled_personnel_commands (
    personnel_command_id  uuid        NOT NULL,
    user_id               uuid        NOT NULL,
    command_kind          text        NOT NULL,
    outcome_kind          text        NOT NULL,
    account_role_grant_id uuid,
    handled_at            timestamptz NOT NULL,
    created_at            timestamptz NOT NULL DEFAULT now(),
    updated_at            timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_handled_personnel_commands PRIMARY KEY (personnel_command_id),
    CONSTRAINT fk_handled_personnel_commands_user_id FOREIGN KEY (user_id) REFERENCES platform_identity.user_accounts (user_id),
    CONSTRAINT ck_handled_personnel_commands_command_kind CHECK (command_kind IN ('REINSTATE')),
    CONSTRAINT ck_handled_personnel_commands_outcome_kind CHECK (outcome_kind IN ('GRANT_OPENED', 'NOT_EMPLOYED', 'ALREADY_GRANTED')),
    CONSTRAINT ck_handled_personnel_commands_grant CHECK ((outcome_kind = 'GRANT_OPENED') = (account_role_grant_id IS NOT NULL))
);
CREATE INDEX ix_handled_personnel_commands_user_id ON platform_identity.handled_personnel_commands (user_id);
CREATE INDEX ix_handled_personnel_commands_account_role_grant_id ON platform_identity.handled_personnel_commands (account_role_grant_id) WHERE account_role_grant_id IS NOT NULL;
COMMENT ON TABLE platform_identity.handled_personnel_commands IS '已处理的人事命令：监听器对一条人事命令判定后记一行（无论开了授予与否），重投时见到该行就跳过，不再按当时状态重新判定；只追加。粒度：每条已判定的人事命令一行';
COMMENT ON COLUMN platform_identity.handled_personnel_commands.personnel_command_id IS '人事命令 ID（跨模块，只存 ID、不建外键）';
COMMENT ON COLUMN platform_identity.handled_personnel_commands.user_id IS '用户 ID：命令所涉员工的账号';
COMMENT ON COLUMN platform_identity.handled_personnel_commands.command_kind IS '命令类别：REINSTATE 复职；将来别的人事命令增加时扩展本检查';
COMMENT ON COLUMN platform_identity.handled_personnel_commands.outcome_kind IS '判定结果：GRANT_OPENED 开了基础角色授予 / NOT_EMPLOYED 目录显示不在职、没开 / ALREADY_GRANTED 账号已有开放授予、没开';
COMMENT ON COLUMN platform_identity.handled_personnel_commands.account_role_grant_id IS '角色授予 ID：本命令开出的授予；只在 GRANT_OPENED 时有';
COMMENT ON COLUMN platform_identity.handled_personnel_commands.handled_at IS '判定时刻（注入时钟）';
COMMENT ON COLUMN platform_identity.handled_personnel_commands.created_at IS '创建时间';
COMMENT ON COLUMN platform_identity.handled_personnel_commands.updated_at IS '更新时间（行只追加，等于创建时间；命名标准要求每表有）';

CREATE FUNCTION platform_identity.require_open_role_version() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM platform_identity.role_versions v WHERE v.role_id = NEW.role_id AND v.effective_to_at IS NULL) THEN
        RAISE EXCEPTION 'role % has no open version (deleted) and cannot be granted', NEW.role_id USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END $$;
COMMENT ON FUNCTION platform_identity.require_open_role_version() IS '新开授予前检查角色仍有当前版本：已删除的角色不能再授予（ADR-0034 第 2 项第 1 条）';
CREATE TRIGGER tg_account_role_grants_open_role_version BEFORE INSERT ON platform_identity.account_role_grants
    FOR EACH ROW EXECUTE FUNCTION platform_identity.require_open_role_version();
CREATE TRIGGER tg_account_role_grants_append_only BEFORE UPDATE OR DELETE ON platform_identity.account_role_grants
    FOR EACH ROW EXECUTE FUNCTION platform_identity.refuse_history_rewrite('effective_to_at', 'revoked_by', 'revocation_cause_kind',
        'revocation_authorization_request_id', 'revocation_separation_command_id', 'revocation_audit_entry_id', 'updated_at');
CREATE TRIGGER tg_account_role_grants_append_only_truncate BEFORE TRUNCATE ON platform_identity.account_role_grants
    FOR EACH STATEMENT EXECUTE FUNCTION platform_identity.refuse_history_rewrite();
COMMENT ON TABLE platform_identity.account_role_grants IS '角色授予：某账号在一段时间内持有某角色，只追加；收回 = 关闭开放行，再次获得新增一行；当前有效授予 = effective_to_at 为空；离职关闭全部当前有效授予（ADR-0034 第 2、3 项）。粒度：每个账号每次持有某角色一行';
COMMENT ON COLUMN platform_identity.account_role_grants.account_role_grant_id IS '角色授予 ID（UUIDv7，应用生成）';
COMMENT ON COLUMN platform_identity.account_role_grants.user_id IS '用户 ID：持有人账号';
COMMENT ON COLUMN platform_identity.account_role_grants.role_id IS '角色 ID';
COMMENT ON COLUMN platform_identity.account_role_grants.effective_from_at IS '生效时刻（含）：greatest(注入时钟, 此人此角色已有行的最大失效时刻)';
COMMENT ON COLUMN platform_identity.account_role_grants.effective_to_at IS '失效时刻（不含，即收回时刻）：greatest(注入时钟, 生效时刻)；为空表示当前有效。是系统实际收回的时刻，不是离职日期';
COMMENT ON COLUMN platform_identity.account_role_grants.granted_by IS '授予人用户 ID：有且只有 MANUAL 有；为空表示非界面授予（默认角色、运维命令、迁入；运维命令的操作人与见证人记在审计）。将来增加授予渠道时同步改 ck_account_role_grants_grant';
COMMENT ON COLUMN platform_identity.account_role_grants.grant_channel_kind IS '授予渠道：MANUAL 账号授权界面（含经双人确认的申请）/ DEFAULT_ROLE 建档或复职时的默认角色 / OPERATIONS 运维命令 grant-role / MIGRATION 老角色迁入';
COMMENT ON COLUMN platform_identity.account_role_grants.grant_authorization_request_id IS '授予所据的授权申请 ID；为空表示未经双人确认（只 MANUAL 可有）';
COMMENT ON COLUMN platform_identity.account_role_grants.migration_batch_id IS '迁入批次 ID（跨模块，只存 ID）：只 MIGRATION 有；为空表示不是迁入';
COMMENT ON COLUMN platform_identity.account_role_grants.grant_audit_entry_id IS '开启本行的同一事务中写下的审计条目 ID（跨 schema，只存 ID、不建外键）';
COMMENT ON COLUMN platform_identity.account_role_grants.revoked_by IS '收回人用户 ID：MANUAL 必有、SEPARATION 必为空；为空表示未收回或系统收回';
COMMENT ON COLUMN platform_identity.account_role_grants.revocation_cause_kind IS '收回原因：MANUAL 账号授权 / SEPARATION 离职 / OPERATIONS 运维命令 revoke-role / MIGRATION_ROLLBACK 迁入批次回退；为空表示未收回';
COMMENT ON COLUMN platform_identity.account_role_grants.revocation_authorization_request_id IS '收回所据的授权申请 ID；为空表示未收回或未经双人确认（只 MANUAL 可有）';
COMMENT ON COLUMN platform_identity.account_role_grants.revocation_separation_command_id IS '触发离职收回的离职命令 ID（跨 schema，只存 ID）：只 SEPARATION 有；为空表示不是离职收回';
COMMENT ON COLUMN platform_identity.account_role_grants.revocation_audit_entry_id IS '收回本行的审计条目 ID（platform.identity.account.role.revoked 等，跨 schema）；为空表示未收回';
COMMENT ON COLUMN platform_identity.account_role_grants.created_at IS '创建时间（应用注入时钟）';
COMMENT ON COLUMN platform_identity.account_role_grants.updated_at IS '更新时间（应用注入时钟）：关闭时写入关闭时刻（见待裁定 A-4）';

-- 自定义部门明细：挂在角色版本下（ADR-0034 第 5 项）；改明细即新增角色版本，行写入后不可改（F-1 卡 7.1）。
CREATE TABLE platform_identity.role_version_custom_org_units (
    role_version_custom_org_unit_id uuid        NOT NULL,
    role_version_id                 uuid        NOT NULL,
    org_unit_id                     uuid        NOT NULL,
    is_descendants_included         boolean     NOT NULL,
    created_at                      timestamptz NOT NULL,
    updated_at                      timestamptz NOT NULL,
    CONSTRAINT pk_role_version_custom_org_units PRIMARY KEY (role_version_custom_org_unit_id),
    CONSTRAINT fk_role_version_custom_org_units_role_version_id FOREIGN KEY (role_version_id)
        REFERENCES platform_identity.role_versions (role_version_id) ON DELETE RESTRICT,
    CONSTRAINT uk_role_version_custom_org_units_role_version_id_org_unit_id UNIQUE (role_version_id, org_unit_id)
);
CREATE INDEX ix_role_version_custom_org_units_org_unit_id ON platform_identity.role_version_custom_org_units (org_unit_id);
COMMENT ON TABLE platform_identity.role_version_custom_org_units IS '角色版本自定义部门：范围类型为 CUSTOM_ORG_UNITS 的角色版本所列部门，每个部门选含下级或仅本部门；随新版本一次写入，不可改不可删，改明细即新增角色版本（ADR-0034 第 5 项）；部门引用组织人事的组织单元，不建外键，部门停用或撤销由范围复核兜底（RULE-387）。粒度：每个角色版本的每个部门一行';
COMMENT ON COLUMN platform_identity.role_version_custom_org_units.role_version_custom_org_unit_id IS '角色版本自定义部门 ID（UUIDv7）';
COMMENT ON COLUMN platform_identity.role_version_custom_org_units.role_version_id IS '角色版本 ID：只能是范围类型为 CUSTOM_ORG_UNITS 且仍开放的版本（触发器检查）';
COMMENT ON COLUMN platform_identity.role_version_custom_org_units.org_unit_id IS '组织单元 ID：跨 schema 引用组织人事的组织单元，不建外键；范围按读取时的组织快照展开，不存展开结果';
COMMENT ON COLUMN platform_identity.role_version_custom_org_units.is_descendants_included IS '是否含下级：真为该部门连同全部下级（组织同步新增的下级自动纳入），假为仅本部门；双人逐人级角色只能为假（ADR-0044 第 1 项，应用校验）';
COMMENT ON COLUMN platform_identity.role_version_custom_org_units.created_at IS '创建时间（应用注入时钟）';
COMMENT ON COLUMN platform_identity.role_version_custom_org_units.updated_at IS '更新时间（行不可改，等于创建时间）';

-- 授权申请中申请的自定义部门：只为双人逐人级角色开申请，这类角色只许仅本部门（ADR-0044 第 1 项），故不设含下级列。
CREATE TABLE platform_identity.authorization_request_custom_org_units (
    authorization_request_custom_org_unit_id uuid        NOT NULL,
    request_id                               uuid        NOT NULL,
    org_unit_id                              uuid        NOT NULL,
    created_at                               timestamptz NOT NULL,
    updated_at                               timestamptz NOT NULL,
    CONSTRAINT pk_authorization_request_custom_org_units PRIMARY KEY (authorization_request_custom_org_unit_id),
    CONSTRAINT fk_authorization_request_custom_org_units_request_id FOREIGN KEY (request_id)
        REFERENCES platform_identity.authorization_requests (request_id) ON DELETE RESTRICT,
    CONSTRAINT uk_authorization_request_custom_org_units_request_org_unit UNIQUE (request_id, org_unit_id)
);
COMMENT ON TABLE platform_identity.authorization_request_custom_org_units IS '授权申请自定义部门：新建或修改角色的申请中、范围类型为 CUSTOM_ORG_UNITS 时申请的部门，均为仅本部门；随申请提交一次写入，不可改不可删；确认时重新校验。粒度：每个申请的每个部门一行';
COMMENT ON COLUMN platform_identity.authorization_request_custom_org_units.authorization_request_custom_org_unit_id IS '授权申请自定义部门 ID（UUIDv7）';
COMMENT ON COLUMN platform_identity.authorization_request_custom_org_units.request_id IS '申请 ID：只能是待确认的 CREATE_ROLE 或 UPDATE_ROLE 申请（触发器检查）';
COMMENT ON COLUMN platform_identity.authorization_request_custom_org_units.org_unit_id IS '组织单元 ID：跨 schema 引用，不建外键；仅本部门';
COMMENT ON COLUMN platform_identity.authorization_request_custom_org_units.created_at IS '创建时间（应用注入时钟）';
COMMENT ON COLUMN platform_identity.authorization_request_custom_org_units.updated_at IS '更新时间（行不可改，等于创建时间）';

-- 两张子表只追加；明细只能写在开放的 CUSTOM_ORG_UNITS 版本上，申请子行只能写在待确认的角色申请上。
CREATE FUNCTION platform_identity.guard_custom_org_unit_rows() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
BEGIN
    IF TG_OP <> 'INSERT' THEN
        RAISE EXCEPTION 'custom org unit rows are written once: % on %.% is refused', lower(TG_OP), TG_TABLE_SCHEMA, TG_TABLE_NAME
            USING ERRCODE = 'restrict_violation';
    END IF;
    -- 分表嵌套判断：NEW 的字段在两张表上不同，不能放进同一个条件表达式。
    IF TG_TABLE_NAME = 'role_version_custom_org_units' THEN
        IF NOT EXISTS (SELECT 1 FROM platform_identity.role_versions v
                        WHERE v.role_version_id = NEW.role_version_id AND v.effective_to_at IS NULL
                          AND v.access_scope_kind = 'CUSTOM_ORG_UNITS') THEN
            RAISE EXCEPTION 'custom org units need an open CUSTOM_ORG_UNITS role version' USING ERRCODE = 'restrict_violation';
        END IF;
    ELSE
        IF NOT EXISTS (SELECT 1 FROM platform_identity.authorization_requests r
                        WHERE r.request_id = NEW.request_id AND r.request_status = 'PENDING'
                          AND r.request_kind IN ('CREATE_ROLE', 'UPDATE_ROLE') AND r.access_scope_kind = 'CUSTOM_ORG_UNITS') THEN
            RAISE EXCEPTION 'custom org units need a pending CUSTOM_ORG_UNITS role request' USING ERRCODE = 'restrict_violation';
        END IF;
    END IF;
    RETURN NEW;
END $$;
COMMENT ON FUNCTION platform_identity.guard_custom_org_unit_rows() IS '自定义部门子表的写入守卫：只许插入，插入时父行须开放（角色版本）或待确认（授权申请），范围类型须为 CUSTOM_ORG_UNITS';
CREATE TRIGGER tg_role_version_custom_org_units_guard BEFORE INSERT OR UPDATE OR DELETE ON platform_identity.role_version_custom_org_units
    FOR EACH ROW EXECUTE FUNCTION platform_identity.guard_custom_org_unit_rows();
CREATE TRIGGER tg_role_version_custom_org_units_guard_truncate BEFORE TRUNCATE ON platform_identity.role_version_custom_org_units
    FOR EACH STATEMENT EXECUTE FUNCTION platform_identity.guard_custom_org_unit_rows();
CREATE TRIGGER tg_authorization_request_custom_org_units_guard BEFORE INSERT OR UPDATE OR DELETE ON platform_identity.authorization_request_custom_org_units
    FOR EACH ROW EXECUTE FUNCTION platform_identity.guard_custom_org_unit_rows();
CREATE TRIGGER tg_authorization_request_custom_org_units_guard_truncate BEFORE TRUNCATE ON platform_identity.authorization_request_custom_org_units
    FOR EACH STATEMENT EXECUTE FUNCTION platform_identity.guard_custom_org_unit_rows();

-- 系统角色（super_admin、employee 等六个）不在迁移中写入：由代码中的系统角色声明在启动时对账写入（ADR-0034 第 1 项第 7 条）。
INSERT INTO platform_identity.navigation_items (item_id, parent_item_id, name, route_path, permission_code, sort_order) VALUES
    ('019a0000-0000-7000-8000-000000000201', NULL, '待办', '/inbox', 'platform.approval:task:read', 10),
    ('019a0000-0000-7000-8000-000000000202', NULL, '系统管理', NULL, NULL, 90),
    ('019a0000-0000-7000-8000-000000000203', '019a0000-0000-7000-8000-000000000202', '角色管理', '/admin/roles', 'platform.identity:role:read', 10),
    ('019a0000-0000-7000-8000-000000000204', '019a0000-0000-7000-8000-000000000202', '编号规则', '/admin/numbering-rules', 'platform.numbering:numbering-rule:read', 20),
    ('019a0000-0000-7000-8000-000000000205', '019a0000-0000-7000-8000-000000000202', '组织浏览', '/admin/org-units', 'organization.workforce:org-unit:read', 30),
    ('019a0000-0000-7000-8000-000000000206', '019a0000-0000-7000-8000-000000000202', '同步记录', '/admin/sync-runs', 'organization.workforce:sync-run:read', 40);
