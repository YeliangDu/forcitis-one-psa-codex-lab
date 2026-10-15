package com.forcitis.one.platform.integration.api;

import java.util.List;

public interface OrgSource {

    SourceOrgSnapshot readSnapshot();

    /** The people the sign-in names; more than one result means ambiguous. Goes with DA-3. */
    List<SourcePerson> findStaffByLogin(StaffLookup lookup);
}
