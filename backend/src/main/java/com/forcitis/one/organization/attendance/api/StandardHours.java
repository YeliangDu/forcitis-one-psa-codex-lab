package com.forcitis.one.organization.attendance.api;

import com.forcitis.one.kernel.LegalEntityId;
import java.time.LocalDate;

/**
 * Standard working minutes of one legal entity on one date. The answer follows the work calendar (rest days and holidays are 0,
 * make-up workdays use the adjusted minutes, a per-date override wins on workdays) and fails with
 * {@link AttendanceError#ORGANIZATION_ATTENDANCE_STANDARD_HOURS_NOT_FOUND} when no version covers the date; no default hours exist.
 */
public interface StandardHours {

    int minutesOn(LegalEntityId legalEntityId, LocalDate date);
}
