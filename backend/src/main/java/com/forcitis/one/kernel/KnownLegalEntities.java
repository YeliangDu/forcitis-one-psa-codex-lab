package com.forcitis.one.kernel;

/** Asked by modules that may not depend on organization.workforce (the context map points the other way); implemented by it. */
public interface KnownLegalEntities {

    /** A deactivated legal entity still exists. */
    boolean isKnown(LegalEntityId legalEntityId);
}
