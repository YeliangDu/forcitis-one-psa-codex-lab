package com.forcitis.one.organization.attendance.application;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.organization.attendance.api.AttendanceError;
import com.forcitis.one.organization.attendance.domain.WorkCalendarKey;
import com.forcitis.one.organization.attendance.domain.WorkCalendarQueries;
import org.springframework.stereotype.Service;

@Service
public class GetWorkCalendarYear {
    private final WorkCalendarQueries calendars;

    GetWorkCalendarYear(WorkCalendarQueries calendars) {
        this.calendars = calendars;
    }

    public WorkCalendarYearView execute(int calendarYear) {
        return calendars.find(WorkCalendarKey.defaultFor(calendarYear)).map(WorkCalendarYearView::of)
                .orElseThrow(() -> new DomainException(AttendanceError.ORGANIZATION_ATTENDANCE_YEAR_NOT_FOUND, ErrorKind.NOT_FOUND,
                        "the work calendar of " + calendarYear + " has not been maintained"));
    }
}
