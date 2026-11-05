-- #1297：待调整权限页面的导航项，挂在「系统管理」下。不绑权限码：读取接口对被指派人（如新部门负责人）本就放开，
-- 页面是否有数据由后端的关系范围决定，菜单不在前端替它做权限判断；不新增权限。
-- 编号不在 …0002xx 段内：MigrationsWriteNoRoleDataIT 以该段识别基线种子。
INSERT INTO platform_identity.navigation_items (item_id, parent_item_id, name, route_path, permission_code, sort_order) VALUES
    ('019a0000-0000-7000-8000-000000000304', '019a0000-0000-7000-8000-000000000202', '待调整权限', '/admin/permission-adjustments', NULL, 16);
