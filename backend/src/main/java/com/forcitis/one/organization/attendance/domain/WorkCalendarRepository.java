package com.forcitis.one.organization.attendance.domain;

import java.util.Optional;

public interface WorkCalendarRepository {

    Optional<WorkCalendarYear> find(WorkCalendarKey key);

    /** Inserts a year that was never saved, or replaces a saved one only if nobody saved it since it was loaded. */
    void save(WorkCalendarYear calendarYear);
}
