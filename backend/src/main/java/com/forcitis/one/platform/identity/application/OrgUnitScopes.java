package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.OrgUnitAccess;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.platform.identity.api.CustomOrgUnit;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.domain.AccessScopeCalculator;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import com.forcitis.one.platform.identity.domain.Role;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The scope of the signed-in person for one permission: only enabled roles that grant it count (RULE-349), a person the organisation
 * does not know has no department, and anything that goes wrong refuses instead of showing more. Org units have no owner, so the
 * people-based scopes show none; the legal entity dimension waits for the first fact that carries one (ADR-0026 section 2).
 */
@Service
class OrgUnitScopes implements OrgUnitAccess {

    private final CurrentUser currentUser;
    private final AccountRepository accounts;
    private final OrgDirectory directory;

    OrgUnitScopes(CurrentUser currentUser, AccountRepository accounts, OrgDirectory directory) {
        this.currentUser = currentUser;
        this.accounts = accounts;
        this.directory = directory;
    }

    private static AccessScopeCalculator.RoleScope scopeOf(Role role) {
        return new AccessScopeCalculator.RoleScope(role.accessScope(), true, unitsOf(role, false), unitsOf(role, true));
    }

    private static Set<OrgUnitId> unitsOf(Role role, boolean withDescendants) {
        return role.customOrgUnits().stream().filter(unit -> unit.includeDescendants() == withDescendants).map(CustomOrgUnit::orgUnitId).collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    public Visible visibleOrgUnits(String permissionCode) {
        try {
            UserId userId = currentUser.userId();
            List<AccessScopeCalculator.RoleScope> roles = accounts.findEnabledRoles(userId).stream()
                    .filter(role -> role.permissionCodes().contains(permissionCode))
                    .map(OrgUnitScopes::scopeOf)
                    .toList();
            var home = directory.findEmployee(currentUser.employeeId()).map(employee -> employee.orgUnitId());
            AccessScopeCalculator.OrgUnitReach reach = AccessScopeCalculator.orgUnitReach(home.orElse(null), roles);
            if (reach.unrestricted()) {
                return Visible.all();
            }
            return new Visible(false, reach.units().stream().map(OrgUnitId::value).collect(Collectors.toSet()),
                    reach.subtrees().stream().map(OrgUnitId::value).collect(Collectors.toSet()));
        } catch (DomainException refusal) {
            throw refusal;
        } catch (RuntimeException failure) {
            throw new DomainException(IdentityError.PLATFORM_IDENTITY_ACCESS_SCOPE_UNAVAILABLE, ErrorKind.FORBIDDEN,
                    "the scope of the caller could not be worked out");
        }
    }
}
