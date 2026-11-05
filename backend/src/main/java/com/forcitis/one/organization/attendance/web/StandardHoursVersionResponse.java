package com.forcitis.one.organization.attendance.web;

import com.forcitis.one.organization.attendance.application.DateOverrideEntry;
import com.forcitis.one.organization.attendance.application.StandardHoursVersionView;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

record StandardHoursVersionResponse(UUID standardHoursVersionId, int versionNumber, String scopeKind, @Nullable UUID legalEntityId, String status,
                                    LocalDate effectiveFromDate, @Nullable LocalDate effectiveToDate, int workdayMinutes, int adjustedWorkdayMinutes,
                                    String basisRef, UUID confirmedBy, Instant confirmedAt, List<OverrideResponse> dateOverrides) {

    record OverrideResponse(LocalDate workDate, int minutes, String reason) {
        static OverrideResponse of(DateOverrideEntry override) {
            return new OverrideResponse(override.workDate(), override.minutes(), override.reason());
        }
    }

    static StandardHoursVersionResponse of(StandardHoursVersionView version) {
        return new StandardHoursVersionResponse(version.standardHoursVersionId(), version.versionNumber(), version.scopeKind(), version.legalEntityId(),
                version.status(), version.effectiveFromDate(), version.effectiveToDate(), version.workdayMinutes(), version.adjustedWorkdayMinutes(),
                version.basisRef(), version.confirmedBy(), version.confirmedAt(), version.dateOverrides().stream().map(OverrideResponse::of).toList());
    }
}
