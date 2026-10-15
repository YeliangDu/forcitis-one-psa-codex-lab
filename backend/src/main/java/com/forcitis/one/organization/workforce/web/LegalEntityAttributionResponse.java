package com.forcitis.one.organization.workforce.web;

import com.forcitis.one.organization.workforce.application.LegalEntityAttributionView;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

record LegalEntityAttributionResponse(String kind, UUID subjectId, List<Period> periods) {
    record Period(UUID legalEntityId, LocalDate effectiveFrom, @org.jspecify.annotations.Nullable LocalDate effectiveTo) {
    }

    static LegalEntityAttributionResponse of(LegalEntityAttributionView view) {
        return new LegalEntityAttributionResponse(view.kind(), view.subjectId(),
                view.periods().stream().map(p -> new Period(p.legalEntityId().value(), p.effectiveFrom(), p.effectiveTo())).toList());
    }
}
