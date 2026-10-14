package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.LegalEntityId;
import java.util.Optional;

/** Writing side. A code already used by another legal entity is refused with a DomainException; a stale save too. */
public interface LegalEntityRepository {
    Optional<LegalEntity> find(LegalEntityId legalEntityId);

    /** Returns the stored legal entity with its id and its registration event recorded. */
    LegalEntity insert(LegalEntity unsaved);

    void save(LegalEntity legalEntity);
}
