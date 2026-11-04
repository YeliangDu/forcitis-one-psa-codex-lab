package com.forcitis.one.organization.attendance.web;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.attendance.api.AttendanceError;
import com.forcitis.one.organization.attendance.application.PublishStandardHoursCommand;
import com.forcitis.one.organization.attendance.application.DateOverrideEntry;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Filled by Jackson, so every field may be null; toCommand refuses what is missing and trims the texts. */
record PublishStandardHoursRequest(@Nullable UUID legalEntityId, @Nullable LocalDate effectiveFromDate, @Nullable Integer workdayMinutes,
                                   @Nullable Integer adjustedWorkdayMinutes, @Nullable List<OverrideRequest> dateOverrides, @Nullable String basisRef) {

    record OverrideRequest(@Nullable LocalDate workDate, @Nullable Integer minutes, @Nullable String reason) {
        DateOverrideEntry toOverride() {
            return new DateOverrideEntry(required(workDate, "workDate"), required(minutes, "minutes"), required(reason, "reason").strip());
        }
    }

    PublishStandardHoursCommand toCommand() {
        List<DateOverrideEntry> overrides = dateOverrides == null ? List.of() : dateOverrides.stream().map(o -> required(o, "dateOverrides item").toOverride()).toList();
        return new PublishStandardHoursCommand(legalEntityId == null ? null : new LegalEntityId(legalEntityId),
                required(effectiveFromDate, "effectiveFromDate"), required(workdayMinutes, "workdayMinutes"),
                required(adjustedWorkdayMinutes, "adjustedWorkdayMinutes"), overrides, required(basisRef, "basisRef").strip());
    }

    static <T> T required(@Nullable T value, String field) {
        if (value == null) {
            throw new DomainException(AttendanceError.ORGANIZATION_ATTENDANCE_STANDARD_HOURS_INVALID, ErrorKind.INVALID, field + " is required");
        }
        return value;
    }
}
