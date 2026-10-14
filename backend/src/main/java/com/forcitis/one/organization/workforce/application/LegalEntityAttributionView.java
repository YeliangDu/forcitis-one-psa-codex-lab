package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.organization.workforce.domain.AttributionPeriod;
import com.forcitis.one.organization.workforce.domain.LegalEntityAttribution;
import java.time.LocalDate;
import java.util.List;

public record LegalEntityAttributionView(String kind, long subjectId, List<Period> periods, Long version) {
    public record Period(long legalEntityId, LocalDate effectiveFrom, LocalDate effectiveTo) {
        static Period of(AttributionPeriod period) {
            return new Period(period.legalEntityId().value(), period.effectiveFrom(), period.effectiveTo());
        }
    }


    static LegalEntityAttributionView of(com.forcitis.one.organization.workforce.api.AttributionSubject subject, List<AttributionPeriod> periods, Long version) {
        return new LegalEntityAttributionView(subject.kind().name(), subject.subjectKey(), periods.stream().map(Period::of).toList(), version);
    }

    static LegalEntityAttributionView of(LegalEntityAttribution attribution) {
        return new LegalEntityAttributionView(attribution.subject().kind().name(), attribution.subject().subjectKey(),
                attribution.periods().stream().map(Period::of).toList(),
                attribution.version());
    }
}
