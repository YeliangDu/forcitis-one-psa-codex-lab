package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.organization.workforce.api.PositionId;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record OrgSnapshot(List<OrgUnit> orgUnits, List<Position> positions, List<StaffEmployee> staff) {
    public OrgSnapshot {
        orgUnits = List.copyOf(orgUnits);
        positions = List.copyOf(positions);
        staff = List.copyOf(staff);
    }

    public static OrgSnapshot empty() {
        return new OrgSnapshot(List.of(), List.of(), List.of());
    }

    public Map<OrgUnitId, OrgUnit> orgUnitsById() {
        Map<OrgUnitId, OrgUnit> byId = new LinkedHashMap<>();
        orgUnits.forEach(u -> byId.put(u.orgUnitId(), u));
        return byId;
    }

    public Map<PositionId, Position> positionsById() {
        Map<PositionId, Position> byId = new LinkedHashMap<>();
        positions.forEach(p -> byId.put(p.positionId(), p));
        return byId;
    }

    public Map<EmployeeId, StaffEmployee> staffById() {
        Map<EmployeeId, StaffEmployee> byId = new LinkedHashMap<>();
        staff.forEach(s -> byId.put(s.employeeId(), s));
        return byId;
    }

    public int activeOrgUnitCount() {
        return (int) orgUnits.stream().filter(u -> u.status() == OrgUnitStatus.ACTIVE).count();
    }

    public int employedCount() {
        return (int) staff.stream().filter(s -> s.employee().employmentStatus() == EmploymentStatus.EMPLOYED).count();
    }
}
