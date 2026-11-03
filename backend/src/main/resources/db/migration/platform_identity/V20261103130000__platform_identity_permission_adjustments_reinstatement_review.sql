-- F-1 data scope block 3 (#730): a correction that undoes a wrong separation raises a person item with no department before it (ADR-0082 decision 2).
-- The department before the trigger may be empty only for a sensitive-role review triggered by the department; the comparison of the two departments
-- must not turn an empty side into "unknown", so it uses IS DISTINCT FROM.
ALTER TABLE platform_identity.permission_adjustments DROP CONSTRAINT ck_permission_adjustments_assignment_change;
ALTER TABLE platform_identity.permission_adjustments ADD CONSTRAINT ck_permission_adjustments_assignment_change CHECK (
    (origin_kind = 'ASSIGNMENT_CHANGE') = (trigger_field_kind IS NOT NULL)
    AND (origin_kind = 'ASSIGNMENT_CHANGE') = (after_org_unit_id IS NOT NULL)
    AND (origin_kind <> 'ASSIGNMENT_CHANGE' OR before_org_unit_id IS NOT NULL
         OR (kind = 'SENSITIVE_ROLE_REVIEW' AND trigger_field_kind = 'ORG_UNIT'))
    AND (before_org_unit_id IS NOT NULL OR before_position_id IS NULL)
    AND (origin_kind = 'ASSIGNMENT_CHANGE' OR (before_org_unit_id IS NULL AND after_org_unit_id IS NULL
                                               AND before_position_id IS NULL AND after_position_id IS NULL))
    AND (trigger_field_kind IS DISTINCT FROM 'ORG_UNIT' OR before_org_unit_id IS DISTINCT FROM after_org_unit_id));
COMMENT ON COLUMN platform_identity.permission_adjustments.before_org_unit_id IS '触发前部门 ID（跨 schema）；为空表示不是任职变化产生的条目，或触发前此人不在职（撤销误离职的更正产生的敏感角色复核，ADR-0082 决定 2）';
