package com.forcitis.one.organization.attendance.web;

import com.forcitis.one.kernel.CursorPage;
import com.forcitis.one.kernel.ExpectedVersion;
import com.forcitis.one.kernel.PageRequest;
import com.forcitis.one.organization.attendance.application.DayEntry;
import com.forcitis.one.organization.attendance.application.GetWorkCalendarYear;
import com.forcitis.one.organization.attendance.application.ListWorkCalendarYears;
import com.forcitis.one.organization.attendance.application.MaintainWorkCalendarYear;
import com.forcitis.one.organization.attendance.application.WorkCalendarYearView;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    CursorPage<MaintainedYearResponse> maintainedYears(@RequestParam(required = false) Integer limit, @RequestParam(required = false) String cursor) {
        CursorPage<Integer> page = list.execute(PageRequest.of(limit, cursor, "work-calendars"));
        return CursorPage.of(page.items().stream().map(MaintainedYearResponse::new).toList(), page.nextCursor());
    }

    @GetMapping("/{calendarYear}")
    @PreAuthorize("hasAuthority('" + AttendancePermissions.WORK_CALENDAR_READ + "')")
    ResponseEntity<WorkCalendarYearResponse> view(@PathVariable int calendarYear) {
        WorkCalendarYearView year = get.execute(calendarYear);
        return ResponseEntity.ok().eTag(ExpectedVersion.etag(year.version())).body(WorkCalendarYearResponse.of(year));
    }

    /** A year that was never maintained is created with If-None-Match: *, a maintained one is replaced with If-Match. */
    @PutMapping("/{calendarYear}")
    @PreAuthorize("hasAuthority('" + AttendancePermissions.WORK_CALENDAR_UPDATE + "')")
    ResponseEntity<WorkCalendarYearResponse> maintain(@PathVariable int calendarYear, @RequestBody MaintainWorkCalendarRequest request,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
        List<DayEntry> days = request.days().stream().map(d -> new DayEntry(d.calendarDate(), d.dayKind() == null ? null : d.dayKind().name())).toList();
        WorkCalendarYearView year = maintain.execute(calendarYear, ExpectedVersion.fromHeaders(ifMatch, ifNoneMatch), request.confirmed(), days);
        return ResponseEntity.ok().eTag(ExpectedVersion.etag(year.version())).body(WorkCalendarYearResponse.of(year));
    }
}
