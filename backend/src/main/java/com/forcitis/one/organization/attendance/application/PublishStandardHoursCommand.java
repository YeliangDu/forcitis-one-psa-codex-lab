package com.forcitis.one.organization.attendance.application;

import com.forcitis.one.kernel.LegalEntityId;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** legalEntityId null publishes for the DEFAULT scope. */
public record PublishStandardHoursCommand(@Nullable LegalEntityId legalEntityId, LocalDate effectiveFromDate, int workdayMinutes,
                                          int adjustedWorkdayMinutes, List<DateOverrideEntry> dateOverrides, String basisRef) {
    public PublishStandardHoursCommand {
        dateOverrides = List.copyOf(dateOverrides);
    }
}
