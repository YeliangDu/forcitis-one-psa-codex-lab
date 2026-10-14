-- 系统角色：super_admin 为预留码（RULE-340/345），只能在此建立；employee 是新员工建档时显式授予一次的默认角色（RULE-344），仅含最小权限。
INSERT INTO platform_identity.roles (code, name, kind, status, access_scope, is_administrative) VALUES
    ('super_admin', '超级管理员', 'SYSTEM', 'ENABLED', 'ALL', true),
    ('employee', '员工', 'SYSTEM', 'ENABLED', 'SELF', false);

INSERT INTO platform_identity.role_permissions (role_id, permission_code)
SELECT roles.role_id, grants.permission_code
  FROM platform_identity.roles roles
 CROSS JOIN (VALUES ('platform.approval:task:read'), ('platform.approval:task:approve')) AS grants (permission_code)
 WHERE roles.code = 'employee';

INSERT INTO platform_identity.navigation_items (name, route_path, permission_code, sort_order) VALUES
    ('待办', '/inbox', 'platform.approval:task:read', 10);

WITH directory AS (
    INSERT INTO platform_identity.navigation_items (name, route_path, permission_code, sort_order)
    VALUES ('系统管理', NULL, NULL, 90)
    RETURNING item_id
)
INSERT INTO platform_identity.navigation_items (parent_item_id, name, route_path, permission_code, sort_order)
SELECT directory.item_id, pages.name, pages.route_path, pages.permission_code, pages.sort_order
  FROM directory
 CROSS JOIN (VALUES
     ('角色管理', '/admin/roles', 'platform.identity:role:read', 10),
     ('编号规则', '/admin/numbering-rules', 'platform.numbering:numbering-rule:read', 20),
     ('组织浏览', '/admin/org-units', 'organization.workforce:org-unit:read', 30),
     ('同步记录', '/admin/sync-runs', 'organization.workforce:sync-run:read', 40)
 ) AS pages (name, route_path, permission_code, sort_order);
