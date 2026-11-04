-- #1296：人事命令页面的导航项，挂在「系统管理」下；绑定现有的人事命令发起权限（列表、详情的读取目前也用它，不新增权限）。
-- 编号不在 …0002xx 段内：MigrationsWriteNoRoleDataIT 以该段识别基线种子。
INSERT INTO platform_identity.navigation_items (item_id, parent_item_id, name, route_path, permission_code, sort_order) VALUES
    ('019a0000-0000-7000-8000-000000000302', '019a0000-0000-7000-8000-000000000202', '人事命令', '/admin/personnel-commands', 'organization.workforce:personnel-command:create', 36);
