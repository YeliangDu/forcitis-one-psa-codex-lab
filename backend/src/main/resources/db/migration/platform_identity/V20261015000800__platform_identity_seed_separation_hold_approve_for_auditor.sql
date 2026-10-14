-- ADR-0028：安全审计员在普通会话里也能放行管理员离职造成的同步暂停。
INSERT INTO platform_identity.role_permissions (role_id, permission_code)
SELECT role_id, 'organization.workforce:separation-hold:approve'
  FROM platform_identity.roles
 WHERE code = 'security_auditor';
