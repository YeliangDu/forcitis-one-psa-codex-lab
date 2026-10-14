package com.forcitis.one.organization.attendance.application;

import com.forcitis.one.kernel.CallerPermissions;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.ExpectedVersion;
import com.forcitis.one.organization.attendance.api.AttendanceError;
import com.forcitis.one.organization.attendance.api.AttendancePermissions;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maintenance that may change days before today, which moves deadlines already worked out. The permission is checked here, so no
 * entry point can reach it without it.
 */
@Service
public class MaintainLockedWorkCalendarDays {
    private final WorkCalendarMaintenance maintenance;
    private final CallerPermissions caller;

    MaintainLockedWorkCalendarDays(WorkCalendarMaintenance maintenance, CallerPermissions caller) {
        this.maintenance = maintenance;
        this.caller = caller;
    }

    @Transactional
    public WorkCalendarYearView execute(int calendarYear, ExpectedVersion expected, List<DayEntry> days) {
        if (!caller.holds(AttendancePermissions.WORK_CALENDAR_UPDATE_LOCKED_DAYS)) {
            throw new DomainException(AttendanceError.ORGANIZATION_ATTENDANCE_LOCKED_DAY_FORBIDDEN, ErrorKind.FORBIDDEN,
                    "changing days before today needs " + AttendancePermissions.WORK_CALENDAR_UPDATE_LOCKED_DAYS);
        }
        return maintenance.maintain(calendarYear, expected, true, days);
    }
}
