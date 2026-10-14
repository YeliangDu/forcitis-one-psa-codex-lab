package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Pure: no I/O, no clock; the same function builds the expected state for reconciliation against the source. */
public final class SnapshotReconciler {
    private SnapshotReconciler() {
    }

    public static Reconciliation reconcile(OrgSnapshot current, SourceSnapshot source) {
        List<SyncDiff> diffs = new ArrayList<>();
        List<Position> changedPositions = reconcilePositions(current, source, diffs);
        Staff staff = reconcileStaff(current, source, diffs);
        Units units = reconcileOrgUnits(current, source, staff.statusAfter(), diffs);
        int issues = (int) diffs.stream().filter(SyncDiff::isIssue).count();
        int incomingActiveUnits = (int) source.orgUnits().stream().filter(u -> u.status() == OrgUnitStatus.ACTIVE).count();
        RunStats stats = new RunStats(source.orgUnits().size(), source.members().size(), incomingActiveUnits,
                staff.incomingEmployed(), current.activeOrgUnitCount(), current.employedCount(),
                units.changed().size(), staff.changed().size(), changedPositions.size(), staff.isolated().size(), issues,
                staff.employedWithoutWecom());
        return new Reconciliation(changedPositions, units.changed(), staff.changed(), diffs, staff.changedEmployees(),
                staff.separatedEmployees(), units.changed().stream().map(OrgUnit::orgUnitId).toList(), stats);
    }

    private static List<Position> reconcilePositions(OrgSnapshot current, SourceSnapshot source, List<SyncDiff> diffs) {
        Map<PositionId, Position> before = current.positionsById();
        Map<PositionId, Position> wanted = new LinkedHashMap<>();
        for (SourcePosition p : source.positions()) {
            wanted.putIfAbsent(p.positionId(), new Position(p.positionId(), p.code(), p.name(), p.sortOrder(), p.status()));
        }
        List<Position> changed = new ArrayList<>();
        wanted.forEach((id, position) -> {
            Position old = before.get(id);
            String subject = "position:" + id.value();
            if (old == null) {
                changed.add(position);
                diffs.add(SyncDiff.change(EntityKind.POSITION, subject, ChangeKind.CREATED, List.of()));
                return;
            }
            List<String> fields = SyncedFields.differences(SyncedFields.POSITION, old, position);
            if (!fields.isEmpty()) {
                changed.add(position);
                diffs.add(SyncDiff.change(EntityKind.POSITION, subject, ChangeKind.UPDATED, fields));
            }
        });
        before.forEach((id, old) -> {
            if (!wanted.containsKey(id) && old.status() != PositionStatus.DELETED) {
                changed.add(old.withStatus(PositionStatus.DELETED));
                diffs.add(SyncDiff.change(EntityKind.POSITION, "position:" + id.value(), ChangeKind.UPDATED, List.of("status")));
            }
        });
        return changed;
    }

    private record Staff(List<StaffMember> changed, List<UserId> changedEmployees, List<UserId> separatedEmployees,
                         Set<UserId> isolated, Map<UserId, EmploymentStatus> statusAfter, int incomingEmployed,
                         int employedWithoutWecom) {
    }

