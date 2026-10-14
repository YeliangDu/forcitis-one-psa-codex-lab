package com.forcitis.one.platform.numbering.domain;

import com.forcitis.one.kernel.DocumentType;
import java.util.List;
import java.util.Optional;

public interface NumberingRuleRepository {
    long GLOBAL_COUNTER_SCOPE = 0;

    Optional<NumberingRule> findActive(DocumentType type);

    List<NumberingRule> findAllActive();

    /** Highest value any counter of the type has reached (0 when none was issued yet). */
    long maxLastValue(DocumentType type);

    void save(NumberingRule rule);

    /** Atomically reserves the next value of the counter (row lock until the surrounding transaction ends).
     *  counterScope is the legal entity id, or GLOBAL_COUNTER_SCOPE when the template has no {LE}. */
    long reserveNext(DocumentType type, long counterScope, String periodKey, long startValue);
}
