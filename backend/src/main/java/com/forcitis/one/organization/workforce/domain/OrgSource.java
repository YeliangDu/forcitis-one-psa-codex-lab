package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.WecomUserId;
import java.util.List;

/**
 * Where org master data comes from (the external organisation source today, the future in-house HRM later - the source
 * is replaceable, REIMAGINED_ARCHITECTURE §3.3). Bulk sync and single-person lookups share one interpretation.
 */
public interface OrgSource {

    /** Full read of org units, relations, positions and persons in the sync scope. */
    SourceSnapshot fetchAll();

    /** The persons that carry this WeCom user id in the sync scope (normally zero or one; more means ambiguous). */
    List<SourceMember> fetchMembersByWecomUserId(WecomUserId wecomUserId);

    /** The persons that carry this login name in the sync scope (normally zero or one; more means ambiguous). */
    List<SourceMember> fetchMembersByLoginName(String loginName);
}
