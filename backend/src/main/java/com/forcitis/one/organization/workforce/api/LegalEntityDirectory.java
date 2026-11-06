package com.forcitis.one.organization.workforce.api;

import com.forcitis.one.kernel.LegalEntityId;
import java.util.List;
import java.util.Optional;

public interface LegalEntityDirectory {
    /** A deactivated legal entity keeps its code. */
    Optional<LegalEntityCode> findCode(LegalEntityId legalEntityId);

    /**
     * Empty for an unknown or deactivated legal entity. Takes a shared lock on the legal entity until the caller's transaction ends,
     * so a correction or a deactivation (which needs the exclusive lock) waits for the numbers issued meanwhile.
     */
    Optional<LegalEntityCode> lockActiveCode(LegalEntityId legalEntityId);

    /** Every legal entity, active or not, for a consumer that reconciles what its own tables hold with what is registered. */
    List<LegalEntityId> allIds();
}
