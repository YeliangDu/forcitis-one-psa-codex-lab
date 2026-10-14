package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.organization.workforce.api.AttributionSubject;
import java.util.Optional;

/** Writing side. A stale save is refused with a DomainException (CONFLICT). */
public interface LegalEntityAttributionRepository {
    Optional<LegalEntityAttribution> find(AttributionSubject subject);

    void save(LegalEntityAttribution attribution);
}