    private static Staff reconcileStaff(OrgSnapshot current, SourceSnapshot source, List<SyncDiff> diffs) {
        Map<UserId, StaffMember> currentStaff = current.staffById();
        Set<OrgUnitId> knownUnits = source.orgUnits().stream().map(SourceOrgUnit::orgUnitId).collect(Collectors.toSet());
        Set<PositionId> knownPositions = source.positions().stream().map(SourcePosition::positionId).collect(Collectors.toSet());

        Set<UserId> seen = new HashSet<>();
        Set<UserId> isolated = new LinkedHashSet<>();
        Map<UserId, SourceMember> valid = new LinkedHashMap<>();
        int incomingEmployed = 0;
        for (SourceMember member : source.members()) {
            if (!seen.add(member.userId())) {
                diffs.add(SyncDiff.issue(EntityKind.EMPLOYEE, "employee:" + member.userId().value(), IssueKind.DUPLICATE_MEMBER,
                        "the source returned this person more than once; the first row is used"));
                continue;
            }
            if (MemberChecks.employmentOf(member) == EmploymentStatus.EMPLOYED) {
                incomingEmployed++;
            }
            Optional<SyncDiff> isolation = MemberChecks.isolation(member, knownUnits::contains, knownPositions::contains);
            if (isolation.isPresent()) {
                isolated.add(member.userId());
                diffs.add(isolation.get());
            } else {
                valid.put(member.userId(), member);
            }
        }

        Set<UserId> keepWecom = wecomUserIdsToKeep(valid, isolated, currentStaff, diffs);

        Map<UserId, EmploymentStatus> statusAfter = new HashMap<>();
        currentStaff.forEach((id, s) -> statusAfter.put(id, s.employee().employmentStatus()));
        currentStaff.keySet().stream().filter(id -> !seen.contains(id)).forEach(id -> statusAfter.put(id, EmploymentStatus.SEPARATED));
        valid.forEach((id, m) -> statusAfter.put(id, MemberChecks.employmentOf(m)));

        Map<UserId, UserId> claimed = new LinkedHashMap<>();
        valid.forEach((id, m) -> {
            if (MemberChecks.employmentOf(m) == EmploymentStatus.EMPLOYED && m.supervisorId() != null) {
                claimed.put(id, m.supervisorId());
            }
        });
        Function<UserId, UserId> storedSupervisor = id -> {
            StaffMember s = currentStaff.get(id);
            return s == null ? null : s.assignment().reportsToEmployeeId();
        };
        Map<UserId, UserId> acceptedSupervisors = ReportingLines.accept(claimed, statusAfter, storedSupervisor, diffs);

        List<StaffMember> changed = new ArrayList<>();
        List<UserId> changedEmployees = new ArrayList<>();
        List<UserId> separatedEmployees = new ArrayList<>();
        Map<UserId, StaffMember> after = new LinkedHashMap<>(currentStaff);

        valid.forEach((id, m) -> {
            Employee employee = MemberChecks.toEmployee(m, keepWecom.contains(id));
            boolean employed = employee.employmentStatus() == EmploymentStatus.EMPLOYED;
            UserId leader = employed ? acceptedSupervisors.get(id) : (id.equals(m.supervisorId()) ? null : m.supervisorId());
            StaffMember desired = new StaffMember(employee, new Assignment(id, m.orgUnitId(), m.positionId(), leader));
            StaffMember before = currentStaff.get(id);
            String subject = "employee:" + id.value();
            if (before == null) {
                changed.add(desired);
                changedEmployees.add(id);
                diffs.add(SyncDiff.change(EntityKind.EMPLOYEE, subject, ChangeKind.CREATED, List.of()));
                after.put(id, desired);
                return;
            }
            List<String> fields = SyncedFields.differences(SyncedFields.STAFF, before, desired);
            if (fields.isEmpty()) {
                return;
            }
            EmploymentStatus was = before.employee().employmentStatus();
            ChangeKind kind = ChangeKind.UPDATED;
            if (was == EmploymentStatus.EMPLOYED && !employed) {
                kind = ChangeKind.SEPARATED;
                separatedEmployees.add(id);
            } else {
                changedEmployees.add(id);
                if (was == EmploymentStatus.SEPARATED && employed) {
                    kind = ChangeKind.REHIRED;
                }
            }
            changed.add(desired);
            diffs.add(SyncDiff.change(EntityKind.EMPLOYEE, subject, kind, fields));
            after.put(id, desired);
        });

        currentStaff.forEach((id, before) -> {
            if (seen.contains(id) || before.employee().employmentStatus() == EmploymentStatus.SEPARATED) {
                return;
            }
            StaffMember desired = new StaffMember(before.employee().withEmploymentStatus(EmploymentStatus.SEPARATED), before.assignment());
            changed.add(desired);
            separatedEmployees.add(id);
            diffs.add(SyncDiff.change(EntityKind.EMPLOYEE, "employee:" + id.value(), ChangeKind.SEPARATED, List.of("employmentStatus")));
            after.put(id, desired);
        });

        int withoutWecom = (int) after.values().stream()
                .filter(s -> s.employee().employmentStatus() == EmploymentStatus.EMPLOYED && s.employee().wecomUserId() == null)
                .count();
        return new Staff(changed, changedEmployees, separatedEmployees, isolated, statusAfter, incomingEmployed, withoutWecom);
    }

