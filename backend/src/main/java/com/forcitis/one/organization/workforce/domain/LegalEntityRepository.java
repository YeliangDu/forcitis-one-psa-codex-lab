package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.LegalEntityId;
import java.util.Optional;

/** Writing side. A code already used by another legal entity is refused with a DomainException; a stale save too. */
public interface LegalEntityRepository {
    Optional<LegalEntity> find(LegalEntityId legalEntityId);

    /** Takes the exclusive lock first, so that it waits for the shared locks of the numbers being issued. */
    Optional<LegalEntity> findForUpdate(LegalEntityId legalEntityId);

    /** Returns the stored legal entity with its id and its registration event recorded. */
    LegalEntity insert(LegalEntity unsaved);

    void save(LegalEntity legalEntity);
}
