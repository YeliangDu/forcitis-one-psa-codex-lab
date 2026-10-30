package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.api.EmployeeProfile;
import com.forcitis.one.organization.workforce.api.EmploymentVerdict;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.LoginLookup;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.OrgUnitProfile;
import com.forcitis.one.organization.workforce.domain.OrgSnapshotQueries;
import com.forcitis.one.organization.workforce.domain.OrgUnitStatus;
import com.forcitis.one.organization.workforce.domain.StaffEmployee;
import com.forcitis.one.organization.workforce.domain.UnpaidLeaveQueries;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class OrgDirectoryService implements OrgDirectory {
    private final OrgSnapshotQueries snapshots;
    private final RegisterEmployeeOnLogin registerOnLogin;
    private final RegisterEmployeeOnLoginName registerOnLoginName;
    private final SnapshotFreshness freshness;
    private final UnpaidLeaveQueries leaves;

    OrgDirectoryService(OrgSnapshotQueries snapshots, RegisterEmployeeOnLogin registerOnLogin,
                        RegisterEmployeeOnLoginName registerOnLoginName, SnapshotFreshness freshness, UnpaidLeaveQueries leaves) {
        this.leaves = leaves;
        this.freshness = freshness;
        this.snapshots = snapshots;
        this.registerOnLogin = registerOnLogin;
        this.registerOnLoginName = registerOnLoginName;
    }

    @Override
    public Optional<EmployeeProfile> findEmployee(EmployeeId employeeId) {
        return snapshots.findStaff(employeeId).map(OrgDirectoryService::profile);
    }

    @Override
    public Optional<EmployeeProfile> findEmployeeByLogin(LoginLookup lookup) {
        return switch (lookup) {
            case LoginLookup.ByWecomUser wecom -> findEmployeeByWecomUser(wecom.wecomUserId());
            case LoginLookup.ByLoginName login -> findEmployeeByLoginName(login.loginName());
        };
    }

    @Override
    public boolean isOnUnpaidLeave(EmployeeId employeeId, LocalDate day) {
        return leaves.isOnLeave(employeeId, day);
    }

    @Override
    public EmploymentVerdict employmentOf(EmployeeId employeeId) {
        Optional<StaffEmployee> staff = snapshots.findStaff(employeeId);
        if (staff.isPresent() && staff.get().employee().employmentStatus() != EmploymentStatus.EMPLOYED) {
            return EmploymentVerdict.SEPARATED;
        }
        if (!freshness.isCurrent()) {
            return EmploymentVerdict.SNAPSHOT_UNAVAILABLE;
        }
        return staff.isPresent() ? EmploymentVerdict.EMPLOYED : EmploymentVerdict.NOT_LISTED;
    }

    private Optional<EmployeeProfile> findEmployeeByWecomUser(WecomUserId wecomUserId) {
        return snapshots.findStaffByWecomUser(wecomUserId)
                .or(() -> registerOnLogin.execute(wecomUserId))
                .map(OrgDirectoryService::profile);
    }

    private Optional<EmployeeProfile> findEmployeeByLoginName(String loginName) {
        if (loginName == null || loginName.isBlank()) {
            return Optional.empty();
        }
        String key = loginName.trim();
        return snapshots.findStaffByLoginName(key)
                .or(() -> registerOnLoginName.execute(key))
                .map(OrgDirectoryService::profile);
    }

    @Override
    public Optional<EmployeeId> findSupervisor(EmployeeId employeeId) {
        return snapshots.findStaff(employeeId)
                .filter(s -> s.employee().employmentStatus() == EmploymentStatus.EMPLOYED)
                .map(s -> s.assignment().reportsToEmployeeId())
                .flatMap(snapshots::findStaff)
                .filter(leader -> leader.employee().employmentStatus() == EmploymentStatus.EMPLOYED)
                .map(StaffEmployee::employeeId);
    }

    @Override
    public Optional<EmployeeId> findOrgUnitHead(OrgUnitId orgUnitId) {
        return snapshots.findOrgUnit(orgUnitId).map(unit -> unit.headEmployeeId());
    }

    @Override
    public Optional<EmployeeId> findRegionalReceivablesManager(OrgUnitId orgUnitId) {
        return snapshots.findOrgUnit(orgUnitId).map(unit -> unit.regionalReceivablesManagerId());
    }

    @Override
    public Optional<OrgUnitProfile> findOrgUnit(OrgUnitId orgUnitId) {
        return snapshots.findOrgUnit(orgUnitId).map(unit -> new OrgUnitProfile(unit.orgUnitId(), unit.name(),
                unit.parentOrgUnitId(), unit.status() == OrgUnitStatus.ACTIVE));
    }

    @Override
    public List<OrgUnitId> findAncestors(OrgUnitId orgUnitId) {
        return snapshots.findAncestors(orgUnitId);
    }

    private static EmployeeProfile profile(StaffEmployee staff) {
        return new EmployeeProfile(staff.employeeId(), staff.employee().displayName(), staff.assignment().orgUnitId(),
                staff.employee().wecomUserId(), staff.employee().employmentStatus(), staff.employee().feeEarner());
    }
}
