package com.forcitis.one.organization.attendance.application;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The exception to the lock on days before today (data scorecard T2): its own permission, held by the organisation data administrator only. */
@Service
public class MaintainLockedWorkCalendarDays {
    private final CalendarMaintenance maintenance;

    MaintainLockedWorkCalendarDays(CalendarMaintenance maintenance) {
        this.maintenance = maintenance;
    }

    @Transactional
    @PreAuthorize("hasAuthority('organization.attendance:locked-day:update')")
    public WorkCalendarYearView execute(MaintainWorkCalendarCommand command) {
        return maintenance.run(command, true);
    }
}
