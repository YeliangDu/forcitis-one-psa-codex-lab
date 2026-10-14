package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.platform.integration.api.SourceOrgSnapshot;
import com.forcitis.one.platform.integration.api.SourceOrgUnit;
import com.forcitis.one.platform.integration.api.SourceRecordState;
import static com.forcitis.one.organization.workforce.domain.Fixtures.sourceUnit;

import static com.forcitis.one.organization.workforce.domain.Fixtures.apply;
import static com.forcitis.one.organization.workforce.domain.Fixtures.first;
import static com.forcitis.one.organization.workforce.domain.Fixtures.head;
import static com.forcitis.one.organization.workforce.domain.Fixtures.issues;
import static com.forcitis.one.organization.workforce.domain.Fixtures.manager;
import static com.forcitis.one.organization.workforce.domain.Fixtures.person;
import static com.forcitis.one.organization.workforce.domain.Fixtures.orgUnit;
import static com.forcitis.one.organization.workforce.domain.Fixtures.post;
import static com.forcitis.one.organization.workforce.domain.Fixtures.source;
import static com.forcitis.one.organization.workforce.domain.Fixtures.tree;
import static com.forcitis.one.organization.workforce.domain.Fixtures.unit;
import static com.forcitis.one.organization.workforce.domain.Fixtures.unitId;
import static com.forcitis.one.organization.workforce.domain.Fixtures.user;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.organization.workforce.api.OrgUnitId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrgUnitSyncRulesTest {
    @Test
    @DisplayName("RULE-354 the parent is the one the source names (the adapter has resolved it); the root has none")
    void parentComesFromTheSource() {
        OrgSnapshot snapshot = apply(OrgSnapshot.empty(), first(tree()));

        assertThat(orgUnit(snapshot, 1).parentOrgUnitId()).isNull();
        assertThat(orgUnit(snapshot, 2).parentOrgUnitId()).isEqualTo(unitId(1));
        assertThat(orgUnit(snapshot, 3).parentOrgUnitId()).isEqualTo(unitId(2));
    }

    @Test
    @DisplayName("RULE-354 units are written parents first, whatever order the source lists them in")
    void parentsComeFirst() {
        SourceOrgSnapshot shuffled = source(List.of(unit(3, 2L), unit(2, 1L), unit(1, null)), List.of(), List.of(post(100)), List.of());

        Reconciliation changes = first(shuffled);

        assertThat(changes.orgUnits()).extracting(u -> u.orgUnitId().value()).containsExactly(1L, 2L, 3L);
    }

    @Test
    @DisplayName("RULE-354 when the source reports that two units share a place, the stewards are told")
    void sharedPlaceIsReported() {
        SourceOrgUnit sharing = new SourceOrgUnit(sourceUnit(3), "C3", "Unit 3", sourceUnit(1), 3, SourceRecordState.ACTIVE, List.of(), null, null,
                false, sourceUnit(2));
        SourceOrgSnapshot shared = source(List.of(unit(1, null), unit(2, 1L, SourceRecordState.DISABLED), sharing, unit(4, 3L)), List.of(),
                List.of(post(100)), List.of());

        Reconciliation changes = first(shared);
        OrgSnapshot snapshot = apply(OrgSnapshot.empty(), changes);

        assertThat(orgUnit(snapshot, 4).parentOrgUnitId()).isEqualTo(unitId(3));
        assertThat(issues(changes)).contains(IssueKind.DUPLICATE_UNIT);
    }

    @Test
    @DisplayName("RULE-355 a disabled unit is DISABLED, a unit the source no longer returns is REVOKED: two different states")
    void disabledAndRevokedAreSeparate() {
        OrgSnapshot before = apply(OrgSnapshot.empty(), first(tree()));
        SourceOrgSnapshot next = source(List.of(unit(1, null), unit(2, 1L, SourceRecordState.DISABLED)), List.of(),
                List.of(post(100), post(101)), List.of());

        Reconciliation changes = SnapshotReconciler.reconcile(before, next);
        OrgSnapshot after = apply(before, changes);

        assertThat(orgUnit(after, 2).status()).isEqualTo(OrgUnitStatus.DISABLED);
        assertThat(orgUnit(after, 3).status()).isEqualTo(OrgUnitStatus.REVOKED);
        assertThat(changes.diffs()).filteredOn(d -> d.changeKind() == ChangeKind.REVOKED).hasSize(1);
    }

    @Test
    @DisplayName("RULE-355 a unit with NULL flags counts as valid and is listed in the exception report")
    void nullFlagsAreValidAndReported() {
        SourceOrgUnit nullFlags = new SourceOrgUnit(sourceUnit(1), "C1", "Unit 1", null, 1, SourceRecordState.ACTIVE,
                List.of("enabled"), null, null, false, null);

        Reconciliation changes = first(source(List.of(nullFlags), List.of(), List.of(post(100)), List.of()));

        assertThat(orgUnit(apply(OrgSnapshot.empty(), changes), 1).status()).isEqualTo(OrgUnitStatus.ACTIVE);
        assertThat(issues(changes)).containsExactly(IssueKind.NULL_FLAG);
    }

    @Test
    @DisplayName("RULE-356 several heads: the smallest id wins, deterministically, and the data steward is told")
    void smallestHeadIdWins() {
        SourceOrgSnapshot snapshot = source(List.of(unit(1, null)), List.of(head(1, 805), head(1, 801)), List.of(post(100)),
                List.of(person(801, 1, 100L, null), person(805, 1, 100L, null)));

        Reconciliation changes = first(snapshot);

        assertThat(orgUnit(apply(OrgSnapshot.empty(), changes), 1).headEmployeeId()).isEqualTo(user(801));
        assertThat(issues(changes)).contains(IssueKind.MULTIPLE_HEADS);
    }

    @Test
    @DisplayName("RULE-356 the regional receivables manager follows the same rule and a removed one becomes empty")
    void managerIsSyncedByValue() {
        SourceOrgSnapshot with = source(List.of(unit(1, null)), List.of(manager(1, 801)), List.of(post(100)),
                List.of(person(801, 1, 100L, null)));
        OrgSnapshot before = apply(OrgSnapshot.empty(), first(with));
        assertThat(orgUnit(before, 1).regionalReceivablesManagerId()).isEqualTo(user(801));

        SourceOrgSnapshot without = source(List.of(unit(1, null)), List.of(), List.of(post(100)), List.of(person(801, 1, 100L, null)));
        OrgSnapshot after = apply(before, SnapshotReconciler.reconcile(before, without));

        assertThat(orgUnit(after, 1).regionalReceivablesManagerId()).isNull();
    }

    @Test
    @DisplayName("RULE-356 a head who is not in the snapshot is stored empty and reported, not left dangling")
    void unknownHeadIsReported() {
        SourceOrgSnapshot snapshot = source(List.of(unit(1, null)), List.of(head(1, 999)), List.of(post(100)), List.of());

        Reconciliation changes = first(snapshot);

        assertThat(orgUnit(apply(OrgSnapshot.empty(), changes), 1).headEmployeeId()).isNull();
        assertThat(issues(changes)).contains(IssueKind.HEAD_NOT_FOUND);
    }

    @Test
    @DisplayName("RULE-357 insert new, update on change, revoke what the source no longer returns, revive when it returns")
    void insertUpdateRevokeRevive() {
        OrgSnapshot v1 = apply(OrgSnapshot.empty(), first(tree()));

        Reconciliation noChange = SnapshotReconciler.reconcile(v1, tree());
        assertThat(noChange.hasChanges()).isFalse();

        SourceOrgSnapshot renamed = source(List.of(unit(1, null), new SourceOrgUnit(sourceUnit(2), "C2", "Renamed", sourceUnit(1), 2,
                SourceRecordState.ACTIVE, List.of(), null, null, false, null)), List.of(), List.of(post(100), post(101)), List.of());
        Reconciliation rename = SnapshotReconciler.reconcile(v1, renamed);
        OrgSnapshot v2 = apply(v1, rename);
        assertThat(orgUnit(v2, 2).name()).isEqualTo("Renamed");
        assertThat(rename.diffs()).anySatisfy(d -> {
            assertThat(d.subjectKey()).isEqualTo("org-unit:2");
            assertThat(d.changedFields()).containsExactly("name");
        });
        assertThat(orgUnit(v2, 3).status()).isEqualTo(OrgUnitStatus.REVOKED);

        Reconciliation back = SnapshotReconciler.reconcile(v2, tree());
        OrgSnapshot v3 = apply(v2, back);
        assertThat(orgUnit(v3, 3).status()).isEqualTo(OrgUnitStatus.ACTIVE);
        assertThat(back.diffs()).anyMatch(d -> d.changeKind() == ChangeKind.REVIVED && d.subjectKey().equals("org-unit:3"));
    }

    @Test
    @DisplayName("RULE-357 columns the sync does not own (short name) survive an update")
    void shortNameIsNotOverwritten() {
        OrgSnapshot v1 = apply(OrgSnapshot.empty(), first(tree()));
        OrgUnit withShortName = orgUnit(v1, 2).withShortName("S2");
        OrgSnapshot stored = new OrgSnapshot(v1.orgUnits().stream().map(u -> u.orgUnitId().equals(unitId(2)) ? withShortName : u).toList(),
                v1.positions(), v1.staff());

        SourceOrgSnapshot renamed = source(List.of(unit(1, null), new SourceOrgUnit(sourceUnit(2), "C2", "Renamed", sourceUnit(1), 2,
                SourceRecordState.ACTIVE, List.of(), null, null, false, null), unit(3, 2L)), List.of(),
                List.of(post(100), post(101)), List.of());
        OrgSnapshot after = apply(stored, SnapshotReconciler.reconcile(stored, renamed));

        assertThat(orgUnit(after, 2).shortName()).isEqualTo("S2");
    }

    @Test
    @DisplayName("RULE-360 'unit and below' leaves out revoked units and everything below them; disabled units stay")
    void unitAndBelowExcludesRevoked() {
        List<OrgUnit> units = List.of(
                new OrgUnit(unitId(15), null, "15", null, 1, OrgUnitStatus.ACTIVE, null, null, null, false, null),
                new OrgUnit(unitId(16), null, "16", unitId(15), 1, OrgUnitStatus.ACTIVE, null, null, null, false, null),
                new OrgUnit(unitId(17), null, "17", unitId(16), 1, OrgUnitStatus.REVOKED, null, null, null, false, null),
                new OrgUnit(unitId(18), null, "18", unitId(17), 1, OrgUnitStatus.ACTIVE, null, null, null, false, null),
                new OrgUnit(unitId(19), null, "19", unitId(15), 2, OrgUnitStatus.DISABLED, null, null, null, false, null));

        List<OrgUnitId> scope = OrgHierarchy.unitAndBelow(units, unitId(15));

        assertThat(scope).containsExactly(unitId(15), unitId(16), unitId(19));
    }

    @Test
    @DisplayName("RULE-360 ancestors come nearest first, exclude the unit itself and survive a corrupt loop")
    void ancestorsNearestFirst() {
        OrgSnapshot snapshot = apply(OrgSnapshot.empty(), first(tree()));

        assertThat(OrgHierarchy.ancestors(snapshot.orgUnitsById(), unitId(3))).containsExactly(unitId(2), unitId(1));
        assertThat(OrgHierarchy.ancestors(snapshot.orgUnitsById(), unitId(1))).isEmpty();

        OrgUnit loopA = new OrgUnit(unitId(7), null, "7", unitId(8), 1, OrgUnitStatus.ACTIVE, null, null, null, false, null);
        OrgUnit loopB = new OrgUnit(unitId(8), null, "8", unitId(7), 1, OrgUnitStatus.ACTIVE, null, null, null, false, null);
        assertThat(OrgHierarchy.ancestors(new OrgSnapshot(List.of(loopA, loopB), List.of(), List.of()).orgUnitsById(), unitId(7)))
                .containsExactly(unitId(8));
    }
}
