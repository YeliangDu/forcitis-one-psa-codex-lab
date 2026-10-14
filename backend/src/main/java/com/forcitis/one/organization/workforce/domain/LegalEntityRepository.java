package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.workforce.api.LegalEntityCode;
import java.util.List;
import java.util.Optional;

public interface LegalEntityRepository {

    List<LegalEntity> findAll();

    Optional<LegalEntity> find(LegalEntityId legalEntityId);

    /** Empty when the code is already used. */
    Optional<LegalEntity> insert(LegalEntityCode code, String name);

    void update(LegalEntity legalEntity);
}
