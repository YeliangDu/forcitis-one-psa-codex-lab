package com.forcitis.one.organization.attendance.web;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.attendance.api.AttendanceError;
import com.forcitis.one.organization.attendance.application.PublishStandardHoursCommand;
import com.forcitis.one.organization.attendance.application.DateOverrideEntry;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Filled by Jackson, so every field may be null; toCommand refuses what is missing and trims the texts. Minutes arrive as numbers and must be whole: Jackson would silently cut 7.5 to 7. */
record PublishStandardHoursRequest(@Nullable UUID legalEntityId, @Nullable LocalDate effectiveFromDate, @io.swagger.v3.oas.annotations.media.Schema(type = "integer") @Nullable BigDecimal workdayMinutes,
                                   @io.swagger.v3.oas.annotations.media.Schema(type = "integer") @Nullable BigDecimal adjustedWorkdayMinutes, @Nullable List<OverrideRequest> dateOverrides, @Nullable String basisRef) {

    record OverrideRequest(@Nullable LocalDate workDate, @io.swagger.v3.oas.annotations.media.Schema(type = "integer") @Nullable BigDecimal minutes, @Nullable String reason) {
        DateOverrideEntry toOverride() {
            return new DateOverrideEntry(required(workDate, "workDate"), wholeMinutes(minutes, "minutes"), requiredText(reason, "reason"));
        }
    }

    PublishStandardHoursCommand toCommand() {
        List<DateOverrideEntry> overrides = dateOverrides == null ? List.of() : dateOverrides.stream().map(o -> required(o, "dateOverrides item").toOverride()).toList();
        return new PublishStandardHoursCommand(legalEntityId == null ? null : new LegalEntityId(legalEntityId),
                required(effectiveFromDate, "effectiveFromDate"), wholeMinutes(workdayMinutes, "workdayMinutes"),
                wholeMinutes(adjustedWorkdayMinutes, "adjustedWorkdayMinutes"), overrides, requiredText(basisRef, "basisRef"));
    }

    static <T> T required(@Nullable T value, String field) {
        if (value == null) {
            throw new DomainException(AttendanceError.ORGANIZATION_ATTENDANCE_STANDARD_HOURS_INVALID, ErrorKind.INVALID, field + " is required");
        }
        return value;
    }

    /** PostgreSQL cannot store NUL in text: left alone it would surface as a 500 from the insert. */
    static String requiredText(@Nullable String value, String field) {
        String text = required(value, field);
        if (text.indexOf('\u0000') >= 0) {
            throw new DomainException(AttendanceError.ORGANIZATION_ATTENDANCE_STANDARD_HOURS_INVALID, ErrorKind.INVALID, field + " must not contain a NUL character");
        }
        return text.strip();
    }

    static int wholeMinutes(@Nullable BigDecimal value, String field) {
        try {
            return required(value, field).intValueExact();
        } catch (ArithmeticException notWhole) {
            throw new DomainException(AttendanceError.ORGANIZATION_ATTENDANCE_STANDARD_HOURS_INVALID, ErrorKind.INVALID, field + " must be a whole number of minutes");
        }
    }
}
