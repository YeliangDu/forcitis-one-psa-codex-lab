package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.organization.workforce.domain.PositionQueries;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record PositionView(UUID positionId, @Nullable String code, String name, String status, LocalDate effectiveFromDate,
                           @Nullable LocalDate effectiveToDate) {
    static PositionView of(PositionQueries.Row row) {
        return new PositionView(row.positionId().value(), row.code(), row.name(), row.status().name(), row.effectiveFromDate(), row.effectiveToDate());
    }
}
