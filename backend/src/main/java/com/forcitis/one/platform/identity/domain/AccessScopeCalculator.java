package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.platform.identity.api.AccessGrant;
import com.forcitis.one.platform.identity.api.AccessScope;
import com.forcitis.one.platform.identity.api.AccessScopeKind;
import java.util.Collection;
import java.util.HashSet;
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

    private AccessScopeCalculator() {
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
