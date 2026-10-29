package com.forcitis.one.organization.attendance.domain;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.ValueObject;
import com.forcitis.one.organization.attendance.api.AttendanceError;
import org.jspecify.annotations.Nullable;

/** legalEntityId is null for the default calendar that every legal entity without its own calendar follows. */
@ValueObject
public record WorkCalendarKey(@Nullable LegalEntityId legalEntityId, int calendarYear) {
    public static final int FIRST_YEAR = 2000;
    public static final int LAST_YEAR = 2100;

    public WorkCalendarKey {
        if (calendarYear < FIRST_YEAR || calendarYear > LAST_YEAR) {
            throw new DomainException(AttendanceError.ORGANIZATION_ATTENDANCE_YEAR_OUT_OF_RANGE, ErrorKind.UNPROCESSABLE,
                    "calendar year " + calendarYear + " is outside " + FIRST_YEAR + ".." + LAST_YEAR);
        }
    }

    public static WorkCalendarKey defaultFor(int calendarYear) {
        return new WorkCalendarKey(null, calendarYear);
    }
}
