package com.forcitis.one.organization.attendance.application;

import com.forcitis.one.organization.attendance.domain.StandardHoursVersionRecord;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** scopeKind is DEFAULT or LEGAL_ENTITY, status IN_EFFECT or VOIDED; legalEntityId is null for DEFAULT. */
public record StandardHoursVersionView(UUID standardHoursVersionId, int versionNumber, String scopeKind, @Nullable UUID legalEntityId, String status,
                                       LocalDate effectiveFromDate, @Nullable LocalDate effectiveToDate, int workdayMinutes,
                                       int adjustedWorkdayMinutes, String basisRef, UUID confirmedBy, Instant confirmedAt,
                                       List<DateOverrideEntry> dateOverrides) {
    public StandardHoursVersionView {
        dateOverrides = List.copyOf(dateOverrides);
    }

    static StandardHoursVersionView of(StandardHoursVersionRecord version) {
        UUID legalEntity = version.legalEntityId() == null ? null : version.legalEntityId().value();
        return new StandardHoursVersionView(version.id(), version.versionNumber(), legalEntity == null ? "DEFAULT" : "LEGAL_ENTITY", legalEntity,
                version.status().name(), version.effectiveFromDate(), version.effectiveToDate(), version.workdayMinutes(),
                version.adjustedWorkdayMinutes(), version.basisRef(), version.confirmedBy().value(), version.confirmedAt(),
                version.dateOverrides().stream().map(o -> new DateOverrideEntry(o.workDate(), o.minutes(), o.reason())).toList());
    }
}
