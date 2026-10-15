package com.forcitis.one.organization.attendance.domain;

import com.forcitis.one.kernel.LegalEntityId;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Reading side of the work calendar. */
public interface WorkCalendarQueries {

    Optional<WorkCalendarYearRecord> find(WorkCalendarKey key);

    /** Years maintained for the calendar of the legal entity (null = the default calendar), newest first, those before the given year, at most limit. */
    List<Integer> findMaintainedYearsBefore(@Nullable LegalEntityId legalEntityId, @Nullable Integer beforeYear, int limit);
}
