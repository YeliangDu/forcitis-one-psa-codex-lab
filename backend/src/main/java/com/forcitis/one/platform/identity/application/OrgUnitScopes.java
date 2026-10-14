package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.OrgUnitAccess;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.identity.api.AccessGrant;
import com.forcitis.one.platform.identity.api.AccessScope;
import com.forcitis.one.platform.identity.api.CurrentUser;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.domain.AccessScopeCalculator;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The scope of the signed-in person for one permission: only enabled roles that grant it count (RULE-349), a person the organisation
 * does not know has no department, and anything that goes wrong refuses instead of showing more. Org units have no owner, so the
 * people-based scopes show none; the legal entity dimension waits for the first fact that carries one (ADR-0026 section 2).
 */
@Service
class OrgUnitScopes implements OrgUnitAccess {

    private static final LegalEntityId NOT_USED = new LegalEntityId(1);

    private final CurrentUser currentUser;
    private final AccountRepository accounts;
    private final OrgDirectory directory;

    OrgUnitScopes(CurrentUser currentUser, AccountRepository accounts, OrgDirectory directory) {
        this.currentUser = currentUser;
        this.accounts = accounts;
        this.directory = directory;
    }

    @Override
    @Transactional(readOnly = true)
    public Visible visibleOrgUnits(String permissionCode) {
        try {
            UserId userId = currentUser.userId();
            List<AccessScopeCalculator.RoleScope> roles = accounts.findEnabledRoles(userId).stream()
                    .filter(role -> role.permissionCodes().contains(permissionCode))
                    .map(role -> new AccessScopeCalculator.RoleScope(role.accessScope(), true, Set.of()))
                    .toList();
            var home = directory.findEmployee(userId).map(employee -> employee.orgUnitId());
            AccessScope scope = AccessScopeCalculator.calculate(
                    new AccessScopeCalculator.Person(userId, NOT_USED, home.orElse(null), Set.of()), roles);
            if (scope.unrestricted()) {
                return Visible.all();
            }
            Set<Long> units = new HashSet<>();
            Set<Long> subtrees = new HashSet<>();
            for (AccessGrant grant : scope.grants()) {
                (grant.withDescendants() ? subtrees : units).add(grant.orgUnitId().value());
            }
            return new Visible(false, units, subtrees);
        } catch (DomainException refusal) {
            throw refusal;
        } catch (RuntimeException failure) {
            throw new DomainException(IdentityError.PLATFORM_IDENTITY_ACCESS_SCOPE_UNAVAILABLE, ErrorKind.FORBIDDEN,
                    "the scope of the caller could not be worked out");
        }
    }
}
