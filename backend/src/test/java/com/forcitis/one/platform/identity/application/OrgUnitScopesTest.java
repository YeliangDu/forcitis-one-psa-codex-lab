package com.forcitis.one.platform.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.fixture.doubles.TestOrgIds;
import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.kernel.OrgUnitAccess;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.platform.identity.api.AccessScopeKind;
import com.forcitis.one.platform.identity.api.CustomOrgUnit;
import com.forcitis.one.platform.identity.api.RoleCode;
import com.forcitis.one.platform.identity.api.RoleId;
import com.forcitis.one.platform.identity.api.RoleVersionId;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import com.forcitis.one.platform.identity.domain.Role;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("#730 S4 OrgUnitScopes: what the roles of the caller reach")
class OrgUnitScopesTest {
    static final String CODE = "organization.workforce:org-unit:read";
    static final OrgUnitId PARENT = TestOrgIds.orgUnit(1);
    static final OrgUnitId OTHER = TestOrgIds.orgUnit(2);

    final CurrentUser currentUser = mock(CurrentUser.class);
    final AccountRepository accounts = mock(AccountRepository.class);
    final OrgDirectory directory = mock(OrgDirectory.class);
    final OrgUnitScopes scopes = new OrgUnitScopes(currentUser, accounts, directory);

    private Role custom(Set<CustomOrgUnit> units) {
        return Role.restore(new RoleId(UUID.randomUUID()), new RoleCode("synthetic-role"), "CUSTOM", false, new RoleVersionId(UUID.randomUUID()), "Synthetic", "ENABLED",
                AccessScopeKind.CUSTOM_ORG_UNITS, Set.of(CODE), units, 1, 1);
    }

    @Test
    @DisplayName("a custom unit marked with descendants is reached as a subtree, one without as that unit alone")
    void customUnitsKeepTheirDescendantsFlag() {
        when(currentUser.userId()).thenReturn(TestAccounts.account(1));
        when(currentUser.employeeId()).thenReturn(TestAccounts.employee(1));
        when(directory.findEmployee(TestAccounts.employee(1))).thenReturn(Optional.empty());
        when(accounts.findEnabledRoles(TestAccounts.account(1))).thenReturn(List.of(custom(Set.of(new CustomOrgUnit(PARENT, true), CustomOrgUnit.only(OTHER)))));

        OrgUnitAccess.Visible visible = scopes.visibleOrgUnits(CODE);

        assertThat(visible.everything()).isFalse();
        assertThat(visible.orgUnitSubtrees()).containsExactly(PARENT.value());
        assertThat(visible.orgUnits()).containsExactly(OTHER.value());
    }
}
