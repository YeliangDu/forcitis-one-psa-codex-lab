package com.forcitis.one.organization.attendance.application;

import com.forcitis.one.organization.attendance.domain.WorkCalendarRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ListWorkCalendarYears {
    private final WorkCalendarRepository calendars;

    ListWorkCalendarYears(WorkCalendarRepository calendars) {
        this.calendars = calendars;
    }

    public List<Integer> execute() {
        return calendars.findMaintainedYears(null);
    }
}