    /** A WeCom user id is the login key: when several employed rows share it, none keeps it (login fails closed). */
    private static Set<UserId> wecomUserIdsToKeep(Map<UserId, SourceMember> valid, Set<UserId> isolated,
                                                  Map<UserId, StaffMember> currentStaff, List<SyncDiff> diffs) {
        Map<WecomUserId, Integer> employedHolders = new HashMap<>();
        valid.values().stream()
                .filter(m -> m.wecomUserId() != null && MemberChecks.employmentOf(m) == EmploymentStatus.EMPLOYED)
                .forEach(m -> employedHolders.merge(m.wecomUserId(), 1, Integer::sum));
        for (UserId id : isolated) {
            StaffMember retained = currentStaff.get(id);
            if (retained != null && retained.employee().employmentStatus() == EmploymentStatus.EMPLOYED
                    && retained.employee().wecomUserId() != null) {
                employedHolders.merge(retained.employee().wecomUserId(), 1, Integer::sum);
            }
        }
        Set<UserId> keep = new HashSet<>();
        valid.forEach((id, m) -> {
            boolean unique = m.wecomUserId() == null || MemberChecks.employmentOf(m) != EmploymentStatus.EMPLOYED
                    || employedHolders.get(m.wecomUserId()) <= 1;
            if (unique) {
                keep.add(id);
            } else {
                diffs.add(SyncDiff.issue(EntityKind.EMPLOYEE, "employee:" + id.value(), IssueKind.DUPLICATE_WECOM_USER_ID,
                        "several employed people carry the same WeCom user id; it is not stored for any of them"));
            }
        });
        return keep;
    }

    private record Units(List<OrgUnit> changed) {
    }

    private static Units reconcileOrgUnits(OrgSnapshot current, SourceSnapshot source,
                                           Map<UserId, EmploymentStatus> statusAfter, List<SyncDiff> diffs) {
        List<SourceOrgUnit> sorted = parentsFirst(source.orgUnits());
        for (SourceOrgUnit unit : sorted) {
            if (unit.duplicateOfUnitId() != null) {
                diffs.add(SyncDiff.issue(EntityKind.ORG_UNIT, "org-unit:" + unit.orgUnitId().value(), IssueKind.DUPLICATE_UNIT,
                        "the place in the org tree is also used by org unit " + unit.duplicateOfUnitId().value()
                                + "; the active one is the parent candidate"));
            }
        }
        Map<OrgUnitId, UserId> heads = pickSmallest(source, RelationshipKind.HEAD, IssueKind.MULTIPLE_HEADS, diffs);
        Map<OrgUnitId, UserId> managers = pickSmallest(source, RelationshipKind.REGIONAL_RECEIVABLES_MANAGER,
                IssueKind.MULTIPLE_REGIONAL_MANAGERS, diffs);

        Map<OrgUnitId, OrgUnit> before = current.orgUnitsById();
        Set<OrgUnitId> sourceIds = new HashSet<>();
        List<OrgUnit> changed = new ArrayList<>();
        for (SourceOrgUnit unit : sorted) {
            sourceIds.add(unit.orgUnitId());
            String subject = "org-unit:" + unit.orgUnitId().value();
            OrgUnitId parent = unit.parentOrgUnitId();
            if (!unit.nullFlags().isEmpty()) {
                diffs.add(SyncDiff.issue(EntityKind.ORG_UNIT, subject, IssueKind.NULL_FLAG,
                        "flags " + unit.nullFlags() + " are NULL in the source; treated as valid"));
            }
            if (unit.regionCategory() == null && unit.rawCategory() != null) {
                diffs.add(SyncDiff.issue(EntityKind.ORG_UNIT, subject, IssueKind.UNKNOWN_CATEGORY,
                        "unrecognised category value '" + unit.rawCategory() + "'; stored empty"));
            }
            UserId head = known(heads.get(unit.orgUnitId()), statusAfter, subject, IssueKind.HEAD_NOT_FOUND, "head", diffs);
            UserId manager = known(managers.get(unit.orgUnitId()), statusAfter, subject,
                    IssueKind.REGIONAL_MANAGER_NOT_FOUND, "regional receivables manager", diffs);
            OrgUnit old = before.get(unit.orgUnitId());
            OrgUnit desired = new OrgUnit(unit.orgUnitId(), unit.code(), unit.name(), parent, unit.sortOrder(),
                    unit.status(), head, manager, unit.regionCategory(), unit.deliveryUnit(), old == null ? null : old.shortName());
            if (old == null) {
                changed.add(desired);
                diffs.add(SyncDiff.change(EntityKind.ORG_UNIT, subject, ChangeKind.CREATED, List.of()));
                continue;
            }
            List<String> fields = SyncedFields.differences(SyncedFields.ORG_UNIT, old, desired);
            if (!fields.isEmpty()) {
                changed.add(desired);
                diffs.add(SyncDiff.change(EntityKind.ORG_UNIT, subject,
                        old.status() == OrgUnitStatus.REVOKED ? ChangeKind.REVIVED : ChangeKind.UPDATED, fields));
            }
        }
        // No longer returned = revoked (not "disabled"); never repeated.
        before.forEach((id, old) -> {
            if (!sourceIds.contains(id) && old.status() != OrgUnitStatus.REVOKED) {
                changed.add(old.withStatus(OrgUnitStatus.REVOKED));
                diffs.add(SyncDiff.change(EntityKind.ORG_UNIT, "org-unit:" + id.value(), ChangeKind.REVOKED, List.of("status")));
            }
        });
        return new Units(changed);
    }

