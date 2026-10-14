package com.forcitis.one.platform.numbering.domain;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.platform.numbering.api.DocumentType;
import java.util.List;
import java.util.Optional;

public interface NumberingRuleRepository {

    Optional<NumberingRule> findActive(DocumentType type);

    List<NumberingRule> findAllActive();

    /** Highest value any counter of the type has reached (0 when none was issued yet). */
    long maxLastValue(DocumentType type);

    void save(NumberingRule rule);

    /** Atomically reserves the next value of the counter (row lock until the surrounding transaction ends). */
    long reserveNext(DocumentType type, LegalEntityId legalEntity, String periodKey, long startValue);
}
