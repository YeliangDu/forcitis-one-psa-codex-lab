package com.forcitis.one.organization.workforce.api;

import com.forcitis.one.kernel.LegalEntityId;
import java.util.Optional;

/** The legal entity table the administrators maintain; numbering reads the code from here. */
public interface LegalEntityDirectory {

    /** Empty when the legal entity does not exist. A deactivated legal entity keeps its code. */
    Optional<LegalEntityCode> findCode(LegalEntityId legalEntityId);
}
