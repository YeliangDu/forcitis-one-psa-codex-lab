package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.domain.PersonnelCommandQueries;
import com.forcitis.one.organization.workforce.api.Availability;
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
import com.forcitis.one.organization.workforce.domain.EmployeeVersionQueries;
import java.time.LocalDate;
import com.forcitis.one.kernel.BusinessZone;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class OrgDirectoryService implements OrgDirectory {
    private final OrgSnapshotQueries snapshots;
    private final EmployeeVersionQueries versions;
    private final Clock clock;
    private final BusinessZone zone;
    private final PersonnelCommandQueries commands;

    OrgDirectoryService(OrgSnapshotQueries snapshots, EmployeeVersionQueries versions, PersonnelCommandQueries commands, Clock clock, BusinessZone zone) {
        this.commands = commands;
        this.versions = versions;
        this.clock = clock;
        this.zone = zone;
        this.snapshots = snapshots;
    }

    @Override
    public Optional<EmployeeProfile> findEmployee(EmployeeId employeeId) {
        return snapshots.findStaff(employeeId).map(this::profile);
    }

    @Override
    public Optional<EmployeeProfile> findEmployeeByLogin(LoginLookup lookup) {
        return switch (lookup) {
            case LoginLookup.ByWecomUser wecom -> findEmployeeByWecomUser(wecom.wecomUserId());
        };
    }

    @Override
    public Availability availabilityOn(EmployeeId employeeId, LocalDate day) {
        return new Availability(versions.isOnLeave(employeeId, day), commands.isHeld(employeeId), commands.isLeaving(employeeId));
    }

    @Override
    public Optional<EmploymentStatus> employmentOn(EmployeeId employeeId, LocalDate day) {
        if (versions.hasVersions(employeeId)) {
            return versions.employmentOn(employeeId, day);
        }
        return snapshots.findStaff(employeeId).map(staff -> staff.employee().employmentStatus());
    }

    @Override
    public EmploymentVerdict employmentOf(EmployeeId employeeId) {
        if (versions.hasVersions(employeeId)) {
            return versions.employmentOn(employeeId, zone.today(clock))
                    .map(status -> status == EmploymentStatus.EMPLOYED ? EmploymentVerdict.EMPLOYED : EmploymentVerdict.SEPARATED)
                    .orElse(EmploymentVerdict.NOT_LISTED);
        }
        Optional<StaffEmployee> staff = snapshots.findStaff(employeeId);
        if (staff.isPresent() && staff.get().employee().employmentStatus() != EmploymentStatus.EMPLOYED) {
            return EmploymentVerdict.SEPARATED;
        }
        return staff.isPresent() ? EmploymentVerdict.EMPLOYED : EmploymentVerdict.NOT_LISTED;
    }

    private Optional<EmployeeProfile> findEmployeeByWecomUser(WecomUserId wecomUserId) {
        return snapshots.findStaffByWecomUser(wecomUserId).map(this::profile);
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

    private EmployeeProfile profile(StaffEmployee staff) {
        return new EmployeeProfile(staff.employeeId(), staff.employee().displayName(), staff.assignment().orgUnitId(),
                staff.employee().wecomUserId(), staff.employee().employmentStatus(), staff.employee().feeEarner());
    }
}
