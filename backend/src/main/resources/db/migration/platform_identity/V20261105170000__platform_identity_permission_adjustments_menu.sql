-- #1297：待调整权限页面的导航项，挂在「系统管理」下，绑读取权限码（不新增权限）：没有读取权限的人看不到菜单，
-- 「系统管理」也不会出现在普通会话里。只经范围复核被指派、没有读取权限的负责人，从待办或链接进入详情，不经菜单。
-- 编号不在 …0002xx 段内：MigrationsWriteNoRoleDataIT 以该段识别基线种子。
INSERT INTO platform_identity.navigation_items (item_id, parent_item_id, name, route_path, permission_code, sort_order) VALUES
    ('019a0000-0000-7000-8000-000000000304', '019a0000-0000-7000-8000-000000000202', '待调整权限', '/admin/permission-adjustments', 'platform.identity:permission-adjustment:read', 16);
