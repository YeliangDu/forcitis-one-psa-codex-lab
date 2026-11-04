package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.platform.integration.api.SourceOrgSnapshot;
import com.forcitis.one.platform.integration.api.SourcePerson;
import com.forcitis.one.platform.integration.api.SourceRecordState;

import static com.forcitis.one.organization.workforce.domain.Fixtures.apply;
import static com.forcitis.one.organization.workforce.domain.Fixtures.employed;
import static com.forcitis.one.organization.workforce.domain.Fixtures.first;
import static com.forcitis.one.organization.workforce.domain.Fixtures.issues;
import static com.forcitis.one.organization.workforce.domain.Fixtures.person;
import static com.forcitis.one.organization.workforce.domain.Fixtures.post;
import static com.forcitis.one.organization.workforce.domain.Fixtures.source;
import static com.forcitis.one.organization.workforce.domain.Fixtures.staff;
import static com.forcitis.one.organization.workforce.domain.Fixtures.tree;
import static com.forcitis.one.organization.workforce.domain.Fixtures.unit;
import static com.forcitis.one.organization.workforce.domain.Fixtures.sourcePerson;
import static com.forcitis.one.organization.workforce.domain.Fixtures.user;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.forcitis.one.fixture.doubles.TestOrgIds;

class EmployeeSyncRulesTest {
    @Test
    @DisplayName("RULE-362 a new person is registered with the source id and the WeCom user id from the first sync")
    void newPersonIsCreatedWithWecomUserId() {
        Reconciliation changes = first(tree(person(2001, 2, 100L, null, "13900000000", "wx-2001")));

        StaffEmployee created = staff(apply(OrgSnapshot.empty(), changes), 2001);

        assertThat(created.employee().wecomUserId()).isEqualTo(new WecomUserId("wx-2001"));
        assertThat(created.employee().employmentStatus()).isEqualTo(EmploymentStatus.EMPLOYED);
        assertThat(created.assignment().orgUnitId()).isEqualTo(Fixtures.unitId(2));
        assertThat(changes.changedEmployees()).containsExactly(user(2001));
        assertThat(changes.diffs()).anyMatch(d -> d.changeKind() == ChangeKind.CREATED && d.subjectKey().equals("employee:" + Fixtures.user(2001).value()));
    }

    @Test
    @DisplayName("D-1a-06 a person whose internal flag is NULL is synced as employed and listed as a data issue for the organisation data administrator")
    void nullInternalFlagIsSyncedAndReported() {
        SourcePerson base = person(7, 2, 100L, null);
        SourcePerson unflagged = new SourcePerson(base.personId(), base.employeeCode(), base.displayName(), base.unitId(), base.positionId(),
                base.supervisorId(), base.loginName(), base.email(), base.gender(), base.wecomUserId(), base.feeEarner(), true);

        Reconciliation changes = first(tree(unflagged));

        assertThat(employed(staff(apply(OrgSnapshot.empty(), changes), 7))).isTrue();
        assertThat(issues(changes, IssueKind.NULL_FLAG)).singleElement().satisfies(d -> assertThat(d.subjectKey()).isEqualTo("employee:" + Fixtures.user(7).value()));
    }

    @Test
    @DisplayName("RULE-363 one login test: null, empty and blank login names all mean separated")
    void loginNameTestIsUnified() {
        Reconciliation changes = first(tree(person(1, 2, 100L, null, null, null), person(2, 2, 100L, null, "", null),
                person(3, 2, 100L, null, "   ", null), person(4, 2, 100L, null, "13900000004", null)));
        OrgSnapshot snapshot = apply(OrgSnapshot.empty(), changes);

        assertThat(employed(staff(snapshot, 1))).isFalse();
        assertThat(employed(staff(snapshot, 2))).isFalse();
        assertThat(employed(staff(snapshot, 3))).isFalse();
        assertThat(employed(staff(snapshot, 4))).isTrue();
    }

