package com.forcitis.one.organization.workforce.domain;

import static com.forcitis.one.organization.workforce.domain.Fixtures.ROOT;
import static com.forcitis.one.organization.workforce.domain.Fixtures.apply;
import static com.forcitis.one.organization.workforce.domain.Fixtures.first;
import static com.forcitis.one.organization.workforce.domain.Fixtures.member;
import static com.forcitis.one.organization.workforce.domain.Fixtures.post;
import static com.forcitis.one.organization.workforce.domain.Fixtures.source;
import static com.forcitis.one.organization.workforce.domain.Fixtures.tree;
import static com.forcitis.one.organization.workforce.domain.Fixtures.unit;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Reconciliation queries of the exit criterion "组织零差异": employed people, org units, reporting relations. */
class OrgReconciliationTest {

    private static SourceSnapshot a8() {
        return tree(member(1, 2, 100L, null), member(2, 3, 101L, 1L), member(3, 3, 101L, 1L), member(4, 2, 100L, null, null, null));
    }

    @Test
    @DisplayName("组织零差异: a snapshot synced from A8 reconciles with no difference in counts or reporting relations")
    void freshSnapshotHasZeroDifference() {
        OrgSnapshot synced = apply(OrgSnapshot.empty(), first(a8()));

        OrgReconciliationReport report = OrgReconciliation.compare(a8(), synced);

        assertThat(report.isZeroDifference()).isTrue();
        assertThat(report.sourceEmployedCount()).isEqualTo(3).isEqualTo(report.snapshotEmployedCount());
        assertThat(report.sourceOrgUnitCount()).isEqualTo(3).isEqualTo(report.snapshotOrgUnitCount());
    }

    @Test
    @DisplayName("组织零差异: revoked units in the snapshot are not part of what A8 returns")
    void revokedUnitsAreIgnored() {
        OrgSnapshot synced = apply(OrgSnapshot.empty(), first(a8()));
        SourceSnapshot smaller = source(List.of(unit(1, ROOT), unit(2, ROOT + "0001")), List.of(), List.of(post(100), post(101)),
                List.of(member(1, 2, 100L, null)));
        OrgSnapshot afterRevoke = apply(synced, SnapshotReconciler.reconcile(synced, smaller));

        assertThat(OrgReconciliation.compare(smaller, afterRevoke).isZeroDifference()).isTrue();
    }

    @Test
    @DisplayName("组织零差异: drift is found - a changed direct leader, a changed parent, a missing and an unexpected employee")
    void driftIsReported() {
        OrgSnapshot synced = apply(OrgSnapshot.empty(), first(a8()));
        StaffMember two = Fixtures.staff(synced, 2);
        StaffMember drifted = new StaffMember(two.employee(), new Assignment(two.employee().userId(), two.assignment().orgUnitId(),
                two.assignment().positionId(), Fixtures.user(3)));
        StaffMember leaver = Fixtures.staff(synced, 4);
        StaffMember stillEmployed = new StaffMember(leaver.employee().withEmploymentStatus(EmploymentStatus.EMPLOYED), leaver.assignment());
        List<OrgUnit> units = synced.orgUnits().stream()
                .map(u -> u.orgUnitId().value() == 3 ? new OrgUnit(u.orgUnitId(), u.code(), u.name(), Fixtures.unitId(1), u.path(),
                        u.sortOrder(), u.status(), u.headEmployeeId(), u.regionalReceivablesManagerId(), u.regionCategory(),
                        u.deliveryUnit(), u.shortName()) : u).toList();
        List<StaffMember> staff = synced.staff().stream()
                .filter(s -> s.userId().value() != 3)
                .map(s -> s.userId().value() == 2 ? drifted : s.userId().value() == 4 ? stillEmployed : s).toList();
        OrgSnapshot stored = new OrgSnapshot(units, synced.positions(), staff);

        OrgReconciliationReport report = OrgReconciliation.compare(a8(), stored);

        assertThat(report.isZeroDifference()).isFalse();
        assertThat(report.reportingLineMismatches()).containsExactly("employee:2");
        assertThat(report.parentMismatches()).containsExactly("org-unit:3");
        assertThat(report.missingInSnapshot()).containsExactly("employee:3");
        assertThat(report.unexpectedInSnapshot()).singleElement().asString().startsWith("employee:4");
        assertThat(report.sourceEmployedCount()).isEqualTo(3);
        assertThat(report.snapshotEmployedCount()).isEqualTo(3);
    }
}
