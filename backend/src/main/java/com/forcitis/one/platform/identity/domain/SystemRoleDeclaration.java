package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.kernel.ValueObject;
import com.forcitis.one.platform.identity.api.AccessScopeKind;
import com.forcitis.one.platform.identity.api.Permission.Codes;
import com.forcitis.one.platform.identity.api.RoleCode;
import com.forcitis.one.platform.identity.api.RoleId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The system roles, declared in code (ADR-0034 decision 1 item 7): the start-up reconciliation writes them; no migration writes role data.
 * The content is the seed of the identity baseline before ADR-0034-IMPL PR-2, code for code (card 7.2, 0034-G8-02). Changing a role here
 * means raising its revision number; the reconciliation then closes the current version and opens a SEED version with the new revision.
 * A {@code SystemRoleDeclaration} bean in the context replaces {@link #CURRENT}: that is the seam the acceptance tests of
 * ADR-0034-IMPL PR-2 (role_versioning) use to raise a revision; keep it.
 */
@ValueObject
public record SystemRoleDeclaration(List<DeclaredRole> roles) {

    /**
     * super_admin is the reserved code (RULE-340/345); employee is the default role granted once when an account is opened (RULE-344);
     * process_administrator receives the tasks nobody can be found for (D-1a-61); org_data_admin, business_admin and security_auditor
     * are the system business roles of ADR-0027 sections 2 and 3, with ADR-0028 (security_auditor releases a separation hold) and
     * RULE-385 (org_data_admin corrects a legal entity attribution);
     * personnel_specialist (人事专员, user decision 2026-10-30) enters and withdraws personnel commands (ADR-0068 item 1.6);
     * legal_entity_finance_head (法人财务负责人) countersigns a change of the cost legal entity when it is the finance head of the entity, and business_admin
     * may from escalation level 2 (ADR-0073 item 3.6, user answer Q3 of 2026-10-30; the rule is in organization.workforce, the role only holds the permission).
     */
    public static final SystemRoleDeclaration CURRENT = new SystemRoleDeclaration(List.of(
            declared("019a0000-0000-7000-8000-000000000101", "super_admin", "超级管理员", AccessScopeKind.ALL, true),
            declared("019a0000-0000-7000-8000-000000000102", "employee", "员工", AccessScopeKind.SELF, false,
                    Codes.PLATFORM_APPROVAL_TASK_READ, Codes.PLATFORM_APPROVAL_TASK_APPROVE, Codes.PLATFORM_AUDIT_OWN_LOGIN_RECORD_READ,
                    Codes.PLATFORM_FILE_FILE_CREATE, Codes.PLATFORM_FILE_FILE_READ),
            declared("019a0000-0000-7000-8000-000000000103", "process_administrator", "流程管理员", AccessScopeKind.SELF, false),
            declared("019a0000-0000-7000-8000-000000000104", "org_data_admin", "组织数据管理员", AccessScopeKind.ALL, false, 2,
                    Codes.ORGANIZATION_WORKFORCE_ORG_UNIT_READ, Codes.ORGANIZATION_WORKFORCE_ORG_RECONCILIATION_EXECUTE,
                    Codes.ORGANIZATION_WORKFORCE_LEGAL_ENTITY_UPDATE, Codes.ORGANIZATION_ATTENDANCE_WORK_CALENDAR_READ,
                    Codes.ORGANIZATION_ATTENDANCE_WORK_CALENDAR_UPDATE, Codes.ORGANIZATION_ATTENDANCE_LOCKED_DAY_UPDATE,
                    Codes.PLATFORM_NUMBERING_RULE_READ, Codes.PLATFORM_NUMBERING_RULE_UPDATE, Codes.PLATFORM_INTEGRATION_SOURCE_REFERENCE_UPDATE,
                    Codes.ORGANIZATION_WORKFORCE_LEGAL_ENTITY_ATTRIBUTION_CORRECTION_CREATE),
            declared("019a0000-0000-7000-8000-000000000105", "business_admin", "业务管理员", AccessScopeKind.SELF, false, 2,
                    Codes.PLATFORM_NUMBERING_RULE_READ, Codes.PLATFORM_NUMBERING_RULE_APPROVE, Codes.PLATFORM_INTEGRATION_SOURCE_REFERENCE_APPROVE,
                    Codes.ORGANIZATION_WORKFORCE_LEGAL_ENTITY_ATTRIBUTION_APPROVE),
            declared("019a0000-0000-7000-8000-000000000106", "security_auditor", "安全审计员", AccessScopeKind.ALL, false,
                    Codes.PLATFORM_AUDIT_ENTRY_READ, Codes.PLATFORM_AUDIT_ENTRY_EXPORT, Codes.ORGANIZATION_WORKFORCE_SEPARATION_HOLD_APPROVE),
            declared("019a0000-0000-7000-8000-000000000107", "personnel_specialist", "人事专员", AccessScopeKind.ALL, false,
                    Codes.ORGANIZATION_WORKFORCE_PERSONNEL_COMMAND_CREATE),
            declared("019a0000-0000-7000-8000-000000000108", "legal_entity_finance_head", "法人财务负责人", AccessScopeKind.SELF, false,
                    Codes.ORGANIZATION_WORKFORCE_LEGAL_ENTITY_ATTRIBUTION_APPROVE)));

    /** The revision every role of the first declaration carries (card 7.2: 声明修订号 1); org_data_admin is at 2 since #934 swapped its sync-run codes for org-reconciliation:execute; business_admin is at 2 since it holds legal-entity-attribution:approve. */
    static final int FIRST_REVISION = 1;

    public SystemRoleDeclaration {
        roles = List.copyOf(roles);
        Set<RoleId> ids = new HashSet<>();
        Set<String> codes = new HashSet<>();
        for (DeclaredRole role : roles) {
            if (!ids.add(role.roleId()) || !codes.add(role.code().value().toLowerCase(java.util.Locale.ROOT))) {
                throw new IllegalArgumentException("a system role is declared twice: " + role.roleId().value());
            }
        }
    }

    private static DeclaredRole declared(String roleId, String code, String name, AccessScopeKind scope, boolean administrative, String... permissionCodes) {
        return declared(roleId, code, name, scope, administrative, FIRST_REVISION, permissionCodes);
    }

    private static DeclaredRole declared(String roleId, String code, String name, AccessScopeKind scope, boolean administrative, int revision,
            String... permissionCodes) {
        return new DeclaredRole(new RoleId(UUID.fromString(roleId)), new RoleCode(code), administrative, revision,
                new RoleDefinition(name, RoleStatus.ENABLED, scope, Set.of(permissionCodes)));
    }
}
