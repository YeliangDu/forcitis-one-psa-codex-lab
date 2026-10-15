package com.forcitis.one.platform.integration.api;

import java.util.List;

public interface OrgSource {

    /** Relationships come ordered by the source's person id, signed ascending; the reconciliation relies on it (RULE-356). */
    SourceOrgSnapshot readSnapshot();

    /** The people the sign-in names; more than one result means ambiguous. Goes with DA-3. */
    List<SourcePerson> findStaffByLogin(StaffLookup lookup);
}
