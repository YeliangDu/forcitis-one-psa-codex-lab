-- 敏感权限的授予与含敏感权限的角色定义，须另一人确认后才生效（ADR-0026 第 6 项第 2 条，ADR-0027 第 3 项第 2 条；I-12）。
CREATE TABLE platform_identity.authorization_requests (
    request_id        uuid        NOT NULL,
    request_kind      text        NOT NULL CHECK (request_kind IN ('REPLACE_ROLES', 'CREATE_ROLE', 'UPDATE_ROLE')),
    requested_by      bigint      NOT NULL,
    requested_at      timestamptz NOT NULL,
    subject_user_id   bigint,
    role_id           bigint,
    role_codes        text[]      NOT NULL DEFAULT '{}',
    new_role_code     text,
    role_name         text,
    is_enabled        boolean,
    access_scope_kind text CHECK (access_scope_kind IN ('ALL', 'CUSTOM_ORG_UNITS', 'OWN_ORG_UNIT', 'OWN_ORG_UNIT_SUBTREE', 'SELF', 'SELF_AND_DIRECT_REPORTS')),
    permission_codes  text[]      NOT NULL DEFAULT '{}',
    request_status    text        NOT NULL CHECK (request_status IN ('PENDING', 'CONFIRMED', 'REJECTED')),
    decided_by        bigint,
    decided_at        timestamptz,
    version           bigint      NOT NULL DEFAULT 0,
    event_count       bigint      NOT NULL DEFAULT 0,
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_authorization_requests PRIMARY KEY (request_id),
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
COMMENT ON COLUMN platform_identity.authorization_requests.request_status IS '申请状态：PENDING 待确认 / CONFIRMED 已确认并交监听器执行 / REJECTED 已拒绝';
COMMENT ON COLUMN platform_identity.authorization_requests.decided_by IS '确认人或拒绝人用户 ID；确认人不是申请人，也不是受益人';
COMMENT ON COLUMN platform_identity.authorization_requests.decided_at IS '确认或拒绝的时间';
COMMENT ON COLUMN platform_identity.authorization_requests.version IS '乐观锁版本';
COMMENT ON COLUMN platform_identity.authorization_requests.event_count IS '事件序号：该聚合已发出的事件数，作为事件信封的 aggregateVersion；与乐观锁 version 分开';
COMMENT ON COLUMN platform_identity.authorization_requests.created_at IS '创建时间';
COMMENT ON COLUMN platform_identity.authorization_requests.updated_at IS '更新时间';
