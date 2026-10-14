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

/**
 * One scope model for reads and writes (RULE-349/352): the union over the person's ENABLED roles (RULE-343/349).
 * Nothing enabled, or nothing granted, gives {@link AccessScope#none()} - never "everything" (RULE-351).
 */
public final class AccessScopeCalculator {

    /** One role as the calculation sees it. customOrgUnits only counts for CUSTOM_ORG_UNITS (RULE-349). */
    public record RoleScope(AccessScopeKind kind, boolean enabled, Set<OrgUnitId> customOrgUnits) {

        public RoleScope {
            customOrgUnits = Set.copyOf(customOrgUnits);
        }
    }

    /** Organisation facts about the person: directReports must already exclude separated people (RULE-349). */
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
