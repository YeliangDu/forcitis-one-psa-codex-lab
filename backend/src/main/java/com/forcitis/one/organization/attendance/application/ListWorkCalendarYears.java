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
        List<Integer> after = calendars.findMaintainedYears(null).stream().sorted(java.util.Comparator.reverseOrder())
                .filter(year -> page.cursorValue() == null || year < Integer.parseInt(page.cursorValue())).toList();
        List<Integer> items = after.stream().limit(page.limit()).toList();
        return CursorPage.of(items, after.size() > items.size() ? PageRequest.encode("work-calendars", String.valueOf(items.getLast())) : null);
    }
}
