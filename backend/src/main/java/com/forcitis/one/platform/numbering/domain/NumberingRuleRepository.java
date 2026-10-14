package com.forcitis.one.platform.numbering.domain;

import com.forcitis.one.kernel.DocumentType;
import com.forcitis.one.kernel.LegalEntityId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NumberingRuleRepository {
    UUID GLOBAL_COUNTER_SCOPE = new UUID(0, 0);

    Optional<NumberingRule> findActive(DocumentType type);

    List<NumberingRule> findAllActive();

    /** Highest value any counter of the type has reached (0 when none was issued yet). */
    long maxLastValue(DocumentType type);

    /** True once any counter of any type is kept for the legal entity, that is, a number carrying its code was issued. */
    boolean hasCounterFor(LegalEntityId legalEntityId);

    /** Applies only to the version the rule was loaded with and to versions still in the status they were loaded in; otherwise a DomainException (CONFLICT). */
    void save(NumberingRule rule);

    /** Atomically reserves the next value of the counter (row lock until the surrounding transaction ends).
     *  counterScope is the legal entity id, or GLOBAL_COUNTER_SCOPE when the template has no {LE}. */
    long reserveNext(DocumentType type, UUID counterScope, String periodKey, long startValue);
}
