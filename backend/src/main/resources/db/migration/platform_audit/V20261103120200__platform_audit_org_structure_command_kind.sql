-- IMP-1030-ORG-F2b（#907）：审计对象类别 ORG_STRUCTURE_COMMAND（组织结构命令）。组织结构命令的事件以命令为聚合，岗位命令的审计也以命令为对象（岗位没有自己的类别）。
INSERT INTO platform_audit.audit_subject_kinds (audit_subject_kind_id, code, owner_bc_code, has_object, status, effective_from_date, created_at, updated_at)
VALUES ('019a0000-0000-7000-8000-000000000622', 'ORG_STRUCTURE_COMMAND', 'organization.workforce', true, 'ACTIVE', DATE '2000-01-01', TIMESTAMPTZ '2000-01-01 00:00:00+00', TIMESTAMPTZ '2000-01-01 00:00:00+00');
