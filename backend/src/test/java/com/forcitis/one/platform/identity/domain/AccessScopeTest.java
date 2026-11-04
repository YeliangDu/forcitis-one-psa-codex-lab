package com.forcitis.one.platform.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestIds;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.platform.identity.api.AccessGrant;
import com.forcitis.one.platform.identity.api.AccessScope;
import com.forcitis.one.platform.identity.api.AccessScopeKind;
import com.forcitis.one.platform.identity.api.AccessTarget;
import com.forcitis.one.platform.identity.api.ProjectRole;
import com.forcitis.one.platform.identity.domain.AccessScopeCalculator.Person;
import com.forcitis.one.platform.identity.domain.AccessScopeCalculator.RoleScope;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.fixture.doubles.TestOrgIds;

class AccessScopeTest {

    static final LegalEntityId LE1 = new LegalEntityId(TestIds.uuid(1));
    static final LegalEntityId LE2 = new LegalEntityId(TestIds.uuid(2));
    static final OrgUnitId ROOT = TestOrgIds.orgUnit(1);
    static final OrgUnitId DEPT_15 = TestOrgIds.orgUnit(15);
    static final OrgUnitId DEPT_16 = TestOrgIds.orgUnit(16);
    static final OrgUnitId DEPT_20 = TestOrgIds.orgUnit(20);
    static final EmployeeId ME = TestAccounts.employee(1001);
    static final EmployeeId REPORT = TestAccounts.employee(2001);
    static final EmployeeId REPORTS_REPORT = TestAccounts.employee(3001);
    static final EmployeeId STRANGER = TestAccounts.employee(9999);

    static AccessTarget row(LegalEntityId le, OrgUnitId unit, EmployeeId owner) {
        List<OrgUnitId> path = unit.equals(DEPT_16) ? List.of(DEPT_16, DEPT_15, ROOT) : unit.equals(DEPT_15) ? List.of(DEPT_15, ROOT)
                : List.of(unit, ROOT);
        return new AccessTarget(le, path, null, owner);
    }

    static final Person PERSON = new Person(ME, LE1, DEPT_15, Set.of(REPORT));

    static RoleScope role(AccessScopeKind kind) {
        return new RoleScope(kind, true, Set.of());
    }

    @Test
    @DisplayName("RULE-351 an empty scope returns zero rows, never everything")
    void emptyScopeFailsClosed() {
        assertThat(AccessScope.none().isEmpty()).isTrue();
        assertThat(AccessScope.none().permits(row(LE1, DEPT_15, ME))).isFalse();
        assertThat(new AccessScope(false, Set.of(), Set.of()).permits(row(LE1, DEPT_15, ME))).isFalse();

        assertThat(AccessScopeCalculator.calculate(PERSON, List.of())).as("no roles").isEqualTo(AccessScope.none());
        assertThat(AccessScopeCalculator.calculate(PERSON, List.of(new RoleScope(AccessScopeKind.ALL, false, Set.of()))))
                .as("only a disabled role").isEqualTo(AccessScope.none());
    }

    @Test
    @DisplayName("RULE-349 ALL in any enabled role means unrestricted")
    void allMeansUnrestricted() {
        AccessScope scope = AccessScopeCalculator.calculate(PERSON, List.of(role(AccessScopeKind.SELF), role(AccessScopeKind.ALL)));

        assertThat(scope.unrestricted()).isTrue();
        assertThat(scope.permits(row(LE2, DEPT_20, STRANGER))).isTrue();
        assertThat(AccessScope.none().union(scope).unrestricted()).isTrue();
    }

    @Test
    @DisplayName("RULE-349 only enabled roles count: a disabled ALL role does not widen the scope")
    void disabledRolesAreIgnored() {
        AccessScope scope = AccessScopeCalculator.calculate(PERSON,
                List.of(role(AccessScopeKind.SELF), new RoleScope(AccessScopeKind.ALL, false, Set.of())));

        assertThat(scope.unrestricted()).isFalse();
        assertThat(scope.permits(row(LE1, DEPT_20, STRANGER))).isFalse();
        assertThat(scope.permits(row(LE1, DEPT_20, ME))).isTrue();
    }

    @Test
    @DisplayName("RULE-349 'self and subordinates' covers direct reports only, not their reports")
    void directReportsOnly() {
        AccessScope scope = AccessScopeCalculator.calculate(PERSON, List.of(role(AccessScopeKind.SELF_AND_DIRECT_REPORTS)));

        assertThat(scope.permits(row(LE1, DEPT_20, ME))).isTrue();
        assertThat(scope.permits(row(LE1, DEPT_20, REPORT))).isTrue();
        assertThat(scope.permits(row(LE1, DEPT_20, REPORTS_REPORT))).as("indirect report").isFalse();
        assertThat(scope.permits(row(LE1, DEPT_20, STRANGER))).isFalse();
    }

    @Test
    @DisplayName("RULE-349 own unit covers the unit only; own unit and below covers its subtree")
    void orgUnitScopes() {
        AccessScope unitOnly = AccessScopeCalculator.calculate(PERSON, List.of(role(AccessScopeKind.OWN_ORG_UNIT)));
        AccessScope subtree = AccessScopeCalculator.calculate(PERSON, List.of(role(AccessScopeKind.OWN_ORG_UNIT_SUBTREE)));

        assertThat(unitOnly.permits(row(LE1, DEPT_15, STRANGER))).isTrue();
        assertThat(unitOnly.permits(row(LE1, DEPT_16, STRANGER))).as("child unit").isFalse();
        assertThat(subtree.permits(row(LE1, DEPT_15, STRANGER))).isTrue();
        assertThat(subtree.permits(row(LE1, DEPT_16, STRANGER))).isTrue();
        assertThat(subtree.permits(row(LE1, DEPT_20, STRANGER))).as("sibling branch").isFalse();
    }

