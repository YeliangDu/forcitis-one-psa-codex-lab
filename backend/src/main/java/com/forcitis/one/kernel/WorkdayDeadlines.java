package com.forcitis.one.kernel;

import java.time.Instant;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * A deadline counted in workdays, answered by whoever owns the work calendar (F-1 card A-3), so a module that must not depend on it can still ask.
 * Fails when a year the answer depends on has not been maintained; never assumes "all workdays".
 */
public interface WorkdayDeadlines {

    /** End (24:00 business zone) of the n-th workday after startDate in the calendar of the legal entity, or in the default calendar when none is known (null); startDate itself never counts. n >= 1. */
    Instant endOfWorkdayAfter(LocalDate startDate, int n, @Nullable LegalEntityId legalEntityId);
}
