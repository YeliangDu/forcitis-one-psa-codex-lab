package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import java.util.ArrayList;
import java.util.List;

/** Small builders for the reconciler tests: org tree 1 > 2 > 3, positions 100 / 101, people 1001... */
public final class Fixtures {

    public static final String ROOT = "00000001";

    private Fixtures() {
    }

    public static OrgUnitId unitId(long id) {
        return new OrgUnitId(id);
    }

    public static UserId user(long id) {
        return new UserId(id);
    }

    public static SourceOrgUnit unit(long id, String path) {
        return unit(id, path, OrgUnitStatus.ACTIVE);
    }

    public static SourceOrgUnit unit(long id, String path, OrgUnitStatus status) {
        return new SourceOrgUnit(unitId(id), "C" + id, "Unit " + id, path, (int) id, status, List.of(), null, null, false);
    }

    public static SourcePosition post(long id) {
        return new SourcePosition(new PositionId(id), "P" + id, "Post " + id, (int) id, PositionStatus.ACTIVE);
    }

    public static SourcePosition post(long id, PositionStatus status) {
        return new SourcePosition(new PositionId(id), "P" + id, "Post " + id, (int) id, status);
    }

    /** An employed person with login name "13900000000" and WeCom id "wx-<id>". */
    public static SourceMember member(long id, long unit, Long post, Long leader) {
        return member(id, unit, post, leader, "139" + id, "wx-" + id);
    }

    public static SourceMember member(long id, long unit, Long post, Long leader, String login, String wecom) {
        return new SourceMember(user(id), "E" + id, "Person " + id, unitId(unit), post == null ? null : new PositionId(post),
                leader == null ? null : user(leader), login, "p" + id + "@example.test", Gender.MALE,
                wecom == null ? null : new WecomUserId(wecom), false);
    }

    public static SourceRelationship head(long unit, long member) {
        return new SourceRelationship(RelationshipKind.HEAD, unitId(unit), user(member), false);
    }

    public static SourceRelationship manager(long unit, long member) {
        return new SourceRelationship(RelationshipKind.REGIONAL_RECEIVABLES_MANAGER, unitId(unit), user(member), false);
    }

    public static SourceSnapshot source(List<SourceOrgUnit> units, List<SourceRelationship> relationships,
                                 List<SourcePosition> positions, List<SourceMember> members) {
        return new SourceSnapshot(units, relationships, positions, members);
    }

    /** Tree 1 > 2 > 3, posts 100 and 101, no people. */
    public static SourceSnapshot tree(SourceMember... members) {
        return source(List.of(unit(1, ROOT), unit(2, ROOT + "0001"), unit(3, ROOT + "00010001")), List.of(),
                List.of(post(100), post(101)), List.of(members));
    }

    public static Reconciliation first(SourceSnapshot source) {
        return SnapshotReconciler.reconcile(OrgSnapshot.empty(), source);
    }

    /** The snapshot as it would be stored after applying the reconciliation to the snapshot it was computed from. */
    public static OrgSnapshot apply(OrgSnapshot before, Reconciliation changes) {
        List<OrgUnit> units = new ArrayList<>(before.orgUnitsById().values());
        units.removeIf(u -> changes.orgUnits().stream().anyMatch(c -> c.orgUnitId().equals(u.orgUnitId())));
        units.addAll(changes.orgUnits());
        List<Position> positions = new ArrayList<>(before.positionsById().values());
        positions.removeIf(p -> changes.positions().stream().anyMatch(c -> c.positionId().equals(p.positionId())));
        positions.addAll(changes.positions());
        List<StaffMember> staff = new ArrayList<>(before.staffById().values());
        staff.removeIf(s -> changes.staff().stream().anyMatch(c -> c.userId().equals(s.userId())));
        staff.addAll(changes.staff());
        return new OrgSnapshot(units, positions, staff);
    }

    public static StaffMember staff(OrgSnapshot snapshot, long id) {
        return snapshot.staffById().get(user(id));
    }

    public static OrgUnit orgUnit(OrgSnapshot snapshot, long id) {
        return snapshot.orgUnitsById().get(unitId(id));
    }

    public static boolean employed(StaffMember s) {
        return s.employee().employmentStatus() == EmploymentStatus.EMPLOYED;
    }

    public static List<IssueKind> issues(Reconciliation changes) {
        return changes.diffs().stream().filter(SyncDiff::isIssue).map(SyncDiff::issueKind).toList();
    }

    public static List<SyncDiff> issues(Reconciliation changes, IssueKind kind) {
        return changes.diffs().stream().filter(d -> d.isIssue() && d.issueKind() == kind).toList();
    }
}
