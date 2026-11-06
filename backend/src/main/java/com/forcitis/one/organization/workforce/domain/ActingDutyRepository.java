package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.organization.workforce.api.ActingDutyEndCause;
import com.forcitis.one.organization.workforce.api.ActingDutyId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PositionId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Persistence of the employee's acting-duty facts, serialized by the employee lock. */
public interface ActingDutyRepository {
    void insert(ActingDuty duty);

    boolean lockEmployee(EmployeeId employee);

    void saveEventSequence(EmployeeId employee, long sequence);

    Optional<ActingDuty> find(ActingDutyId id);

    List<ActingDutyId> openDue(LocalDate today, int limit);

    void updateEnd(ActingDuty loaded, LocalDate endedOn, ActingDutyEndCause cause, Instant at);

    void updateEndsBefore(ActingDuty loaded, LocalDate endsBefore, Instant at);

    List<ActingDuty> findOverlap(EmployeeId employee, OrgUnitId unit, @Nullable PositionId position, LocalDate from, LocalDate to);

    List<ActingDuty> listOpen(EmployeeId employee);
}
