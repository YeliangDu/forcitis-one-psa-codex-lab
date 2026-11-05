package com.forcitis.one.kernel;

import java.util.List;

/**
 * Every legal entity that is registered, active or not, for a context that reconciles what its own tables hold with what is registered
 * (accounting.ledger creates the missing books). Implemented by organization.workforce, which owns the legal entities. A kernel port and not
 * a workforce api method: the workforce api is at its method limit (G4), and the asker needs no other part of the workforce api for it.
 */
public interface RegisteredLegalEntities {

    List<LegalEntityId> all();
}