    private static List<SourceOrgUnit> parentsFirst(List<SourceOrgUnit> units) {
        Map<OrgUnitId, SourceOrgUnit> byId = new HashMap<>();
        units.forEach(u -> byId.putIfAbsent(u.orgUnitId(), u));
        return units.stream()
                .sorted(Comparator.comparingInt((SourceOrgUnit u) -> depth(u, byId))
                        .thenComparing(u -> u.sortOrder() == null ? Integer.MAX_VALUE : u.sortOrder())
                        .thenComparingLong(u -> u.orgUnitId().value()))
                .toList();
    }

    private static int depth(SourceOrgUnit unit, Map<OrgUnitId, SourceOrgUnit> byId) {
        int depth = 0;
        Set<OrgUnitId> seen = new HashSet<>();
        SourceOrgUnit at = unit;
        while (at.parentOrgUnitId() != null && seen.add(at.orgUnitId())) {
            at = byId.get(at.parentOrgUnitId());
            if (at == null) {
                break;
            }
            depth++;
        }
        return depth;
    }

    private static UserId known(UserId person, Map<UserId, EmploymentStatus> statusAfter, String subject,
                                IssueKind issue, String role, List<SyncDiff> diffs) {
        if (person != null && !statusAfter.containsKey(person)) {
            diffs.add(SyncDiff.issue(EntityKind.ORG_UNIT, subject, issue, role + " " + person.value() + " is not in the snapshot; stored empty"));
            return null;
        }
        return person;
    }

    /** Several people for one unit: the smallest id wins, deterministically. */
    private static Map<OrgUnitId, UserId> pickSmallest(SourceSnapshot source, RelationshipKind kind, IssueKind multiple,
                                                       List<SyncDiff> diffs) {
        Map<OrgUnitId, List<UserId>> byUnit = new LinkedHashMap<>();
        for (SourceRelationship r : source.relationships()) {
            if (r.kind() == kind && !r.memberDeleted()) {
                byUnit.computeIfAbsent(r.orgUnitId(), k -> new ArrayList<>()).add(r.memberId());
            }
        }
        Map<OrgUnitId, UserId> picked = new LinkedHashMap<>();
        byUnit.forEach((unit, members) -> {
            List<UserId> ordered = members.stream().distinct().sorted(Comparator.comparingLong(UserId::value)).toList();
            picked.put(unit, ordered.getFirst());
            if (ordered.size() > 1) {
                diffs.add(SyncDiff.issue(EntityKind.ORG_UNIT, "org-unit:" + unit.value(), multiple,
                        "several people " + ordered.stream().map(u -> String.valueOf(u.value())).toList()
                                + "; the smallest id " + ordered.getFirst().value() + " is used"));
            }
        });
        return picked;
    }
}