    @Test
    @DisplayName("RULE-363 resignation: the employee is separated, announced once, and HR fields keep updating afterwards")
    void resignationKeepsHistoryUpdating() {
        OrgSnapshot v1 = apply(OrgSnapshot.empty(), first(tree(person(2001, 2, 100L, null))));

        Reconciliation leaves = SnapshotReconciler.reconcile(v1, tree(person(2001, 2, 100L, null, null, "wx-2001")));
        OrgSnapshot v2 = apply(v1, leaves);
        assertThat(employed(staff(v2, 2001))).isFalse();
        assertThat(leaves.separatedEmployees()).containsExactly(user(2001));
        assertThat(leaves.diffs()).anyMatch(d -> d.changeKind() == ChangeKind.SEPARATED);

        Reconciliation moves = SnapshotReconciler.reconcile(v2, tree(person(2001, 3, 101L, null, null, "wx-2001")));
        OrgSnapshot v3 = apply(v2, moves);
        assertThat(staff(v3, 2001).assignment().orgUnitId()).isEqualTo(Fixtures.unitId(3));
        assertThat(moves.separatedEmployees()).isEmpty();
        assertThat(moves.changedEmployees()).containsExactly(user(2001));
    }

    @Test
    @DisplayName("RULE-364 rehire: the source gives a login name again, the employee is employed again")
    void rehireRestoresTheEmployee() {
        OrgSnapshot v1 = apply(OrgSnapshot.empty(), first(tree(person(2001, 2, 100L, null, null, "wx-2001"))));
        assertThat(employed(staff(v1, 2001))).isFalse();

        Reconciliation rehire = SnapshotReconciler.reconcile(v1, tree(person(2001, 2, 100L, null, "13900000000", "wx-2001")));

        assertThat(employed(staff(apply(v1, rehire), 2001))).isTrue();
        assertThat(rehire.diffs()).anyMatch(d -> d.changeKind() == ChangeKind.REHIRED);
        assertThat(rehire.changedEmployees()).containsExactly(user(2001));
        assertThat(rehire.separatedEmployees()).isEmpty();
    }

    @Test
    @DisplayName("RULE-365 source-authoritative columns are synced by value, an emptied leader and e-mail are written as empty")
    void explicitNullsAreWritten() {
        SourcePerson boss = person(1000, 2, 100L, null);
        SourcePerson withSupervisor = person(2001, 2, 100L, 1000L);
        OrgSnapshot v1 = apply(OrgSnapshot.empty(), first(tree(boss, withSupervisor)));
        assertThat(staff(v1, 2001).assignment().reportsToEmployeeId()).isEqualTo(user(1000));

        SourcePerson cleared = new SourcePerson(sourcePerson(2001), "E2001", "Person 2001", withSupervisor.unitId(), withSupervisor.positionId(), null,
                withSupervisor.loginName(), null, withSupervisor.gender(), withSupervisor.wecomUserId(), false);
        OrgSnapshot v2 = apply(v1, SnapshotReconciler.reconcile(v1, tree(boss, cleared)));

        assertThat(staff(v2, 2001).assignment().reportsToEmployeeId()).isNull();
        assertThat(staff(v2, 2001).employee().email()).isNull();
    }

