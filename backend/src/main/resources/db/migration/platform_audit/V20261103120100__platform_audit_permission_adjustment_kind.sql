-- F-1（#729）：待调整权限的事件按聚合类型 PermissionAdjustment 进审计，对象类别 PERMISSION_ADJUSTMENT 先登记。
INSERT INTO platform_audit.audit_subject_kinds (audit_subject_kind_id, code, owner_bc_code, has_object, status, effective_from_date, created_at, updated_at) VALUES
    ('019a0000-0000-7000-8000-000000000621', 'PERMISSION_ADJUSTMENT', 'platform.identity', true, 'ACTIVE', DATE '2000-01-01', TIMESTAMPTZ '2000-01-01 00:00:00+00', TIMESTAMPTZ '2000-01-01 00:00:00+00');
