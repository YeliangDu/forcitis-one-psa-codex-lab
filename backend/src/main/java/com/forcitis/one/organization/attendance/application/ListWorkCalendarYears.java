package com.forcitis.one.organization.attendance.application;

import com.forcitis.one.kernel.CursorPage;
import com.forcitis.one.kernel.PageRequest;
import com.forcitis.one.organization.attendance.domain.WorkCalendarQueries;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListWorkCalendarYears {
    private final WorkCalendarQueries calendars;

    ListWorkCalendarYears(WorkCalendarQueries calendars) {
        this.calendars = calendars;
    }

    /** Newest year first; the cursor is the year the previous page ended with. */
    @Transactional(readOnly = true)
    public CursorPage<Integer> execute(PageRequest page) {
        List<Integer> found = calendars.findMaintainedYearsBefore(null, page.cursorNumber(), page.limit() + 1);
        List<Integer> items = found.stream().limit(page.limit()).toList();
        return CursorPage.of(items, found.size() > items.size() ? PageRequest.encode("work-calendars", String.valueOf(items.getLast())) : null);
    }
}
