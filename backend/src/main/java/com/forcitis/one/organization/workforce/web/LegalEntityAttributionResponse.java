package com.forcitis.one.organization.workforce.web;

import com.forcitis.one.organization.workforce.application.LegalEntityAttributionView;
import java.time.LocalDate;
import java.util.List;

record LegalEntityAttributionResponse(String kind, String subjectId, List<Period> periods) {
    record Period(String legalEntityId, LocalDate effectiveFrom, @org.jspecify.annotations.Nullable LocalDate effectiveTo) {
    }

    static LegalEntityAttributionResponse of(LegalEntityAttributionView view) {
        return new LegalEntityAttributionResponse(view.kind(), String.valueOf(view.subjectId()),
                view.periods().stream().map(p -> new Period(String.valueOf(p.legalEntityId()), p.effectiveFrom(), p.effectiveTo())).toList());
    }
}
