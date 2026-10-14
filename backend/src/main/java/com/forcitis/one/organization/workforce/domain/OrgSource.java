package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.WecomUserId;
import java.util.List;

/** The source is replaceable (future in-house HRM); bulk sync and single-person lookups share one interpretation. */
public interface OrgSource {
    SourceSnapshot fetchAll();

    /** Normally zero or one person; more means ambiguous. */
    List<SourceMember> fetchMembersByWecomUserId(WecomUserId wecomUserId);

    /** Normally zero or one person; more means ambiguous. */
    List<SourceMember> fetchMembersByLoginName(String loginName);
}