    @Test
    @DisplayName("RULE-365 a row without a known org unit or position is isolated and reported; the batch continues")
    void rowsWithoutOrgUnitOrPositionAreIsolated() {
        Reconciliation changes = first(tree(person(1, 2, 100L, null), person(2, 99, 100L, null), person(3, 2, null, null),
                person(4, 2, 555L, null)));
        OrgSnapshot snapshot = apply(OrgSnapshot.empty(), changes);

        assertThat(snapshot.staffById()).containsOnlyKeys(user(1));
        assertThat(issues(changes)).containsExactlyInAnyOrder(IssueKind.ISOLATED_MISSING_ORG_UNIT,
                IssueKind.ISOLATED_MISSING_POSITION, IssueKind.ISOLATED_MISSING_POSITION);
        assertThat(changes.stats().isolatedRowCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("RULE-365 an isolated row keeps its stored state and is not treated as a leaver")
    void isolatedRowIsNotSeparated() {
        OrgSnapshot v1 = apply(OrgSnapshot.empty(), first(tree(person(2001, 2, 100L, null))));

        Reconciliation next = SnapshotReconciler.reconcile(v1, tree(person(2001, 2, null, null)));

        assertThat(next.staff()).isEmpty();
        assertThat(next.separatedEmployees()).isEmpty();
        assertThat(employed(staff(apply(v1, next), 2001))).isTrue();
    }

    @Test
    @DisplayName("RULE-366 an employee who drops out of the source result set is a leaver candidate and is separated once")
    void missingPersonBecomesSeparated() {
        OrgSnapshot v1 = apply(OrgSnapshot.empty(), first(tree(person(1, 2, 100L, null), person(2001, 2, 100L, null))));

        Reconciliation gone = SnapshotReconciler.reconcile(v1, tree(person(1, 2, 100L, null)));
        OrgSnapshot v2 = apply(v1, gone);

        assertThat(employed(staff(v2, 2001))).isFalse();
        assertThat(gone.separatedEmployees()).containsExactly(user(2001));

        Reconciliation again = SnapshotReconciler.reconcile(v2, tree(person(1, 2, 100L, null)));
        assertThat(again.hasChanges()).isFalse();
        assertThat(again.separatedEmployees()).isEmpty();
    }

    @Test
    @DisplayName("RULE-367 positions: new ones are added, disabled and deleted ones follow the source, ones no longer returned are deleted")
    void positionStatusFollowsTheSource() {
        OrgSnapshot v1 = apply(OrgSnapshot.empty(), first(tree()));
        assertThat(v1.positionsById().get(TestOrgIds.position(100)).status()).isEqualTo(PositionStatus.ACTIVE);

        SourceOrgSnapshot next = source(List.of(unit(1, null), unit(2, 1L), unit(3, 2L)), List.of(),
                List.of(post(100, SourceRecordState.DISABLED), post(102)), List.of());
        Reconciliation changes = SnapshotReconciler.reconcile(v1, next);
        OrgSnapshot v2 = apply(v1, changes);

        assertThat(v2.positionsById().get(TestOrgIds.position(100)).status()).isEqualTo(PositionStatus.DISABLED);
        assertThat(v2.positionsById().get(TestOrgIds.position(101)).status()).isEqualTo(PositionStatus.DELETED);
        assertThat(v2.positionsById().get(TestOrgIds.position(102)).status()).isEqualTo(PositionStatus.ACTIVE);
        assertThat(changes.stats().positionChangeCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("RULE-372 a leader must exist, be employed, not be the person and not close a loop; failures are reported and not written")
    void leadersAreValidated() {
        SourcePerson self = person(1, 2, 100L, 1L);
        SourcePerson unknownSupervisor = person(2, 2, 100L, 99L);
        SourcePerson leaverSupervisor = person(3, 2, 100L, 4L);
        SourcePerson leaver = person(4, 2, 100L, null, null, null);
        SourcePerson loopA = person(5, 2, 100L, 6L);
        SourcePerson loopB = person(6, 2, 100L, 5L);
        SourcePerson fine = person(7, 2, 100L, 8L);
        SourcePerson boss = person(8, 2, 100L, null);

        Reconciliation changes = first(tree(self, unknownSupervisor, leaverSupervisor, leaver, loopA, loopB, fine, boss));
        OrgSnapshot snapshot = apply(OrgSnapshot.empty(), changes);

        assertThat(staff(snapshot, 1).assignment().reportsToEmployeeId()).isNull();
        assertThat(staff(snapshot, 2).assignment().reportsToEmployeeId()).isNull();
        assertThat(staff(snapshot, 3).assignment().reportsToEmployeeId()).isNull();
        assertThat(staff(snapshot, 5).assignment().reportsToEmployeeId()).isNull();
        assertThat(staff(snapshot, 6).assignment().reportsToEmployeeId()).isNull();
        assertThat(staff(snapshot, 7).assignment().reportsToEmployeeId()).isEqualTo(user(8));
        assertThat(issues(changes)).containsExactlyInAnyOrder(IssueKind.SUPERVISOR_IS_SELF, IssueKind.SUPERVISOR_NOT_FOUND,
                IssueKind.SUPERVISOR_SEPARATED, IssueKind.SUPERVISOR_CYCLE, IssueKind.SUPERVISOR_CYCLE);
    }

    private static SourcePerson withCode(SourcePerson base, String code) {
        return new SourcePerson(base.personId(), code, base.displayName(), base.unitId(), base.positionId(), base.supervisorId(),
                base.loginName(), base.email(), base.gender(), base.wecomUserId(), base.feeEarner());
    }

    @Test
    @DisplayName("W2 PR-B the employee code is unique: of two new people with one code the smaller source id is built, the other is isolated and reported")
    void secondNewPersonWithTheSameCodeIsIsolated() {
        Reconciliation changes = first(tree(withCode(person(2, 2, 100L, null, "13900000002", null), "E-SAME"),
                withCode(person(1, 2, 100L, null, "13900000001", null), "E-SAME")));
        OrgSnapshot snapshot = apply(OrgSnapshot.empty(), changes);

        assertThat(snapshot.staffById()).containsOnlyKeys(user(1));
        assertThat(issues(changes, IssueKind.DUPLICATE_EMPLOYEE_CODE)).hasSize(1);
        assertThat(changes.diffs()).anyMatch(d -> d.subjectKey().equals("employee:" + user(2).value()) && d.changeKind() == ChangeKind.ISSUE
                && d.detail().contains(user(1).value().toString()) && !d.detail().contains("E-SAME"));
        assertThat(changes.stats().isolatedRowCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("W2 PR-B an existing employee keeps the code against a new person with a smaller source id, and a repeated sync gives the same result")
    void existingHolderWinsAndRerunIsStable() {
        OrgSnapshot existing = apply(OrgSnapshot.empty(), first(tree(withCode(person(5, 2, 100L, null, "13900000005", null), "E-SAME"))));
        SourceOrgSnapshot source = tree(withCode(person(1, 2, 100L, null, "13900000001", null), "E-SAME"),
                withCode(person(5, 2, 100L, null, "13900000005", null), "E-SAME"));

        Reconciliation changes = SnapshotReconciler.reconcile(existing, source);
        OrgSnapshot after = apply(existing, changes);

        assertThat(after.staffById()).containsOnlyKeys(user(5));
        assertThat(issues(changes, IssueKind.DUPLICATE_EMPLOYEE_CODE)).hasSize(1);
        Reconciliation again = SnapshotReconciler.reconcile(after, source);
        assertThat(issues(again, IssueKind.DUPLICATE_EMPLOYEE_CODE)).hasSize(1);
        assertThat(again.changedEmployees()).isEmpty();
    }

    @Test
    @DisplayName("ADR-0078 an issued employee code is kept when the source carries another one, and the change is reported")
    void anIssuedCodeIsNotRewrittenBySync() {
        OrgSnapshot existing = apply(OrgSnapshot.empty(), first(tree(withCode(person(5, 2, 100L, null, "13900000005", null), "E-OLD"))));

        Reconciliation changes = SnapshotReconciler.reconcile(existing, tree(withCode(person(5, 2, 100L, null, "13900000005", null), "E-NEW")));
        OrgSnapshot after = apply(existing, changes);

        assertThat(after.staffById().get(user(5)).employee().employeeCode()).isEqualTo("E-OLD");
        assertThat(issues(changes, IssueKind.EMPLOYEE_CODE_CHANGE_IGNORED)).hasSize(1);
        assertThat(changes.stats().isolatedRowCount()).as("the person is kept, not isolated").isZero();
        assertThat(changes.diffs()).filteredOn(d -> d.issueKind() == IssueKind.EMPLOYEE_CODE_CHANGE_IGNORED)
                .allMatch(d -> !d.detail().contains("E-OLD") && !d.detail().contains("E-NEW"));
        assertThat(changes.changedEmployees()).isEmpty();

        Reconciliation again = SnapshotReconciler.reconcile(after, tree(withCode(person(5, 2, 100L, null, "13900000005", null), "E-NEW")));
        assertThat(issues(again, IssueKind.EMPLOYEE_CODE_CHANGE_IGNORED)).as("each sync reports it again, no dedup across runs").hasSize(1);
    }

    @Test
    @DisplayName("ADR-0078 a new person cannot take the code an existing employee was issued, even when the source gave that employee another code")
    void aNewPersonCannotTakeAnIssuedCode() {
        OrgSnapshot existing = apply(OrgSnapshot.empty(), first(tree(withCode(person(5, 2, 100L, null, "13900000005", null), "E-OLD"))));

        Reconciliation changes = SnapshotReconciler.reconcile(existing, tree(withCode(person(5, 2, 100L, null, "13900000005", null), "E-NEW"),
                withCode(person(1, 2, 100L, null, "13900000001", null), "E-OLD")));
        OrgSnapshot after = apply(existing, changes);

        assertThat(after.staffById()).containsOnlyKeys(user(5));
        assertThat(issues(changes, IssueKind.DUPLICATE_EMPLOYEE_CODE)).hasSize(1);
    }

    @Test
    @DisplayName("RULE-362 one WeCom user id identifies one employed person: duplicates are stored for nobody and reported")
    void duplicateWecomUserIdIsNotStored() {
        Reconciliation changes = first(tree(person(1, 2, 100L, null, "13900000001", "wx-same"),
                person(2, 2, 100L, null, "13900000002", "wx-same"), person(3, 2, 100L, null, null, "wx-same")));
        OrgSnapshot snapshot = apply(OrgSnapshot.empty(), changes);

        assertThat(staff(snapshot, 1).employee().wecomUserId()).isNull();
        assertThat(staff(snapshot, 2).employee().wecomUserId()).isNull();
        assertThat(staff(snapshot, 3).employee().wecomUserId()).isEqualTo(new WecomUserId("wx-same"));
        assertThat(issues(changes, IssueKind.DUPLICATE_WECOM_USER_ID)).hasSize(2);
    }

    @Test
    @DisplayName("RULE-374 the data-quality count of employed people without a WeCom user id is output on every run")
    void employedWithoutWecomIsCounted() {
        Reconciliation changes = first(tree(person(1, 2, 100L, null, "13900000001", null), person(2, 2, 100L, null),
                person(3, 2, 100L, null, null, null)));

        assertThat(changes.stats().employedWithoutWecomCount()).isEqualTo(1);
        assertThat(changes.stats().incomingEmployedCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("RULE-373 the change event and the diff report come from the same synced-field list; no change, no event content")
    void eventContentFollowsTheSyncedFields() {
        OrgSnapshot v1 = apply(OrgSnapshot.empty(), first(tree(person(2001, 2, 100L, null), person(2002, 2, 100L, null))));
        assertThat(SnapshotReconciler.reconcile(v1, tree(person(2001, 2, 100L, null), person(2002, 2, 100L, null))).change().hasContent())
                .isFalse();

        Reconciliation moved = SnapshotReconciler.reconcile(v1, tree(person(2001, 3, 100L, null), person(2002, 2, 100L, null)));

        assertThat(moved.changedEmployees()).containsExactly(user(2001));
        assertThat(moved.diffs()).filteredOn(d -> d.subjectKey().equals("employee:" + Fixtures.user(2001).value()))
                .singleElement().satisfies(d -> assertThat(d.changedFields()).containsExactly("orgUnitId"));
        assertThat(SyncedFields.STAFF).extracting(SyncedFields.Field::name)
                .contains("orgUnitId", "positionId", "reportsToEmployeeId", "employmentStatus", "wecomUserId");
    }

    @Test
    @DisplayName("RULE-373 a unit's head change reaches the event; revocation is announced once and never repeated")
    void orgUnitEventsAreNotRepeated() {
        OrgSnapshot v1 = apply(OrgSnapshot.empty(), first(tree(person(801, 2, 100L, null))));
        SourceOrgSnapshot withHead = source(List.of(unit(1, null), unit(2, 1L), unit(3, 2L)),
                List.of(Fixtures.head(2, 801)), List.of(post(100), post(101)), List.of(person(801, 2, 100L, null)));

        Reconciliation headChange = SnapshotReconciler.reconcile(v1, withHead);
        assertThat(headChange.changedOrgUnits()).containsExactly(Fixtures.unitId(2));

        OrgSnapshot v2 = apply(v1, headChange);
        SourceOrgSnapshot gone = source(List.of(unit(1, null), unit(2, 1L)), List.of(Fixtures.head(2, 801)),
                List.of(post(100), post(101)), List.of(person(801, 2, 100L, null)));
        Reconciliation revoke = SnapshotReconciler.reconcile(v2, gone);
        assertThat(revoke.changedOrgUnits()).containsExactly(Fixtures.unitId(3));

        Reconciliation repeat = SnapshotReconciler.reconcile(apply(v2, revoke), gone);
        assertThat(repeat.changedOrgUnits()).isEmpty();
        assertThat(repeat.change().hasContent()).isFalse();
    }
}
