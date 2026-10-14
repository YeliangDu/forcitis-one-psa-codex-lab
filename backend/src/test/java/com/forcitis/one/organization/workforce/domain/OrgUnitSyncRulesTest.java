package com.forcitis.one.organization.workforce.domain;

import static com.forcitis.one.organization.workforce.domain.Fixtures.ROOT;
import static com.forcitis.one.organization.workforce.domain.Fixtures.apply;
import static com.forcitis.one.organization.workforce.domain.Fixtures.first;
import static com.forcitis.one.organization.workforce.domain.Fixtures.head;
import static com.forcitis.one.organization.workforce.domain.Fixtures.issues;
import static com.forcitis.one.organization.workforce.domain.Fixtures.manager;
import static com.forcitis.one.organization.workforce.domain.Fixtures.member;
import static com.forcitis.one.organization.workforce.domain.Fixtures.orgUnit;
import static com.forcitis.one.organization.workforce.domain.Fixtures.post;
import static com.forcitis.one.organization.workforce.domain.Fixtures.source;
import static com.forcitis.one.organization.workforce.domain.Fixtures.tree;
import static com.forcitis.one.organization.workforce.domain.Fixtures.unit;
import static com.forcitis.one.organization.workforce.domain.Fixtures.unitId;
import static com.forcitis.one.organization.workforce.domain.Fixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.organization.workforce.api.OrgUnitId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Org unit rules of RULE-354..357 and 360 (acceptance tests named after the rule ids). */
class OrgUnitSyncRulesTest {

    @Test
    @DisplayName("RULE-354 the parent is the unit whose PATH is its own minus the last 4 characters; the root has none")
    void parentComesFromPath() {
        OrgSnapshot snapshot = apply(OrgSnapshot.empty(), first(tree()));

        assertThat(orgUnit(snapshot, 1).parentOrgUnitId()).isNull();
        assertThat(orgUnit(snapshot, 2).parentOrgUnitId()).isEqualTo(unitId(1));
        assertThat(orgUnit(snapshot, 3).parentOrgUnitId()).isEqualTo(unitId(2));
    }

    @Test
    @DisplayName("RULE-354 a missing parent PATH aborts the whole run and nothing is written")
    void brokenTreeAbortsTheRun() {
        SourceSnapshot broken = source(List.of(unit(1, ROOT), unit(9, ROOT + "0099" + "0001")), List.of(), List.of(post(100)), List.of());

        assertThatThrownBy(() -> first(broken)).isInstanceOf(BrokenOrgTreeException.class);
    }

    @Test
    @DisplayName("RULE-354 a PATH outside the sync root is a broken tree, not silently accepted")
    void pathOutsideTheRootIsRejected() {
        SourceSnapshot outside = source(List.of(unit(1, ROOT), unit(2, "00000002")), List.of(), List.of(post(100)), List.of());

        assertThatThrownBy(() -> first(outside)).isInstanceOf(BrokenOrgTreeException.class);
    }

    @Test
    @DisplayName("RULE-354 when two units share a PATH, the active one is the parent candidate and the duplicate is reported")
    void activeUnitWinsASharedPath() {
        SourceSnapshot shared = source(List.of(unit(1, ROOT), unit(2, ROOT + "0004", OrgUnitStatus.DISABLED),
                unit(3, ROOT + "0004"), unit(4, ROOT + "00040001")), List.of(), List.of(post(100)), List.of());

        Reconciliation changes = first(shared);
        OrgSnapshot snapshot = apply(OrgSnapshot.empty(), changes);

        assertThat(orgUnit(snapshot, 4).parentOrgUnitId()).isEqualTo(unitId(3));
        assertThat(issues(changes)).contains(IssueKind.DUPLICATE_PATH);
    }

