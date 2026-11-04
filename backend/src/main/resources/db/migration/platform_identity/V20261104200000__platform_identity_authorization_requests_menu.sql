-- #1286：授权申请页面的导航项，挂在「系统管理」下，只绑定读取申请已在用的权限（account-role:update），不新增权限。
-- 编号不在 …0002xx 段内：MigrationsWriteNoRoleDataIT 以该段识别基线种子。
INSERT INTO platform_identity.navigation_items (item_id, parent_item_id, name, route_path, permission_code, sort_order) VALUES
    ('019a0000-0000-7000-8000-000000000302', '019a0000-0000-7000-8000-000000000202', '授权申请', '/admin/authorization-requests', 'platform.identity:account-role:update', 15);
