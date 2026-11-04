package com.forcitis.one.organization.workforce.web;

import com.forcitis.one.organization.workforce.application.PositionView;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

record PositionResponse(UUID positionId, @Nullable String code, String name, String status, LocalDate effectiveFromDate, @Nullable LocalDate effectiveToDate) {
    static PositionResponse of(PositionView view) {
        return new PositionResponse(view.positionId(), view.code(), view.name(), view.status(), view.effectiveFromDate(), view.effectiveToDate());
    }
}
