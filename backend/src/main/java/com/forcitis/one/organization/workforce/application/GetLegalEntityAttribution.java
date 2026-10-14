package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.organization.workforce.api.AttributionKind;
import com.forcitis.one.organization.workforce.domain.LegalEntityAttributionQueries;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetLegalEntityAttribution {
    private final LegalEntityAttributionQueries attributions;
    private final AttributionSubjects subjects;

    GetLegalEntityAttribution(LegalEntityAttributionQueries attributions, AttributionSubjects subjects) {
        this.attributions = attributions;
        this.subjects = subjects;
    }

    @Transactional(readOnly = true)
    public LegalEntityAttributionView execute(AttributionKind kind, long subjectId) {
        var subject = subjects.require(kind, subjectId);
        return LegalEntityAttributionView.of(subject, attributions.periodsOf(subject));
    }
}
