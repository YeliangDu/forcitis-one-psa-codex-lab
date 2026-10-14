package com.forcitis.one.platform.integration.api;

import com.forcitis.one.kernel.WecomUserId;
import java.util.List;

/** Read-only door to the external organisation master data (org units, positions, people, reporting lines). */
public interface OrgSource {

    /** Everything in the sync scope: org units (with parents), relations, positions and people. */
    SourceOrgSnapshot readSnapshot();

    /** The people in scope that carry this WeCom user id (normally zero or one; more means ambiguous). */
    List<SourcePerson> findStaffByWecomUserId(WecomUserId wecomUserId);

    /** The people in scope that carry this login name (normally zero or one; more means ambiguous). */
    List<SourcePerson> findStaffByLoginName(String loginName);
}
