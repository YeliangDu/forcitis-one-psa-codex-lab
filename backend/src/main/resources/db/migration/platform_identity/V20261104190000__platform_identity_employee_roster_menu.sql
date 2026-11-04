-- #1280：员工名册页面的导航项，挂在「系统管理」下，与「组织浏览」同一种绑定（只绑定员工读取权限，不新增权限）。
-- 编号不在 …0002xx 段内：MigrationsWriteNoRoleDataIT 以该段识别基线种子。
INSERT INTO platform_identity.navigation_items (item_id, parent_item_id, name, route_path, permission_code, sort_order) VALUES
    ('019a0000-0000-7000-8000-000000000301', '019a0000-0000-7000-8000-000000000202', '员工名册', '/admin/employees', 'organization.workforce:employee:read', 35);
