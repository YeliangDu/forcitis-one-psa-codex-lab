package com.forcitis.one.platform.integration.api;

import com.forcitis.one.kernel.WecomUserId;
import java.util.List;

public interface OrgSource {

    SourceOrgSnapshot readSnapshot();

    /** More than one result means ambiguous. */
    List<SourcePerson> findStaffByWecomUserId(WecomUserId wecomUserId);

    /** More than one result means ambiguous. */
    List<SourcePerson> findStaffByLoginName(String loginName);
}
