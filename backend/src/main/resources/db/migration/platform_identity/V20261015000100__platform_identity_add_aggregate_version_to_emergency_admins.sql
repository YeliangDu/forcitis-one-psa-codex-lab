-- 应急管理员成为聚合：登记、停用、启用、重置凭据都发事件（ADR-0027 第 1 项；D-1a-168）。
ALTER TABLE platform_identity.emergency_admins
    ADD COLUMN version     bigint NOT NULL DEFAULT 0,
    ADD COLUMN event_count bigint NOT NULL DEFAULT 0;
COMMENT ON COLUMN platform_identity.emergency_admins.version IS '乐观锁版本：启用状态与凭据的变更按它串行；失败计数、锁定、动态码步长是访问元数据，用条件原子更新，不动它';
COMMENT ON COLUMN platform_identity.emergency_admins.event_count IS '事件序号：该聚合已发出的事件数，每发一个事件加 1，作为事件信封的 aggregateVersion；与乐观锁 version 分开';
COMMENT ON TABLE platform_identity.emergency_admins IS '应急管理员：独立凭据（Argon2id + TOTP），只能从内网登录，5 次失败锁 15 分钟（RULE-316/317）；user_id 是绑定的超级管理员持有人，每人至多一个，登记至少两个（ADR-0027 第 1 项）';
