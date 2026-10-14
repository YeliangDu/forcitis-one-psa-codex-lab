package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.api.EmployeeProfile;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.OrgUnitProfile;
import com.forcitis.one.organization.workforce.domain.OrgSnapshotRepository;
import com.forcitis.one.organization.workforce.domain.OrgUnitStatus;
import com.forcitis.one.organization.workforce.domain.StaffMember;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * The read side other modules use. Every method is a plain read of the snapshot, except that an unknown WeCom user id
 * falls back to the single-person source lookup (RegisterEmployeeOnLogin). Implements the fixed OrgDirectory contract.
 */
@Component
class OrgDirectoryService implements OrgDirectory {

    private final OrgSnapshotRepository snapshots;
    private final RegisterEmployeeOnLogin registerOnLogin;
    private final RegisterEmployeeOnLoginName registerOnLoginName;

    OrgDirectoryService(OrgSnapshotRepository snapshots, RegisterEmployeeOnLogin registerOnLogin,
                        RegisterEmployeeOnLoginName registerOnLoginName) {
        this.snapshots = snapshots;
        this.registerOnLogin = registerOnLogin;
        this.registerOnLoginName = registerOnLoginName;
    }

    @Override
    public Optional<EmployeeProfile> findEmployee(UserId userId) {
        return snapshots.findStaff(userId).map(OrgDirectoryService::profile);
    }

    @Override
    public Optional<EmployeeProfile> findEmployeeByWecomUserId(WecomUserId wecomUserId) {
        return snapshots.findStaffByWecomUserId(wecomUserId)
                .or(() -> registerOnLogin.execute(wecomUserId))
                .map(OrgDirectoryService::profile);
    }

    @Override
    public Optional<EmployeeProfile> findEmployeeByLoginName(String loginName) {
        if (loginName == null || loginName.isBlank()) {
            return Optional.empty();
        }
        String key = loginName.trim();
        return snapshots.findStaffByLoginName(key)
                .or(() -> registerOnLoginName.execute(key))
                .map(OrgDirectoryService::profile);
    }

    @Override
    public Optional<UserId> findDirectLeader(UserId userId) {
        return snapshots.findStaff(userId)
                .filter(s -> s.employee().employmentStatus() == EmploymentStatus.EMPLOYED)
                .map(s -> s.assignment().reportsToEmployeeId())
                .flatMap(snapshots::findStaff)
                .filter(leader -> leader.employee().employmentStatus() == EmploymentStatus.EMPLOYED)
                .map(StaffMember::userId);
    }

    @Override
    public Optional<UserId> findOrgUnitHead(OrgUnitId orgUnitId) {
        return snapshots.findOrgUnit(orgUnitId).map(unit -> unit.headEmployeeId());
    }

    @Override
    public Optional<UserId> findRegionalReceivablesManager(OrgUnitId orgUnitId) {
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

    private static EmployeeProfile profile(StaffMember staff) {
        return new EmployeeProfile(staff.userId(), staff.employee().displayName(), staff.assignment().orgUnitId(),
                staff.employee().wecomUserId(), staff.employee().employmentStatus(), staff.employee().feeEarner());
    }
}
