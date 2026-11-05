package com.forcitis.one.organization.attendance.domain;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A stored version as it is read; changes go through StandardHours. legalEntityId is null for the DEFAULT scope. */
public record StandardHoursVersionRecord(UUID id, @Nullable LegalEntityId legalEntityId, int versionNumber, StandardHoursStatus status,
                                         LocalDate effectiveFromDate, @Nullable LocalDate effectiveToDate, int workdayMinutes,
                                         int adjustedWorkdayMinutes, String basisRef, UserId confirmedBy, Instant confirmedAt,
                                         List<DateOverride> dateOverrides) {
    public StandardHoursVersionRecord {
        dateOverrides = List.copyOf(dateOverrides);
    }

    public boolean covers(LocalDate date) {
        return status == StandardHoursStatus.IN_EFFECT && !date.isBefore(effectiveFromDate)
                && (effectiveToDate == null || date.isBefore(effectiveToDate));
    }
}
