package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.organization.workforce.api.ActingDutyEndCause;
import com.forcitis.one.organization.workforce.api.ActingDutyId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PositionId;
import com.forcitis.one.organization.workforce.domain.EmployeeRosterQueries.ActingDutyRow;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/** A duty fact with inclusive display dates and business-day state; the covered name is absent when outside the reader's scope. */
public record ActingDutyView(ActingDutyId actingDutyId, EmployeeId employeeId, OrgUnitId coveredOrgUnitId,
                             @Nullable PositionId coveredPositionId, @Nullable EmployeeId coveredEmployeeId,
                             @Nullable String coveredEmployeeName, LocalDate firstDay, @Nullable LocalDate lastDay,
                             State state, @Nullable ActingDutyEndCause endCause, String reason) {
    static ActingDutyView of(ActingDutyRow row, @Nullable String coveredName, LocalDate today) {
        LocalDate end = row.endedOn() == null ? row.endsBefore() : row.endedOn();
        LocalDate last = end.minusDays(1);
        return new ActingDutyView(row.actingDutyId(), row.employeeId(), row.coveredOrgUnitId(), row.coveredPositionId(), row.coveredEmployeeId(),
                coveredName, row.startsOn(), last.isBefore(row.startsOn()) ? null : last,
                row.endedOn() != null || !today.isBefore(row.endsBefore()) ? State.ENDED : State.ACTIVE, row.endCause(), row.reason());
    }

    /** Pending starts have no duty row; a stored duty is active or ended. */
    public enum State { ACTIVE, ENDED }
}
