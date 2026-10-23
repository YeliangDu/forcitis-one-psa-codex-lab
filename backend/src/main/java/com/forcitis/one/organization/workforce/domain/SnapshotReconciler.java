package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.platform.integration.api.OwnObjectId;
import com.forcitis.one.platform.integration.api.SourceOrgSnapshot;
import com.forcitis.one.platform.integration.api.SourceOrgUnit;
import com.forcitis.one.platform.integration.api.SourcePerson;
import com.forcitis.one.platform.integration.api.SourcePosition;
import com.forcitis.one.platform.integration.api.SourceRecordState;
import com.forcitis.one.platform.integration.api.SourceRelationship;
import com.forcitis.one.platform.integration.api.SourceRelationshipKind;
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

    public static Reconciliation reconcile(OrgSnapshot current, SourceOrgSnapshot source) {
        List<SyncDiff> diffs = new ArrayList<>();
        List<Position> changedPositions = reconcilePositions(current, source, diffs);
        Staff staff = reconcileStaff(current, source, diffs);
        Units units = reconcileOrgUnits(current, source, staff.statusAfter(), diffs);
        int issues = (int) diffs.stream().filter(SyncDiff::isIssue).count();
        int incomingActiveUnits = (int) source.units().stream().filter(u -> u.state() == SourceRecordState.ACTIVE).count();
        RunStats stats = new RunStats(source.units().size(), source.staff().size(), incomingActiveUnits,
                staff.incomingEmployed(), current.activeOrgUnitCount(), current.employedCount(),
                units.changed().size(), staff.changed().size(), changedPositions.size(), staff.isolated().size(), issues,
                staff.employedWithoutWecom());
        return new Reconciliation(changedPositions, units.changed(), staff.changed(), diffs, staff.changedEmployees(),
                staff.separatedEmployees(), units.changed().stream().map(OrgUnit::orgUnitId).toList(), stats);
    }

    private static List<Position> reconcilePositions(OrgSnapshot current, SourceOrgSnapshot source, List<SyncDiff> diffs) {
        Map<PositionId, Position> before = current.positionsById();
        Map<PositionId, Position> wanted = new LinkedHashMap<>();
        for (SourcePosition p : source.positions()) {
            PositionId id = SourceTerms.position(p.positionId());
            wanted.putIfAbsent(id, new Position(id, p.code(), p.name(), p.sortOrder(), SourceTerms.positionStatus(p.state())));
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

    private record Staff(List<StaffEmployee> changed, List<EmployeeId> changedEmployees, List<EmployeeId> separatedEmployees,
                         Set<EmployeeId> isolated, Map<EmployeeId, EmploymentStatus> statusAfter, int incomingEmployed,
                         int employedWithoutWecom) {
    }

    private static Staff reconcileStaff(OrgSnapshot current, SourceOrgSnapshot source, List<SyncDiff> diffs) {
        Map<EmployeeId, StaffEmployee> currentStaff = current.staffById();
        Set<OrgUnitId> knownUnits = source.units().stream().map(u -> SourceTerms.unit(u.unitId())).collect(Collectors.toSet());
        Set<PositionId> knownPositions = source.positions().stream().map(p -> SourceTerms.position(p.positionId())).collect(Collectors.toSet());

        Set<EmployeeId> seen = new HashSet<>();
        Set<EmployeeId> isolated = new LinkedHashSet<>();
        Map<EmployeeId, SourcePerson> valid = new LinkedHashMap<>();
        int incomingEmployed = 0;
        for (SourcePerson person : source.staff()) {
            EmployeeId employeeId = SourceTerms.person(person.personId());
            if (!seen.add(employeeId)) {
                diffs.add(SyncDiff.issue(EntityKind.EMPLOYEE, "employee:" + employeeId.value(), IssueKind.DUPLICATE_EMPLOYEE,
                        "the source returned this person more than once; the first row is used"));
                continue;
            }
            if (person.internalFlagMissing()) {
                diffs.add(SyncDiff.issue(EntityKind.EMPLOYEE, "employee:" + employeeId.value(), IssueKind.NULL_FLAG,
                        "the internal flag is NULL in the source; the person is treated as internal"));
            }
            if (EmployeeChecks.employmentOf(person) == EmploymentStatus.EMPLOYED) {
                incomingEmployed++;
            }
            Optional<SyncDiff> isolation = EmployeeChecks.isolation(person, knownUnits::contains, knownPositions::contains);
            if (isolation.isPresent()) {
                isolated.add(employeeId);
                diffs.add(isolation.get());
            } else {
                valid.put(employeeId, person);
            }
        }

        isolateTakenEmployeeCodes(valid, currentStaff, isolated, diffs);

        Set<EmployeeId> keepWecom = wecomUserIdsToKeep(valid, isolated, currentStaff, diffs);

        Map<EmployeeId, EmploymentStatus> statusAfter = new HashMap<>();
        currentStaff.forEach((id, s) -> statusAfter.put(id, s.employee().employmentStatus()));
        currentStaff.keySet().stream().filter(id -> !seen.contains(id)).forEach(id -> statusAfter.put(id, EmploymentStatus.SEPARATED));
        valid.forEach((id, m) -> statusAfter.put(id, EmployeeChecks.employmentOf(m)));

        Map<EmployeeId, EmployeeId> claimed = new LinkedHashMap<>();
        valid.forEach((id, m) -> {
            if (EmployeeChecks.employmentOf(m) == EmploymentStatus.EMPLOYED && m.supervisorId() != null) {
                claimed.put(id, SourceTerms.person(m.supervisorId()));
            }
        });
        Function<EmployeeId, EmployeeId> storedSupervisor = id -> {
            StaffEmployee s = currentStaff.get(id);
            return s == null ? null : s.assignment().reportsToEmployeeId();
        };
        Map<EmployeeId, EmployeeId> acceptedSupervisors = ReportingLines.accept(claimed, statusAfter, storedSupervisor, diffs);

        List<StaffEmployee> changed = new ArrayList<>();
        List<EmployeeId> changedEmployees = new ArrayList<>();
        List<EmployeeId> separatedEmployees = new ArrayList<>();
        Map<EmployeeId, StaffEmployee> after = new LinkedHashMap<>(currentStaff);

        valid.forEach((id, m) -> {
            Employee fromSource = EmployeeChecks.toEmployee(m, keepWecom.contains(id));
            StaffEmployee stored = currentStaff.get(id);
            Employee employee = stored == null ? fromSource : fromSource.withEmployeeCode(stored.employee().employeeCode());
            if (stored != null && !employee.employeeCode().equals(fromSource.employeeCode())) {
                diffs.add(SyncDiff.issue(EntityKind.EMPLOYEE, "employee:" + id.value(), IssueKind.EMPLOYEE_CODE_CHANGE_IGNORED,
                        "the source carries another employee code; the issued code is kept"));
            }
            boolean employed = employee.employmentStatus() == EmploymentStatus.EMPLOYED;
            EmployeeId supervisor = SourceTerms.person(m.supervisorId());
            EmployeeId leader = employed ? acceptedSupervisors.get(id) : (id.equals(supervisor) ? null : supervisor);
            StaffEmployee desired = new StaffEmployee(employee, new Assignment(id, SourceTerms.unit(m.unitId()),
                    SourceTerms.position(m.positionId()), leader));
            StaffEmployee before = currentStaff.get(id);
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
            StaffEmployee desired = new StaffEmployee(before.employee().withEmploymentStatus(EmploymentStatus.SEPARATED), before.assignment());
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

    /** The employee code is unique: a row whose code another person holds is isolated, never renumbered; an existing employee keeps its issued code (the source code is not read for it) and is served before a new one, and of two new people with one code the smaller source id is built. */
    private static void isolateTakenEmployeeCodes(Map<EmployeeId, SourcePerson> valid, Map<EmployeeId, StaffEmployee> currentStaff,
                                                  Set<EmployeeId> isolated, List<SyncDiff> diffs) {
        Map<String, EmployeeId> holders = new HashMap<>();
        currentStaff.forEach((id, s) -> {
            if (!valid.containsKey(id)) {
                holders.put(s.employee().employeeCode(), id);
            }
        });
        List<EmployeeId> rejected = new ArrayList<>();
        valid.entrySet().stream()
                .sorted(Comparator.<Map.Entry<EmployeeId, SourcePerson>, Boolean>comparing(entry -> !currentStaff.containsKey(entry.getKey()))
                        .thenComparing(Map.Entry.comparingByKey(Comparator.comparing(EmployeeId::value))))
                .forEach(entry -> {
                    StaffEmployee stored = currentStaff.get(entry.getKey());
                    String code = stored == null ? entry.getValue().employeeCode() : stored.employee().employeeCode();
                    EmployeeId holder = holders.putIfAbsent(code, entry.getKey());
                    if (holder != null && !holder.equals(entry.getKey())) {
                        rejected.add(entry.getKey());
                        diffs.add(SyncDiff.issue(EntityKind.EMPLOYEE, "employee:" + entry.getKey().value(), IssueKind.DUPLICATE_EMPLOYEE_CODE,
                                "the employee code is already held by employee " + holder.value() + "; the row is isolated"));
                    }
                });
        rejected.forEach(id -> {
            valid.remove(id);
            isolated.add(id);
        });
    }

    /** A WeCom user id is the login key: when several employed rows share it, none keeps it (login fails closed). */
    private static Set<EmployeeId> wecomUserIdsToKeep(Map<EmployeeId, SourcePerson> valid, Set<EmployeeId> isolated,
                                                  Map<EmployeeId, StaffEmployee> currentStaff, List<SyncDiff> diffs) {
        Map<WecomUserId, Integer> employedHolders = new HashMap<>();
        valid.values().stream()
                .filter(m -> m.wecomUserId() != null && EmployeeChecks.employmentOf(m) == EmploymentStatus.EMPLOYED)
                .forEach(m -> employedHolders.merge(m.wecomUserId(), 1, Integer::sum));
        for (EmployeeId id : isolated) {
            StaffEmployee retained = currentStaff.get(id);
            if (retained != null && retained.employee().employmentStatus() == EmploymentStatus.EMPLOYED
                    && retained.employee().wecomUserId() != null) {
                employedHolders.merge(retained.employee().wecomUserId(), 1, Integer::sum);
            }
        }
        Set<EmployeeId> keep = new HashSet<>();
        valid.forEach((id, m) -> {
            boolean unique = m.wecomUserId() == null || EmployeeChecks.employmentOf(m) != EmploymentStatus.EMPLOYED
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

    private static Units reconcileOrgUnits(OrgSnapshot current, SourceOrgSnapshot source,
                                           Map<EmployeeId, EmploymentStatus> statusAfter, List<SyncDiff> diffs) {
        List<SourceOrgUnit> sorted = parentsFirst(source.units());
        for (SourceOrgUnit unit : sorted) {
            if (unit.duplicateOfUnitId() != null) {
                diffs.add(SyncDiff.issue(EntityKind.ORG_UNIT, "org-unit:" + unit.unitId().value(), IssueKind.DUPLICATE_UNIT,
                        "the place in the org tree is also used by org unit " + unit.duplicateOfUnitId().value()
                                + "; the active one is the parent candidate"));
            }
        }
        Map<OrgUnitId, EmployeeId> heads = pickSmallest(source, SourceRelationshipKind.UNIT_HEAD, IssueKind.MULTIPLE_HEADS, diffs);
        Map<OrgUnitId, EmployeeId> managers = pickSmallest(source, SourceRelationshipKind.REGIONAL_RECEIVABLES_MANAGER,
                IssueKind.MULTIPLE_REGIONAL_MANAGERS, diffs);

        Map<OrgUnitId, OrgUnit> before = current.orgUnitsById();
        Set<OrgUnitId> sourceIds = new HashSet<>();
        Set<OrgUnitId> knownUnits = new HashSet<>(before.keySet());
        sorted.forEach(u -> knownUnits.add(SourceTerms.unit(u.unitId())));
        List<OrgUnit> changed = new ArrayList<>();
        for (SourceOrgUnit unit : sorted) {
            OrgUnitId unitId = SourceTerms.unit(unit.unitId());
            sourceIds.add(unitId);
            String subject = "org-unit:" + unitId.value();
            OrgUnitId parent = SourceTerms.unit(unit.parentUnitId());
            if (parent != null && !knownUnits.contains(parent)) {
                diffs.add(SyncDiff.issue(EntityKind.ORG_UNIT, subject, IssueKind.PARENT_NOT_FOUND,
                        "parent org unit " + parent.value() + " is neither in the snapshot nor stored; stored empty"));
                parent = null;
            }
            RegionCategory region = SourceTerms.region(unit.regionKind());
            if (!unit.emptyFlags().isEmpty()) {
                diffs.add(SyncDiff.issue(EntityKind.ORG_UNIT, subject, IssueKind.NULL_FLAG,
                        "flags " + unit.emptyFlags() + " are NULL in the source; treated as valid"));
            }
            if (region == null && unit.rawRegion() != null) {
                diffs.add(SyncDiff.issue(EntityKind.ORG_UNIT, subject, IssueKind.UNKNOWN_CATEGORY,
                        "unrecognised category value '" + unit.rawRegion() + "'; stored empty"));
            }
            EmployeeId head = known(heads.get(unitId), statusAfter, subject, IssueKind.HEAD_NOT_FOUND, "head", diffs);
            EmployeeId manager = known(managers.get(unitId), statusAfter, subject,
                    IssueKind.REGIONAL_MANAGER_NOT_FOUND, "regional receivables manager", diffs);
            OrgUnit old = before.get(unitId);
            OrgUnit desired = new OrgUnit(unitId, unit.code(), unit.name(), parent, unit.sortOrder(),
                    SourceTerms.unitStatus(unit.state()), head, manager, region, unit.deliveryUnit(), old == null ? null : old.shortName());
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
        Map<OwnObjectId, SourceOrgUnit> byId = new HashMap<>();
        units.forEach(u -> byId.putIfAbsent(u.unitId(), u));
        return units.stream()
                .sorted(Comparator.comparingInt((SourceOrgUnit u) -> depth(u, byId))
                        .thenComparing(u -> u.sortOrder() == null ? Integer.MAX_VALUE : u.sortOrder()))
                .toList();
    }

    private static int depth(SourceOrgUnit unit, Map<OwnObjectId, SourceOrgUnit> byId) {
        int depth = 0;
        Set<OwnObjectId> seen = new HashSet<>();
        SourceOrgUnit at = unit;
        while (at.parentUnitId() != null && seen.add(at.unitId())) {
            at = byId.get(at.parentUnitId());
            if (at == null) {
                break;
            }
            depth++;
        }
        return depth;
    }

    private static EmployeeId known(EmployeeId person, Map<EmployeeId, EmploymentStatus> statusAfter, String subject,
                                IssueKind issue, String role, List<SyncDiff> diffs) {
        if (person != null && !statusAfter.containsKey(person)) {
            diffs.add(SyncDiff.issue(EntityKind.ORG_UNIT, subject, issue, role + " " + person.value() + " is not in the snapshot; stored empty"));
            return null;
        }
        return person;
    }

    /** Several people for one unit: the first in the source's order wins (the source lists them by its own id, smallest first, RULE-356). */
    private static Map<OrgUnitId, EmployeeId> pickSmallest(SourceOrgSnapshot source, SourceRelationshipKind kind, IssueKind multiple,
                                                       List<SyncDiff> diffs) {
        Map<OrgUnitId, List<EmployeeId>> byUnit = new LinkedHashMap<>();
        for (SourceRelationship r : source.relationships()) {
            if (r.kind() == kind && !r.personDeleted()) {
                byUnit.computeIfAbsent(SourceTerms.unit(r.unitId()), k -> new ArrayList<>()).add(SourceTerms.person(r.personId()));
            }
        }
        Map<OrgUnitId, EmployeeId> picked = new LinkedHashMap<>();
        byUnit.forEach((unit, members) -> {
            List<EmployeeId> ordered = members.stream().distinct().toList();
            picked.put(unit, ordered.getFirst());
            if (ordered.size() > 1) {
                diffs.add(SyncDiff.issue(EntityKind.ORG_UNIT, "org-unit:" + unit.value(), multiple,
                        "several people " + ordered.stream().map(u -> String.valueOf(u.value())).toList()
                                + "; the one with the smallest source id, " + ordered.getFirst().value() + ", is used"));
            }
        });
        return picked;
    }
}
