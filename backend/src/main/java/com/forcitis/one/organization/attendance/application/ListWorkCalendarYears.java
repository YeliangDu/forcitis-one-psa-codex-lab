package com.forcitis.one.organization.attendance.application;

import com.forcitis.one.organization.attendance.domain.WorkCalendarQueries;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ListWorkCalendarYears {
    private final WorkCalendarQueries calendars;

    ListWorkCalendarYears(WorkCalendarQueries calendars) {
        this.calendars = calendars;
    }

    public List<Integer> execute() {
        return calendars.findMaintainedYears(null);
    }
}
