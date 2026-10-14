-- 表注释写明粒度（数据架构守护 D3）。
COMMENT ON TABLE platform_identity.user_accounts IS '用户账号：身份侧的启用开关与最近登录；员工姓名、部门、在职状态归组织人事模块（不在此冗余）。粒度：每个用户一行';
COMMENT ON TABLE platform_identity.roles IS '角色：编码与名称各自唯一；SYSTEM 角色冻结；新角色默认数据范围 SELF（RULE-345）。粒度：每个角色一行';
COMMENT ON TABLE platform_identity.role_permissions IS '角色—权限：权限码取自 Permission 目录；整组替换（RULE-346）。粒度：每个角色的每个权限码一行';
COMMENT ON TABLE platform_identity.account_roles IS '账号—角色：整组替换（RULE-346）；默认角色只在建档时显式授予一次（RULE-344）；离职时全部删除（ADR-0027）。粒度：每个账号的每个角色一行';
COMMENT ON TABLE platform_identity.navigation_items IS '导航项：目录（无路由）与页面（有路由）；页面按所需权限码对持有者可见（RULE-348）。粒度：每个导航项一行';
COMMENT ON TABLE platform_identity.login_states IS '企业微信 OAuth state：只存哈希；一次性，消费时原子删除（I-9）。粒度：每个尚未使用的 state 一行';
COMMENT ON TABLE platform_identity.sso_ticket_uses IS '已使用的一次性凭证：主键即唯一约束，并发重放只有一次插入成功（I-9，RULE-320）。粒度：每个已使用的一次性凭证一行';
COMMENT ON TABLE platform_identity.ticket_issuers IS '票据签发方：外部签名票据的每方一把密钥（kid 区分），默认关闭；签发方必须在 architecture/ticket-issuers-allowlist.txt 中（I-2）。粒度：每个签发方一行';
COMMENT ON TABLE platform_identity.emergency_admins IS '应急管理员：独立凭据（Argon2id + TOTP），只能从内网登录，5 次失败锁 15 分钟（RULE-316/317）；user_id 是绑定的超级管理员持有人，每人至多一个，登记至少两个（ADR-0027 第 1 项）。粒度：每个绑定的超级管理员持有人一个应急账号一行';
