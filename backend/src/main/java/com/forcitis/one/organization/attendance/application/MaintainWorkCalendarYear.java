package com.forcitis.one.organization.attendance.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Only the default calendar is maintained for now; the legal-entity calendar is reserved in the model and the schema. Days before today stay as they are. */
@Service
public class MaintainWorkCalendarYear {
    private final CalendarMaintenance maintenance;

    MaintainWorkCalendarYear(CalendarMaintenance maintenance) {
        this.maintenance = maintenance;
    }

    @Transactional
    public WorkCalendarYearView execute(MaintainWorkCalendarCommand command) {
        return maintenance.run(command, false);
    }
}
