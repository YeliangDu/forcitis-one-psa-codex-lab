package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.platform.identity.api.AccessGrant;
import com.forcitis.one.platform.identity.api.AccessScope;
import com.forcitis.one.platform.identity.api.AccessScopeKind;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Nothing enabled, or nothing granted, gives AccessScope.none(), never "everything". */
public final class AccessScopeCalculator {

    /** customOrgUnits (shown as such) and customSubtrees (shown with everything below) only count for CUSTOM_ORG_UNITS. */
    public record RoleScope(AccessScopeKind kind, boolean enabled, Set<OrgUnitId> customOrgUnits, Set<OrgUnitId> customSubtrees) {

        public RoleScope {
            customOrgUnits = Set.copyOf(customOrgUnits);
            customSubtrees = Set.copyOf(customSubtrees);
        }

        public RoleScope(AccessScopeKind kind, boolean enabled, Set<OrgUnitId> customOrgUnits) {
            this(kind, enabled, customOrgUnits, Set.of());
        }
    }

    /** directReports must already exclude separated people. */
    public record Person(EmployeeId employeeId, LegalEntityId legalEntityId, OrgUnitId orgUnitId, Set<EmployeeId> directReports) {

        public Person {
            directReports = Set.copyOf(directReports);
        }
    }

    /** The org units a person reaches through roles, apart from legal entities and people: units shown as such, and units shown with everything below them. */
    public record OrgUnitReach(boolean unrestricted, Set<OrgUnitId> units, Set<OrgUnitId> subtrees) {

        public OrgUnitReach {
            units = Set.copyOf(units);
            subtrees = Set.copyOf(subtrees);
        }
    }

    private AccessScopeCalculator() {
    }

    /** No home unit (no employee, or no current assignment) is an empty answer, not a failure: the own-unit kinds add nothing and the other roles still count (#730 comment 6606893624). */
    public static OrgUnitReach orgUnitReach(@Nullable OrgUnitId home, Collection<RoleScope> roles) {
        Set<OrgUnitId> units = new HashSet<>();
        Set<OrgUnitId> subtrees = new HashSet<>();
        for (RoleScope role : roles) {
            if (!role.enabled()) {
                continue;
            }
            switch (role.kind()) {
                case ALL -> {
                    return new OrgUnitReach(true, Set.of(), Set.of());
                }
                case CUSTOM_ORG_UNITS -> {
                    units.addAll(role.customOrgUnits());
                    subtrees.addAll(role.customSubtrees());
                }
                case OWN_ORG_UNIT -> {
                    if (home != null) {
                        units.add(home);
                    }
                }
                case OWN_ORG_UNIT_SUBTREE -> {
                    if (home != null) {
                        subtrees.add(home);
                    }
                }
                case SELF, SELF_AND_DIRECT_REPORTS -> {
                }
            }
        }
        return new OrgUnitReach(false, units, subtrees);
    }

    public static AccessScope calculate(Person person, Collection<RoleScope> roles) {
        Set<AccessGrant> grants = new HashSet<>();
        Set<EmployeeId> owners = new HashSet<>();
        for (RoleScope role : roles) {
            if (!role.enabled()) {
                continue;
            }
            switch (role.kind()) {
                case ALL -> {
                    return AccessScope.everything();
                }
                case CUSTOM_ORG_UNITS -> {
                    role.customOrgUnits().forEach(unit -> grants.add(new AccessGrant(person.legalEntityId(), unit, false, null)));
                    role.customSubtrees().forEach(unit -> grants.add(new AccessGrant(person.legalEntityId(), unit, true, null)));
                }
                case OWN_ORG_UNIT -> grants.add(new AccessGrant(person.legalEntityId(), person.orgUnitId(), false, null));
                case OWN_ORG_UNIT_SUBTREE -> grants.add(new AccessGrant(person.legalEntityId(), person.orgUnitId(), true, null));
                case SELF -> owners.add(person.employeeId());
                case SELF_AND_DIRECT_REPORTS -> {
                    owners.add(person.employeeId());
                    owners.addAll(person.directReports());
                }
            }
        }
        return new AccessScope(false, grants, owners);
    }
}
