package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.platform.identity.api.AccessGrant;
import com.forcitis.one.platform.identity.api.AccessScope;
import com.forcitis.one.platform.identity.api.AccessScopeKind;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Nothing enabled, or nothing granted, gives AccessScope.none(), never "everything". */
public final class AccessScopeCalculator {

    /** customOrgUnits only counts for CUSTOM_ORG_UNITS. */
    public record RoleScope(AccessScopeKind kind, boolean enabled, Set<OrgUnitId> customOrgUnits) {

        public RoleScope {
            customOrgUnits = Set.copyOf(customOrgUnits);
        }
    }

    /** directReports must already exclude separated people. */
    public record Person(UserId userId, LegalEntityId legalEntityId, OrgUnitId orgUnitId, Set<UserId> directReports) {

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

    /** A role with an own-unit scope needs the home unit: without one the call fails instead of reaching nothing quietly. */
    public static OrgUnitReach orgUnitReach(OrgUnitId home, Collection<RoleScope> roles) {
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
                case CUSTOM_ORG_UNITS -> units.addAll(role.customOrgUnits());
                case OWN_ORG_UNIT -> units.add(Objects.requireNonNull(home, "orgUnitId"));
                case OWN_ORG_UNIT_SUBTREE -> subtrees.add(Objects.requireNonNull(home, "orgUnitId"));
                case SELF, SELF_AND_DIRECT_REPORTS -> {
                }
            }
        }
        return new OrgUnitReach(false, units, subtrees);
    }

    public static AccessScope calculate(Person person, Collection<RoleScope> roles) {
        Set<AccessGrant> grants = new HashSet<>();
        Set<UserId> owners = new HashSet<>();
        for (RoleScope role : roles) {
            if (!role.enabled()) {
                continue;
            }
            switch (role.kind()) {
                case ALL -> {
                    return AccessScope.everything();
                }
                case CUSTOM_ORG_UNITS -> role.customOrgUnits()
                        .forEach(unit -> grants.add(new AccessGrant(person.legalEntityId(), unit, false, null)));
                case OWN_ORG_UNIT -> grants.add(new AccessGrant(person.legalEntityId(), person.orgUnitId(), false, null));
                case OWN_ORG_UNIT_SUBTREE -> grants.add(new AccessGrant(person.legalEntityId(), person.orgUnitId(), true, null));
                case SELF -> owners.add(person.userId());
                case SELF_AND_DIRECT_REPORTS -> {
                    owners.add(person.userId());
                    owners.addAll(person.directReports());
                }
            }
        }
        return new AccessScope(false, grants, owners);
    }
}
