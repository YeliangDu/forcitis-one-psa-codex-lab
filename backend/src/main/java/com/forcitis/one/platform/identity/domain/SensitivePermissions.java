package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.platform.identity.api.AccessScopeKind;
import com.forcitis.one.platform.identity.api.CustomOrgUnit;
import com.forcitis.one.platform.identity.api.Permission;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Granting any of these, or changing a role to carry one, needs a second person (ADR-0026 section 6, ADR-0027 section 3); a role that
 * carries one is a two-person role, whose scope must not follow the holder's job and whose custom org units are named one by one.
 */
public final class SensitivePermissions {

    public static final Set<String> CODES = Set.of(
            Permission.Codes.PLATFORM_IDENTITY_ACCOUNT_ROLE_UPDATE,
            Permission.Codes.PLATFORM_AUDIT_ENTRY_READ,
            Permission.Codes.PLATFORM_AUDIT_ENTRY_EXPORT,
            Permission.Codes.PLATFORM_NUMBERING_RULE_APPROVE,
            Permission.Codes.PLATFORM_NUMBERING_VOUCHER_RULE_APPROVE,
            Permission.Codes.ORGANIZATION_WORKFORCE_SEPARATION_HOLD_APPROVE,
            Permission.Codes.ORGANIZATION_PAYROLL_BONUS_SUBMIT,
            Permission.Codes.ORGANIZATION_PAYROLL_BONUS_UPDATE,
            Permission.Codes.ORGANIZATION_PAYROLL_BONUS_READ);

    /** ADR-0032 section 3 (a), answer 0032-Q1: the scopes that do not move with the holder's job. */
    public static final Set<AccessScopeKind> TWO_PERSON_SCOPES = Set.of(AccessScopeKind.ALL, AccessScopeKind.CUSTOM_ORG_UNITS, AccessScopeKind.SELF);

    /** The reason code of a refused custom org unit with descendants (F-1 card, C4: grant.refused). */
    public static final String WITH_DESCENDANTS = "two-person-role-with-descendants";

    /** The reason code of a refused scope that follows the holder's job. */
    public static final String SCOPE_FOLLOWS_THE_JOB = "two-person-role-scope-follows-the-job";

    private SensitivePermissions() {
    }

    public static Set<String> within(Set<String> permissionCodes) {
        return permissionCodes.stream().filter(CODES::contains).collect(Collectors.toUnmodifiableSet());
    }

    public static boolean roleIsSensitive(Role role) {
        return role.administrative() || Role.isReserved(role.code()) || !within(role.permissionCodes()).isEmpty();
    }

    private static final List<AccessScopeKind> BREADTH = List.of(AccessScopeKind.SELF, AccessScopeKind.CUSTOM_ORG_UNITS, AccessScopeKind.ALL);

    /**
     * A change that makes a role take effect for more than before (ADR-0032 decision 1 item 3): a broader scope kind (SELF, CUSTOM_ORG_UNITS, ALL in that
     * order), custom org units that the old units do not cover, or a disabled role enabled again. Added codes are judged apart, by {@link #within}.
     */
    public static boolean addsReach(RoleDefinition before, RoleDefinition after) {
        if (before.status() == RoleStatus.DISABLED && after.status() == RoleStatus.ENABLED) {
            return true;
        }
        if (before.accessScope() != after.accessScope()) {
            return BREADTH.indexOf(after.accessScope()) > BREADTH.indexOf(before.accessScope());
        }
        Set<OrgUnitId> covered = before.customOrgUnits().stream().map(CustomOrgUnit::orgUnitId).collect(Collectors.toSet());
        return after.customOrgUnits().stream().map(CustomOrgUnit::orgUnitId).anyMatch(unit -> !covered.contains(unit));
    }

    /**
     * Why the definition cannot be kept, or empty when it can: a two-person role (one that carries a code of {@link #CODES}) takes only
     * {@link #TWO_PERSON_SCOPES} (invariant 6) and lists every custom org unit without descendants (invariant 5, F1-01). Checked on create,
     * on revision, when a confirmed request is applied and by the start-up reconciliation of the system roles.
     */
    public static Optional<String> scopeRefusal(RoleDefinition definition) {
        if (within(definition.permissionCodes()).isEmpty()) {
            return Optional.empty();
        }
        if (!TWO_PERSON_SCOPES.contains(definition.accessScope())) {
            return Optional.of(SCOPE_FOLLOWS_THE_JOB);
        }
        if (definition.customOrgUnits().stream().anyMatch(CustomOrgUnit::includeDescendants)) {
            return Optional.of(WITH_DESCENDANTS);
        }
        return Optional.empty();
    }
}
