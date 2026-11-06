-- #1451：审计对象类别 RECRUITMENT_REQUISITION（招聘需求）。招聘需求获批与关闭的事件以需求为聚合，类别供事件目录的聚合换算。
INSERT INTO platform_audit.audit_subject_kinds (audit_subject_kind_id, code, owner_bc_code, has_object, status, effective_from_date, created_at, updated_at)
VALUES ('019a0000-0000-7000-8000-000000000625', 'RECRUITMENT_REQUISITION', 'organization.recruitment', true, 'ACTIVE', DATE '2000-01-01', TIMESTAMPTZ '2000-01-01 00:00:00+00', TIMESTAMPTZ '2000-01-01 00:00:00+00');