    @Test
    @DisplayName("RULE-355 a disabled unit is DISABLED, a unit A8 no longer returns is REVOKED: two different states")
    void disabledAndRevokedAreSeparate() {
        OrgSnapshot before = apply(OrgSnapshot.empty(), first(tree()));
        SourceSnapshot next = source(List.of(unit(1, ROOT), unit(2, ROOT + "0001", OrgUnitStatus.DISABLED)), List.of(),
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
        SourceOrgUnit nullFlags = new SourceOrgUnit(unitId(1), "C1", "Unit 1", ROOT, 1, OrgUnitStatus.ACTIVE,
                List.of("IS_ENABLE"), null, null, false);

        Reconciliation changes = first(source(List.of(nullFlags), List.of(), List.of(post(100)), List.of()));

        assertThat(orgUnit(apply(OrgSnapshot.empty(), changes), 1).status()).isEqualTo(OrgUnitStatus.ACTIVE);
        assertThat(issues(changes)).containsExactly(IssueKind.NULL_FLAG);
    }

    @Test
    @DisplayName("RULE-356 several heads: the smallest id wins, deterministically, and the data steward is told")
    void smallestHeadIdWins() {
        SourceSnapshot snapshot = source(List.of(unit(1, ROOT)), List.of(head(1, 805), head(1, 801)), List.of(post(100)),
                List.of(member(801, 1, 100L, null), member(805, 1, 100L, null)));

        Reconciliation changes = first(snapshot);

        assertThat(orgUnit(apply(OrgSnapshot.empty(), changes), 1).headEmployeeId()).isEqualTo(user(801));
        assertThat(issues(changes)).contains(IssueKind.MULTIPLE_HEADS);
    }

    @Test
    @DisplayName("RULE-356 the regional receivables manager follows the same rule and a removed one becomes empty")
    void managerIsSyncedByValue() {
        SourceSnapshot with = source(List.of(unit(1, ROOT)), List.of(manager(1, 801)), List.of(post(100)),
                List.of(member(801, 1, 100L, null)));
        OrgSnapshot before = apply(OrgSnapshot.empty(), first(with));
        assertThat(orgUnit(before, 1).regionalReceivablesManagerId()).isEqualTo(user(801));

        SourceSnapshot without = source(List.of(unit(1, ROOT)), List.of(), List.of(post(100)), List.of(member(801, 1, 100L, null)));
        OrgSnapshot after = apply(before, SnapshotReconciler.reconcile(before, without));

        assertThat(orgUnit(after, 1).regionalReceivablesManagerId()).isNull();
    }

    @Test
    @DisplayName("RULE-356 a head who is not in the snapshot is stored empty and reported, not left dangling")
    void unknownHeadIsReported() {
        SourceSnapshot snapshot = source(List.of(unit(1, ROOT)), List.of(head(1, 999)), List.of(post(100)), List.of());

        Reconciliation changes = first(snapshot);

        assertThat(orgUnit(apply(OrgSnapshot.empty(), changes), 1).headEmployeeId()).isNull();
        assertThat(issues(changes)).contains(IssueKind.HEAD_NOT_FOUND);
    }

    @Test
    @DisplayName("RULE-357 insert new, update on change, revoke what A8 no longer returns, revive when it returns")
    void insertUpdateRevokeRevive() {
        OrgSnapshot v1 = apply(OrgSnapshot.empty(), first(tree()));

        Reconciliation noChange = SnapshotReconciler.reconcile(v1, tree());
        assertThat(noChange.hasChanges()).isFalse();

        SourceSnapshot renamed = source(List.of(unit(1, ROOT), new SourceOrgUnit(unitId(2), "C2", "Renamed", ROOT + "0001", 2,
                OrgUnitStatus.ACTIVE, List.of(), null, null, false)), List.of(), List.of(post(100), post(101)), List.of());
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

        SourceSnapshot renamed = source(List.of(unit(1, ROOT), new SourceOrgUnit(unitId(2), "C2", "Renamed", ROOT + "0001", 2,
                OrgUnitStatus.ACTIVE, List.of(), null, null, false), unit(3, ROOT + "00010001")), List.of(),
                List.of(post(100), post(101)), List.of());
        OrgSnapshot after = apply(stored, SnapshotReconciler.reconcile(stored, renamed));

        assertThat(orgUnit(after, 2).shortName()).isEqualTo("S2");
    }

    @Test
    @DisplayName("RULE-360 'unit and below' leaves out revoked units and everything below them; disabled units stay")
    void unitAndBelowExcludesRevoked() {
        List<OrgUnit> units = List.of(
                new OrgUnit(unitId(15), null, "15", null, ROOT, 1, OrgUnitStatus.ACTIVE, null, null, null, false, null),
                new OrgUnit(unitId(16), null, "16", unitId(15), ROOT + "0001", 1, OrgUnitStatus.ACTIVE, null, null, null, false, null),
                new OrgUnit(unitId(17), null, "17", unitId(16), ROOT + "00010001", 1, OrgUnitStatus.REVOKED, null, null, null, false, null),
                new OrgUnit(unitId(18), null, "18", unitId(17), ROOT + "000100010001", 1, OrgUnitStatus.ACTIVE, null, null, null, false, null),
                new OrgUnit(unitId(19), null, "19", unitId(15), ROOT + "0002", 2, OrgUnitStatus.DISABLED, null, null, null, false, null));

        List<OrgUnitId> scope = OrgHierarchy.unitAndBelow(units, unitId(15));

        assertThat(scope).containsExactly(unitId(15), unitId(16), unitId(19));
    }

    @Test
    @DisplayName("RULE-360 ancestors come nearest first, exclude the unit itself and survive a corrupt loop")
    void ancestorsNearestFirst() {
        OrgSnapshot snapshot = apply(OrgSnapshot.empty(), first(tree()));

        assertThat(OrgHierarchy.ancestors(snapshot.orgUnitsById(), unitId(3))).containsExactly(unitId(2), unitId(1));
        assertThat(OrgHierarchy.ancestors(snapshot.orgUnitsById(), unitId(1))).isEmpty();

        OrgUnit loopA = new OrgUnit(unitId(7), null, "7", unitId(8), ROOT, 1, OrgUnitStatus.ACTIVE, null, null, null, false, null);
        OrgUnit loopB = new OrgUnit(unitId(8), null, "8", unitId(7), ROOT, 1, OrgUnitStatus.ACTIVE, null, null, null, false, null);
        assertThat(OrgHierarchy.ancestors(new OrgSnapshot(List.of(loopA, loopB), List.of(), List.of()).orgUnitsById(), unitId(7)))
                .containsExactly(unitId(8));
    }
}
