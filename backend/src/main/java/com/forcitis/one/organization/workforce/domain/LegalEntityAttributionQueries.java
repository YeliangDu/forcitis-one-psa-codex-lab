package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.organization.workforce.api.AttributionSubject;
import java.util.List;

/** Reading side of the attributions. */
public interface LegalEntityAttributionQueries {
    List<AttributionPeriod> periodsOf(AttributionSubject subject);

    /** Empty while nothing has ever been attributed to the subject. */
    java.util.Optional<Long> versionOf(AttributionSubject subject);
}
