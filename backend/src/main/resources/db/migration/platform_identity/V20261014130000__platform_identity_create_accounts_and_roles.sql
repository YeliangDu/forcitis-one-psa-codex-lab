CREATE SCHEMA IF NOT EXISTS platform_identity;
COMMENT ON SCHEMA platform_identity IS '身份与权限（权限流程与集成平台 BC · 身份）：登录、会话、角色、导航';

CREATE TABLE platform_identity.user_accounts (
    user_id            bigint      PRIMARY KEY,
    status             text        NOT NULL DEFAULT 'ENABLED' CHECK (status IN ('ENABLED', 'DISABLED')),
    last_login_at      timestamptz,
    last_login_address text,
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),
    version            bigint      NOT NULL DEFAULT 0
);
COMMENT ON TABLE platform_identity.user_accounts IS '用户账号：身份侧的启用开关与最近登录；员工姓名、部门、在职状态归组织人事模块（不在此冗余）';
COMMENT ON COLUMN platform_identity.user_accounts.user_id IS '用户 ID：与员工 ID（沿用组织源人员 ID，有符号 bigint）相同';
COMMENT ON COLUMN platform_identity.user_accounts.status IS '账号状态：ENABLED / DISABLED；登录与每次解析会话时都与在职状态一起检查（RULE-326/338）';
COMMENT ON COLUMN platform_identity.user_accounts.last_login_at IS '最近一次登录成功时间（RULE-330）';
COMMENT ON COLUMN platform_identity.user_accounts.last_login_address IS '最近一次登录成功的客户端地址（RULE-330）';
COMMENT ON COLUMN platform_identity.user_accounts.created_at IS '创建时间';
COMMENT ON COLUMN platform_identity.user_accounts.updated_at IS '更新时间';
COMMENT ON COLUMN platform_identity.user_accounts.version IS '乐观锁版本';

CREATE TABLE platform_identity.roles (
    role_id           bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code              text        NOT NULL,
    name              text        NOT NULL,
    kind              text        NOT NULL DEFAULT 'CUSTOM' CHECK (kind IN ('SYSTEM', 'CUSTOM')),
    status            text        NOT NULL DEFAULT 'ENABLED' CHECK (status IN ('ENABLED', 'DISABLED')),
    access_scope      text        NOT NULL DEFAULT 'SELF' CHECK (access_scope IN ('ALL', 'CUSTOM_ORG_UNITS', 'OWN_ORG_UNIT', 'OWN_ORG_UNIT_SUBTREE', 'SELF', 'SELF_AND_DIRECT_REPORTS')),
    is_administrative boolean     NOT NULL DEFAULT false,
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    version           bigint      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uq_roles_code ON platform_identity.roles (lower(code));
CREATE UNIQUE INDEX uq_roles_name ON platform_identity.roles (lower(name));
COMMENT ON TABLE platform_identity.roles IS '角色：编码与名称各自唯一；SYSTEM 角色冻结；新角色默认数据范围 SELF（RULE-345）';
COMMENT ON COLUMN platform_identity.roles.role_id IS '角色 ID';
COMMENT ON COLUMN platform_identity.roles.code IS '角色编码；super_admin 为预留码，只能由迁移脚本建立（RULE-340/345）';
COMMENT ON COLUMN platform_identity.roles.name IS '角色名称';
COMMENT ON COLUMN platform_identity.roles.kind IS '角色类别：SYSTEM（冻结，不可改不可删）/ CUSTOM';
COMMENT ON COLUMN platform_identity.roles.status IS '角色状态：只有 ENABLED 的角色计入权限与数据范围（RULE-343/349）';
COMMENT ON COLUMN platform_identity.roles.access_scope IS '数据范围类型：新角色默认 SELF（最小权限，RULE-345）；1a 只存类型，范围明细随 1b 的 data_scope_grants';
COMMENT ON COLUMN platform_identity.roles.is_administrative IS '是否管理员角色：外部签名票据不得让持有者进入（C1a-03）';
COMMENT ON COLUMN platform_identity.roles.created_at IS '创建时间';
COMMENT ON COLUMN platform_identity.roles.updated_at IS '更新时间';
COMMENT ON COLUMN platform_identity.roles.version IS '乐观锁版本';

CREATE TABLE platform_identity.role_permissions (
    role_id         bigint      NOT NULL REFERENCES platform_identity.roles (role_id) ON DELETE CASCADE,
    permission_code text        NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (role_id, permission_code)
);
COMMENT ON TABLE platform_identity.role_permissions IS '角色—权限：权限码取自 Permission 目录；整组替换（RULE-346）';
COMMENT ON COLUMN platform_identity.role_permissions.role_id IS '角色 ID';
COMMENT ON COLUMN platform_identity.role_permissions.permission_code IS '权限码：{子域}.{BC}:{资源}:{动作}';
COMMENT ON COLUMN platform_identity.role_permissions.created_at IS '创建时间';
COMMENT ON COLUMN platform_identity.role_permissions.updated_at IS '更新时间';

CREATE TABLE platform_identity.account_roles (
    user_id    bigint      NOT NULL REFERENCES platform_identity.user_accounts (user_id) ON DELETE CASCADE,
    role_id    bigint      NOT NULL REFERENCES platform_identity.roles (role_id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, role_id)
);
CREATE INDEX ix_account_roles_role ON platform_identity.account_roles (role_id);
COMMENT ON TABLE platform_identity.account_roles IS '账号—角色：整组替换（RULE-346）；默认角色只在建档时显式授予一次（RULE-344）';
COMMENT ON COLUMN platform_identity.account_roles.user_id IS '用户 ID';
COMMENT ON COLUMN platform_identity.account_roles.role_id IS '角色 ID';
COMMENT ON COLUMN platform_identity.account_roles.created_at IS '创建时间';
COMMENT ON COLUMN platform_identity.account_roles.updated_at IS '更新时间';

CREATE TABLE platform_identity.navigation_items (
    item_id         bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    parent_item_id  bigint      REFERENCES platform_identity.navigation_items (item_id),
    name            text        NOT NULL,
    route_path      text,
    permission_code text,
    sort_order      integer     NOT NULL DEFAULT 0,
    is_enabled      boolean     NOT NULL DEFAULT true,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_navigation_items_parent CHECK (parent_item_id IS NULL OR parent_item_id <> item_id)
);
COMMENT ON TABLE platform_identity.navigation_items IS '导航项：目录（无路由）与页面（有路由）；页面按所需权限码对持有者可见（RULE-348）';
COMMENT ON COLUMN platform_identity.navigation_items.item_id IS '导航项 ID';
COMMENT ON COLUMN platform_identity.navigation_items.parent_item_id IS '上级导航项 ID；顶层为空';
COMMENT ON COLUMN platform_identity.navigation_items.name IS '显示名称';
COMMENT ON COLUMN platform_identity.navigation_items.route_path IS '前端路由；目录为空';
COMMENT ON COLUMN platform_identity.navigation_items.permission_code IS '可见所需权限码；为空表示所有已登录用户可见';
COMMENT ON COLUMN platform_identity.navigation_items.sort_order IS '同级排序';
COMMENT ON COLUMN platform_identity.navigation_items.is_enabled IS '是否启用';
COMMENT ON COLUMN platform_identity.navigation_items.created_at IS '创建时间';
COMMENT ON COLUMN platform_identity.navigation_items.updated_at IS '更新时间';
