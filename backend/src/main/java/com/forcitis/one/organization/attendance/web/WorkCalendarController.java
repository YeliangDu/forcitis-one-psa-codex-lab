package com.forcitis.one.organization.attendance.web;

import com.forcitis.one.organization.attendance.application.DayEntry;
import com.forcitis.one.organization.attendance.application.GetWorkCalendarYear;
import com.forcitis.one.organization.attendance.application.ListWorkCalendarYears;
import com.forcitis.one.organization.attendance.application.MaintainWorkCalendarCommand;
import com.forcitis.one.organization.attendance.application.MaintainWorkCalendarYear;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organization/work-calendars")
class WorkCalendarController {
    private final ListWorkCalendarYears list;
    private final GetWorkCalendarYear get;
    private final MaintainWorkCalendarYear maintain;

    WorkCalendarController(ListWorkCalendarYears list, GetWorkCalendarYear get, MaintainWorkCalendarYear maintain) {
        this.list = list;
        this.get = get;
        this.maintain = maintain;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + AttendancePermissions.WORK_CALENDAR_READ + "')")
    List<Integer> maintainedYears() {
        return list.execute();
    }

    @GetMapping("/{calendarYear}")
    @PreAuthorize("hasAuthority('" + AttendancePermissions.WORK_CALENDAR_READ + "')")
    WorkCalendarYearResponse view(@PathVariable int calendarYear) {
        return WorkCalendarYearResponse.of(get.execute(calendarYear));
    }

    @PutMapping("/{calendarYear}")
    @PreAuthorize("hasAuthority('" + AttendancePermissions.WORK_CALENDAR_UPDATE + "')")
    WorkCalendarYearResponse maintain(@PathVariable int calendarYear, @RequestBody MaintainWorkCalendarRequest request) {
        List<DayEntry> days = request.days().stream().map(d -> new DayEntry(d.calendarDate(), d.dayKind())).toList();
        return WorkCalendarYearResponse.of(maintain.execute(new MaintainWorkCalendarCommand(calendarYear, days)));
    }
}
