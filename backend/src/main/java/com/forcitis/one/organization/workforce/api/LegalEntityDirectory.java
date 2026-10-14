package com.forcitis.one.organization.workforce.api;

import com.forcitis.one.kernel.LegalEntityId;
import java.util.Optional;

public interface LegalEntityDirectory {
    /** A deactivated legal entity keeps its code. */
    Optional<LegalEntityCode> findCode(LegalEntityId legalEntityId);
}
