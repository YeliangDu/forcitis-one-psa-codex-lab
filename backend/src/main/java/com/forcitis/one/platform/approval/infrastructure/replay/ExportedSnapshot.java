package com.forcitis.one.platform.approval.infrastructure.replay;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.api.EmployeeProfile;
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
        return Optional.ofNullable(employees.get(employeeId.value())).map(e -> new EmployeeProfile(new EmployeeId(e.userId()), "exported",
                new OrgUnitId(e.orgUnitId()), null, e.employed() ? EmploymentStatus.EMPLOYED : EmploymentStatus.SEPARATED, false));
    }

    @Override
    public Optional<EmployeeProfile> findEmployeeByWecomUserId(WecomUserId wecomUserId) {
        return Optional.empty();
    }

    @Override
    public Optional<EmployeeProfile> findEmployeeByLoginName(String loginName) {
        return Optional.empty();
    }

    @Override
    public Optional<EmployeeId> findSupervisor(EmployeeId employeeId) {
        return Optional.ofNullable(employees.get(employeeId.value())).map(ReplayExport.EmployeeRecord::supervisorUserId)
                .filter(this::employed).map(EmployeeId::new);
    }

    @Override
    public Optional<EmployeeId> findOrgUnitHead(OrgUnitId orgUnitId) {
        return Optional.ofNullable(units.get(orgUnitId.value())).map(ReplayExport.OrgUnitRecord::headUserId).map(EmployeeId::new);
    }

    @Override
    public Optional<EmployeeId> findRegionalReceivablesManager(OrgUnitId orgUnitId) {
        return Optional.ofNullable(units.get(orgUnitId.value())).map(ReplayExport.OrgUnitRecord::regionalReceivablesManagerId)
                .map(EmployeeId::new);
    }

    @Override
    public Optional<OrgUnitProfile> findOrgUnit(OrgUnitId orgUnitId) {
        return Optional.ofNullable(units.get(orgUnitId.value())).map(u -> new OrgUnitProfile(new OrgUnitId(u.orgUnitId()), "exported",
                u.parentOrgUnitId() == null ? null : new OrgUnitId(u.parentOrgUnitId()), u.active()));
    }

    @Override
    public List<OrgUnitId> findAncestors(OrgUnitId orgUnitId) {
        List<OrgUnitId> ancestors = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        seen.add(orgUnitId.value());
        ReplayExport.OrgUnitRecord current = units.get(orgUnitId.value());
        while (current != null && current.parentOrgUnitId() != null && seen.add(current.parentOrgUnitId())) {
            ancestors.add(new OrgUnitId(current.parentOrgUnitId()));
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
