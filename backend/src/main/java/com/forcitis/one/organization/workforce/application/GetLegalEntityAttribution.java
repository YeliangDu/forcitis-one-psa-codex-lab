package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.organization.workforce.api.AttributionKind;
import com.forcitis.one.organization.workforce.domain.LegalEntityAttributionQueries;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** version is null while nothing has ever been attributed to the subject: the first attribution is then written with If-None-Match: *. */
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
        return LegalEntityAttributionView.of(subject, attributions.periodsOf(subject), attributions.versionOf(subject).orElse(null));
    }
}
