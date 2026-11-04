-- #918: an unpaid-leave start raises a sensitive-role review for a person who holds a sensitive role (ADR-0068 decision 3).
-- The item has its own origin; it names the personnel command and no changed field, so the four before/after columns and the trigger field stay empty
-- (ck_permission_adjustments_assignment_change already says so for every origin other than ASSIGNMENT_CHANGE). No constraint or index is added or renamed; the recipient, original, uphold, expiry and closed checks need no change for this origin.
ALTER TABLE platform_identity.permission_adjustments DROP CONSTRAINT ck_permission_adjustments_origin_kind;
ALTER TABLE platform_identity.permission_adjustments ADD CONSTRAINT ck_permission_adjustments_origin_kind CHECK (
    origin_kind IN ('ASSIGNMENT_CHANGE', 'UPHOLD_EXPIRY', 'ORG_UNIT_INACTIVE', 'UNPAID_LEAVE_START'));
ALTER TABLE platform_identity.permission_adjustments DROP CONSTRAINT ck_permission_adjustments_kind_origin;
ALTER TABLE platform_identity.permission_adjustments ADD CONSTRAINT ck_permission_adjustments_kind_origin CHECK (
    (kind = 'SENSITIVE_ROLE_REVIEW' AND origin_kind IN ('ASSIGNMENT_CHANGE', 'UPHOLD_EXPIRY', 'UNPAID_LEAVE_START'))
    OR (kind = 'SCOPE_REVIEW' AND origin_kind IN ('ASSIGNMENT_CHANGE', 'ORG_UNIT_INACTIVE')));
ALTER TABLE platform_identity.permission_adjustments DROP CONSTRAINT ck_permission_adjustments_trigger_command;
ALTER TABLE platform_identity.permission_adjustments ADD CONSTRAINT ck_permission_adjustments_trigger_command CHECK (
    (origin_kind IN ('ASSIGNMENT_CHANGE', 'UNPAID_LEAVE_START')) = (trigger_personnel_command_id IS NOT NULL)
    AND (origin_kind = 'ORG_UNIT_INACTIVE') = (trigger_org_structure_command_id IS NOT NULL));
COMMENT ON COLUMN platform_identity.permission_adjustments.origin_kind IS '条目来源：ASSIGNMENT_CHANGE 任职变化 / UPHOLD_EXPIRY 维持到期（重新生成）/ ORG_UNIT_INACTIVE 自定义部门停用或撤销 / UNPAID_LEAVE_START 停薪留职开始（只产生敏感角色复核）';
COMMENT ON COLUMN platform_identity.permission_adjustments.trigger_personnel_command_id IS '触发人事命令 ID：调岗、更正或停薪留职开始命令（EmployeeTransferred、EmployeeCorrected、EmployeeUnpaidLeaveStarted），跨 schema 不建外键（命令在 organization_workforce）；为空表示不是人事命令产生的条目';