    @Test
    @DisplayName("RULE-349 custom units count only for the custom-units scope")
    void customUnitsOnlyForCustomScope() {
        AccessScope custom = AccessScopeCalculator.calculate(PERSON,
                List.of(new RoleScope(AccessScopeKind.CUSTOM_ORG_UNITS, true, Set.of(DEPT_20))));
        AccessScope ignored = AccessScopeCalculator.calculate(PERSON, List.of(new RoleScope(AccessScopeKind.SELF, true, Set.of(DEPT_20))));

        assertThat(custom.permits(row(LE1, DEPT_20, STRANGER))).isTrue();
        assertThat(ignored.permits(row(LE1, DEPT_20, STRANGER))).as("custom units of a SELF role are ignored").isFalse();
    }

    @Test
    @DisplayName("RULE-349 the scope of several roles is the union")
    void unionOverRoles() {
        AccessScope scope = AccessScopeCalculator.calculate(PERSON,
                List.of(role(AccessScopeKind.OWN_ORG_UNIT_SUBTREE), role(AccessScopeKind.SELF_AND_DIRECT_REPORTS)));

        assertThat(scope.permits(row(LE1, DEPT_16, STRANGER))).isTrue();
        assertThat(scope.permits(row(LE1, DEPT_20, REPORT))).isTrue();
        assertThat(scope.permits(row(LE1, DEPT_20, STRANGER))).isFalse();
    }

    // RULE-459
    @Test
    @DisplayName("C1a-09 the legal entity is a dimension of its own: the same unit in another legal entity is outside the scope")
    void legalEntityDimension() {
        AccessScope scope = AccessScopeCalculator.calculate(PERSON, List.of(role(AccessScopeKind.OWN_ORG_UNIT_SUBTREE)));

        assertThat(scope.permits(row(LE1, DEPT_15, STRANGER))).isTrue();
        assertThat(scope.permits(row(LE2, DEPT_15, STRANGER))).isFalse();
    }

    // RULE-459
    @Test
    @DisplayName("C1a-09 a grant with a project role covers only rows with that project role")
    void projectRoleDimension() {
        ProjectRole manager = new ProjectRole("project-manager");
        AccessScope scope = new AccessScope(false, Set.of(new AccessGrant(LE1, DEPT_15, true, manager)), Set.of());
        List<OrgUnitId> path = List.of(DEPT_16, DEPT_15, ROOT);

        assertThat(scope.permits(new AccessTarget(LE1, path, manager, STRANGER))).isTrue();
        assertThat(scope.permits(new AccessTarget(LE1, path, new ProjectRole("member"), STRANGER))).isFalse();
        assertThat(scope.permits(new AccessTarget(LE1, path, null, STRANGER))).isFalse();
    }

    @Test
    @DisplayName("RULE-350 the scope is a plain value passed to the repository: records are immutable copies")
    void scopeIsAnImmutableValue() {
        AccessScope scope = AccessScopeCalculator.calculate(PERSON, List.of(role(AccessScopeKind.SELF)));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> scope.ownerIds().add(STRANGER)).isInstanceOf(UnsupportedOperationException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> scope.grants().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("#730 A3 a person without a home unit reaches nothing through own-unit roles, and the other roles still count")
    void noHomeUnitIsAnEmptyReachNotAFailure() {
        var own = AccessScopeCalculator.orgUnitReach(null, List.of(role(AccessScopeKind.OWN_ORG_UNIT), role(AccessScopeKind.OWN_ORG_UNIT_SUBTREE)));
        assertThat(own.unrestricted()).isFalse();
        assertThat(own.units()).isEmpty();
        assertThat(own.subtrees()).isEmpty();

        var mixed = AccessScopeCalculator.orgUnitReach(null, List.of(role(AccessScopeKind.OWN_ORG_UNIT), new RoleScope(AccessScopeKind.CUSTOM_ORG_UNITS, true, Set.of(DEPT_20))));
        assertThat(mixed.units()).containsExactly(DEPT_20);
    }

    @Test
    @DisplayName("#730 A1 a custom org unit scope reaches its units, and the units marked with descendants as subtrees")
    void customUnitsAreReachedAsUnitsAndSubtrees() {
        var reach = AccessScopeCalculator.orgUnitReach(DEPT_15, List.of(new RoleScope(AccessScopeKind.CUSTOM_ORG_UNITS, true, Set.of(DEPT_20), Set.of(DEPT_16))));
        assertThat(reach.units()).containsExactly(DEPT_20);
        assertThat(reach.subtrees()).containsExactly(DEPT_16);
        AccessScope scope = AccessScopeCalculator.calculate(PERSON, List.of(new RoleScope(AccessScopeKind.CUSTOM_ORG_UNITS, true, Set.of(DEPT_20), Set.of(DEPT_16))));
        assertThat(scope.grants()).extracting(AccessGrant::orgUnitId, AccessGrant::withDescendants).containsExactlyInAnyOrder(
                org.assertj.core.groups.Tuple.tuple(DEPT_20, false), org.assertj.core.groups.Tuple.tuple(DEPT_16, true));
    }
}
