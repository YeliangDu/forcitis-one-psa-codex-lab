package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PositionId;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Persistence of the employee's acting-duty facts, serialized by the employee lock. */
public interface ActingDutyRepository {
    void insert(ActingDuty duty);

    List<ActingDuty> findOverlap(EmployeeId employee, OrgUnitId unit, @Nullable PositionId position, LocalDate from, LocalDate to);

    List<ActingDuty> listOpen(EmployeeId employee);
}
