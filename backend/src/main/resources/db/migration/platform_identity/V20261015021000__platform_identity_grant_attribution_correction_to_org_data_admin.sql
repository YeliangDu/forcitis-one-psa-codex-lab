-- RULE-385：组织数据管理员可更正法人归属的历史记录；更正只增不改，本权限不在敏感权限之列。
INSERT INTO platform_identity.role_permissions (role_id, permission_code)
SELECT role_id, 'organization.workforce:legal-entity-attribution-correction:create'
  FROM platform_identity.roles
 WHERE code = 'org_data_admin';
