package com.forcitis.one.organization.attendance.domain;

import com.forcitis.one.kernel.LegalEntityId;
import java.util.List;
import java.util.Optional;

public interface WorkCalendarRepository {

    Optional<WorkCalendarYear> find(WorkCalendarKey key);

    /** Years maintained for the calendar of the legal entity (null = the default calendar), ascending. */
    List<Integer> findMaintainedYears(LegalEntityId legalEntityId);

    /** Inserts a year that was never saved, or replaces a saved one only if nobody saved it since it was loaded. */
    void save(WorkCalendarYear calendarYear);
}
