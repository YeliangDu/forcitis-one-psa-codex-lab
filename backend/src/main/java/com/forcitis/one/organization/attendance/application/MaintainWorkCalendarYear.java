package com.forcitis.one.organization.attendance.application;

import com.forcitis.one.kernel.ExpectedVersion;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A change to a day before today is refused here (422): that is what MaintainLockedWorkCalendarDays is for. */
@Service
public class MaintainWorkCalendarYear {
    private final WorkCalendarMaintenance maintenance;

    MaintainWorkCalendarYear(WorkCalendarMaintenance maintenance) {
        this.maintenance = maintenance;
    }

    @Transactional
    public WorkCalendarYearView execute(int calendarYear, ExpectedVersion expected, List<DayEntry> days) {
        return maintenance.maintain(calendarYear, expected, false, days);
    }
}
