package com.forcitis.one.organization.workforce.api;

import com.forcitis.one.kernel.LegalEntityId;

/** Implemented by the module that issues document numbers; workforce asks it before a legal entity code is corrected. */
public interface LegalEntityNumberUsage {

    boolean hasIssuedNumbers(LegalEntityId legalEntityId);
}
