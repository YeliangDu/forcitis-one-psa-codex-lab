package com.forcitis.one.organization.attendance.domain;

import com.forcitis.one.kernel.LegalEntityId;
import java.util.List;
import java.util.Optional;

/** Reading side of the work calendar. */
public interface WorkCalendarQueries {

    Optional<WorkCalendarYearRecord> find(WorkCalendarKey key);

    /** Years maintained for the calendar of the legal entity (null = the default calendar), newest first, those before the given year, at most limit. */
    List<Integer> findMaintainedYearsBefore(LegalEntityId legalEntityId, Integer beforeYear, int limit);
}
