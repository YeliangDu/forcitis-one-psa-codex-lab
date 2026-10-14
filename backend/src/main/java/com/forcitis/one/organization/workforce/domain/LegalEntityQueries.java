package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.workforce.api.LegalEntityCode;
import java.util.List;
import java.util.Optional;

/** Reading side of the legal entities. */
public interface LegalEntityQueries {
    List<LegalEntityRecord> findAll();

    Optional<LegalEntityRecord> find(LegalEntityId legalEntityId);

    record LegalEntityRecord(LegalEntityId legalEntityId, LegalEntityCode code, String name, LegalEntityStatus status) {
    }
}
