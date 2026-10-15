package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.platform.integration.api.SourceOrgSnapshot;

import static com.forcitis.one.organization.workforce.domain.Fixtures.apply;
import static com.forcitis.one.organization.workforce.domain.Fixtures.first;
import static com.forcitis.one.organization.workforce.domain.Fixtures.person;
import static com.forcitis.one.organization.workforce.domain.Fixtures.post;
import static com.forcitis.one.organization.workforce.domain.Fixtures.source;
import static com.forcitis.one.organization.workforce.domain.Fixtures.tree;
import static com.forcitis.one.organization.workforce.domain.Fixtures.unit;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrgReconciliationTest {
    private static SourceOrgSnapshot sourceSnapshot() {
        return tree(person(1, 2, 100L, null), person(2, 3, 101L, 1L), person(3, 3, 101L, 1L), person(4, 2, 100L, null, null, null));
    }

    @Test
    @DisplayName("组织零差异: a snapshot synced from the source reconciles with no difference in counts or reporting relations")
    void freshSnapshotHasZeroDifference() {
        OrgSnapshot synced = apply(OrgSnapshot.empty(), first(sourceSnapshot()));

        OrgReconciliationReport report = OrgReconciliation.compare(sourceSnapshot(), synced);

        assertThat(report.isZeroDifference()).isTrue();
        assertThat(report.sourceEmployedCount()).isEqualTo(3).isEqualTo(report.snapshotEmployedCount());
        assertThat(report.sourceOrgUnitCount()).isEqualTo(3).isEqualTo(report.snapshotOrgUnitCount());
    }

    @Test
    @DisplayName("组织零差异: revoked units in the snapshot are not part of what the source returns")
    void revokedUnitsAreIgnored() {
        OrgSnapshot synced = apply(OrgSnapshot.empty(), first(sourceSnapshot()));
        SourceOrgSnapshot smaller = source(List.of(unit(1, null), unit(2, 1L)), List.of(), List.of(post(100), post(101)),
                List.of(person(1, 2, 100L, null)));
        OrgSnapshot afterRevoke = apply(synced, SnapshotReconciler.reconcile(synced, smaller));

        assertThat(OrgReconciliation.compare(smaller, afterRevoke).isZeroDifference()).isTrue();
    }

    @Test
    @DisplayName("组织零差异: drift is found - a changed direct leader, a changed parent, a missing and an unexpected employee")
    void driftIsReported() {
        OrgSnapshot synced = apply(OrgSnapshot.empty(), first(sourceSnapshot()));
        StaffEmployee two = Fixtures.staff(synced, 2);
        StaffEmployee drifted = new StaffEmployee(two.employee(), new Assignment(two.employee().employeeId(), two.assignment().orgUnitId(),
                two.assignment().positionId(), Fixtures.user(3)));
        StaffEmployee leaver = Fixtures.staff(synced, 4);
        StaffEmployee stillEmployed = new StaffEmployee(leaver.employee().withEmploymentStatus(EmploymentStatus.EMPLOYED), leaver.assignment());
        List<OrgUnit> units = synced.orgUnits().stream()
                .map(u -> u.orgUnitId().value() == 3 ? new OrgUnit(u.orgUnitId(), u.code(), u.name(), Fixtures.unitId(1),
                        u.sortOrder(), u.status(), u.headEmployeeId(), u.regionalReceivablesManagerId(), u.regionCategory(),
                        u.deliveryUnit(), u.shortName()) : u).toList();
        List<StaffEmployee> staff = synced.staff().stream()
                .filter(s -> s.employeeId().value() != 3)
                .map(s -> s.employeeId().value() == 2 ? drifted : s.employeeId().value() == 4 ? stillEmployed : s).toList();
        OrgSnapshot stored = new OrgSnapshot(units, synced.positions(), staff);

        OrgReconciliationReport report = OrgReconciliation.compare(sourceSnapshot(), stored);

        assertThat(report.isZeroDifference()).isFalse();
        assertThat(report.reportingLineMismatches()).containsExactly("employee:2");
        assertThat(report.parentMismatches()).containsExactly("org-unit:3");
        assertThat(report.missingInSnapshot()).containsExactly("employee:3");
        assertThat(report.unexpectedInSnapshot()).singleElement().asString().startsWith("employee:4");
        assertThat(report.sourceEmployedCount()).isEqualTo(3);
        assertThat(report.snapshotEmployedCount()).isEqualTo(3);
    }
}
