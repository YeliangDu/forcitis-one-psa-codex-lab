package com.forcitis.one.platform.approval.infrastructure.replay;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.EmployeeProfile;
import com.forcitis.one.organization.workforce.api.EmploymentVerdict;
import com.forcitis.one.organization.workforce.api.LoginLookup;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.OrgUnitProfile;
import com.forcitis.one.platform.identity.api.RoleCode;
import com.forcitis.one.platform.identity.api.RoleDirectory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Presented through the live read contracts so the very same AssigneeResolver runs in the replay. */
final class ExportedSnapshot implements OrgDirectory, RoleDirectory {

    private final Map<Long, ReplayExport.EmployeeRecord> employees = new HashMap<>();
    private final Map<Long, ReplayExport.OrgUnitRecord> units = new HashMap<>();
    private final Map<String, Set<Long>> roleHolders = new HashMap<>();

    ExportedSnapshot(ReplayExport.Snapshot snapshot) {
        snapshot.employees().forEach(e -> employees.put(e.userId(), e));
        snapshot.orgUnits().forEach(u -> units.put(u.orgUnitId(), u));
        snapshot.roleHolders().forEach((role, holders) -> roleHolders.put(role, new LinkedHashSet<>(holders)));
    }

    @Override
    public Optional<EmployeeProfile> findEmployee(EmployeeId employeeId) {
        return Optional.ofNullable(employees.get(ExportedIds.person(employeeId))).map(e -> new EmployeeProfile(ExportedIds.employee(e.userId()), "exported",
                ExportedIds.orgUnit(e.orgUnitId()), null, e.employed() ? EmploymentStatus.EMPLOYED : EmploymentStatus.SEPARATED, false));
    }

    @Override
    public Optional<EmployeeProfile> findEmployeeByLogin(LoginLookup lookup) {
        return Optional.empty();
    }

    /** An export carries no leave flag (it predates the flag), so nobody in a replay is on leave. */
    @Override
    public boolean isOnUnpaidLeave(EmployeeId employeeId, java.time.LocalDate day) {
        return false;
    }

    /** An export carries one state per person, so the day does not matter. */
    @Override
    public Optional<EmploymentStatus> employmentOn(EmployeeId employeeId, java.time.LocalDate day) {
        return findEmployee(employeeId).map(EmployeeProfile::employmentStatus);
    }

    @Override
    public EmploymentVerdict employmentOf(EmployeeId employeeId) {
        return findEmployee(employeeId).map(e -> e.employmentStatus() == EmploymentStatus.EMPLOYED ? EmploymentVerdict.EMPLOYED : EmploymentVerdict.SEPARATED)
                .orElse(EmploymentVerdict.NOT_LISTED);
    }

    @Override
    public Optional<EmployeeId> findSupervisor(EmployeeId employeeId) {
        return Optional.ofNullable(employees.get(ExportedIds.person(employeeId))).map(ReplayExport.EmployeeRecord::supervisorUserId)
                .filter(this::employed).map(ExportedIds::employee);
    }

    @Override
    public Optional<EmployeeId> findOrgUnitHead(OrgUnitId orgUnitId) {
        return Optional.ofNullable(units.get(ExportedIds.unit(orgUnitId))).map(ReplayExport.OrgUnitRecord::headUserId).map(ExportedIds::employee);
    }

    @Override
    public Optional<EmployeeId> findRegionalReceivablesManager(OrgUnitId orgUnitId) {
        return Optional.ofNullable(units.get(ExportedIds.unit(orgUnitId))).map(ReplayExport.OrgUnitRecord::regionalReceivablesManagerId)
                .map(ExportedIds::employee);
    }

    @Override
    public Optional<OrgUnitProfile> findOrgUnit(OrgUnitId orgUnitId) {
        return Optional.ofNullable(units.get(ExportedIds.unit(orgUnitId))).map(u -> new OrgUnitProfile(ExportedIds.orgUnit(u.orgUnitId()), "exported",
                u.parentOrgUnitId() == null ? null : ExportedIds.orgUnit(u.parentOrgUnitId()), u.active()));
    }

    @Override
    public List<OrgUnitId> findAncestors(OrgUnitId orgUnitId) {
        List<OrgUnitId> ancestors = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        seen.add(ExportedIds.unit(orgUnitId));
        ReplayExport.OrgUnitRecord current = units.get(ExportedIds.unit(orgUnitId));
        while (current != null && current.parentOrgUnitId() != null && seen.add(current.parentOrgUnitId())) {
            ancestors.add(ExportedIds.orgUnit(current.parentOrgUnitId()));
            current = units.get(current.parentOrgUnitId());
        }
        return ancestors;
    }

    @Override
    public Set<UserId> findRoleHolders(RoleCode roleCode) {
        Set<UserId> holders = new LinkedHashSet<>();
        for (Long holder : roleHolders.getOrDefault(roleCode.value(), Set.of())) {
            if (employed(holder)) {
                holders.add(ExportedAccounts.account(holder));
            }
        }
        return holders;
    }

    boolean employed(Long userId) {
        ReplayExport.EmployeeRecord e = userId == null ? null : employees.get(userId);
        return e != null && e.employed();
    }
}
