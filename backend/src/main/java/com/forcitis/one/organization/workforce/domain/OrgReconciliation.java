package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.platform.integration.api.SourceOrgSnapshot;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Expected state = a sync from an empty snapshot with the same reconciler, so only real drift shows up. */
public final class OrgReconciliation {
    private OrgReconciliation() {
    }

    public static OrgReconciliationReport compare(SourceOrgSnapshot source, OrgSnapshot snapshot) {
        Reconciliation expected = SnapshotReconciler.reconcile(OrgSnapshot.empty(), source);
        Map<OrgUnitId, OrgUnit> expectedUnits = new OrgSnapshot(expected.orgUnits(), List.of(), List.of()).orgUnitsById();
        Map<UserId, StaffEmployee> expectedStaff = new OrgSnapshot(List.of(), List.of(), expected.staff()).staffById();

        // The snapshot keeps revoked units; they are not part of what the source returns.
        Map<OrgUnitId, OrgUnit> actualUnits = new java.util.LinkedHashMap<>();
        snapshot.orgUnits().stream().filter(u -> u.status() != OrgUnitStatus.REVOKED).forEach(u -> actualUnits.put(u.orgUnitId(), u));
        Map<UserId, StaffEmployee> actualStaff = snapshot.staffById();

        List<String> missing = new ArrayList<>();
        List<String> unexpected = new ArrayList<>();
        List<String> parentMismatches = new ArrayList<>();
        List<String> reportingMismatches = new ArrayList<>();
        List<String> responsibilityMismatches = new ArrayList<>();
        List<String> wecomMismatches = new ArrayList<>();

        expectedUnits.forEach((id, unit) -> {
            OrgUnit actual = actualUnits.get(id);
            if (actual == null) {
                missing.add("org-unit:" + id.value());
            } else if (!Objects.equals(unit.parentOrgUnitId(), actual.parentOrgUnitId())) {
                parentMismatches.add("org-unit:" + id.value());
            } else if (!Objects.equals(unit.headEmployeeId(), actual.headEmployeeId())
                    || !Objects.equals(unit.regionalReceivablesManagerId(), actual.regionalReceivablesManagerId())) {
                responsibilityMismatches.add("org-unit:" + id.value());
            }
        });
        actualUnits.keySet().stream().filter(id -> !expectedUnits.containsKey(id))
                .forEach(id -> unexpected.add("org-unit:" + id.value()));

        expectedStaff.forEach((id, staff) -> {
            if (staff.employee().employmentStatus() != EmploymentStatus.EMPLOYED) {
                return;
            }
            StaffEmployee actual = actualStaff.get(id);
            if (actual == null) {
                missing.add("employee:" + id.value());
            } else if (actual.employee().employmentStatus() != EmploymentStatus.EMPLOYED) {
                unexpected.add("employee:" + id.value() + " (separated in snapshot, employed in source)");
            } else if (!Objects.equals(staff.assignment().reportsToEmployeeId(), actual.assignment().reportsToEmployeeId())) {
                reportingMismatches.add("employee:" + id.value());
            } else if (!Objects.equals(staff.employee().wecomUserId(), actual.employee().wecomUserId())) {
                wecomMismatches.add("employee:" + id.value());
            }
        });
        actualStaff.forEach((id, staff) -> {
            StaffEmployee wanted = expectedStaff.get(id);
            boolean employedInSnapshot = staff.employee().employmentStatus() == EmploymentStatus.EMPLOYED;
            if (employedInSnapshot && (wanted == null || wanted.employee().employmentStatus() != EmploymentStatus.EMPLOYED)) {
                unexpected.add("employee:" + id.value() + " (employed in snapshot, not employed in source)");
            }
        });

        int sourceEmployed = (int) expectedStaff.values().stream()
                .filter(s -> s.employee().employmentStatus() == EmploymentStatus.EMPLOYED).count();
        int sourceActiveUnits = (int) expectedUnits.values().stream().filter(u -> u.status() == OrgUnitStatus.ACTIVE).count();
        int snapshotActiveUnits = (int) actualUnits.values().stream().filter(u -> u.status() == OrgUnitStatus.ACTIVE).count();
        return new OrgReconciliationReport(sourceEmployed, snapshot.employedCount(), expectedUnits.size(), actualUnits.size(),
                sourceActiveUnits, snapshotActiveUnits, missing, unexpected, parentMismatches, reportingMismatches,
                responsibilityMismatches, wecomMismatches);
    }
}
