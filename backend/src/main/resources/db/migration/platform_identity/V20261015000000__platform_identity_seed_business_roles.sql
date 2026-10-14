-- ADR-0027 第 2、3 项：三个系统业务角色；流程管理员（D-1a-61）只标识持有人，不带权限，审批模块按它取人。组织数据管理员与业务管理员的持有人在上线前由运维命令按账号授予，迁移不写入任何账号。
INSERT INTO platform_identity.roles (code, name, kind, status, access_scope, is_administrative) VALUES
    ('org_data_admin', '组织数据管理员', 'SYSTEM', 'ENABLED', 'ALL', false),
    ('business_admin', '业务管理员', 'SYSTEM', 'ENABLED', 'SELF', false),
    ('security_auditor', '安全审计员', 'SYSTEM', 'ENABLED', 'ALL', false),
    ('process_administrator', '流程管理员', 'SYSTEM', 'ENABLED', 'SELF', false);

INSERT INTO platform_identity.role_permissions (role_id, permission_code)
SELECT roles.role_id, grants.permission_code
  FROM platform_identity.roles roles
  JOIN (VALUES
      ('org_data_admin', 'organization.workforce:org-unit:read'),
      ('org_data_admin', 'organization.workforce:sync-run:read'),
      ('org_data_admin', 'organization.workforce:sync-run:execute'),
      ('org_data_admin', 'organization.workforce:legal-entity:update'),
      ('org_data_admin', 'organization.attendance:work-calendar:read'),
      ('org_data_admin', 'organization.attendance:work-calendar:update'),
      ('org_data_admin', 'organization.attendance:locked-day:update'),
      ('org_data_admin', 'platform.numbering:numbering-rule:read'),
      ('org_data_admin', 'platform.numbering:numbering-rule:update'),
      ('business_admin', 'platform.numbering:numbering-rule:read'),
      ('business_admin', 'platform.numbering:numbering-rule:approve'),
      ('security_auditor', 'platform.audit:audit-entry:read'),
      ('security_auditor', 'platform.audit:audit-entry:export'),
      ('employee', 'platform.audit:own-login-record:read')
  ) AS grants (role_code, permission_code) ON grants.role_code = roles.code;
