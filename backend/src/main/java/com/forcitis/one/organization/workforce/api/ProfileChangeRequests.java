package com.forcitis.one.organization.workforce.api;

import com.forcitis.one.kernel.CursorPage;
import com.forcitis.one.kernel.ExpectedVersion;
import com.forcitis.one.kernel.PageRequest;

/**
 * The second person of a profile change that moves or clears a set WeCom user id on an account with a role beyond employee (user decision 2026-11-02, three
 * controls; design note wecom-rebind-second-person.md). Needs organization.workforce:personnel-command:create, checked before anything about the content.
 */
public interface ProfileChangeRequests {

    /** The requests that wait, oldest first (no employee data scope exists yet: every holder of the permission sees every request, design note section 5.4). */
    CursorPage<ProfileChangeRequestView> pending(PageRequest page);

    /**
     * Applies the whole proposed profile and ends the sessions and browser identifiers of the employee's account in one transaction; the request ends CONFIRMED. When
     * the profile moved since the request or the proposed WeCom user id now belongs to another employee, nothing is applied and the request ends VOIDED: that is
     * returned (the status and its audit entry must commit), not thrown.
     *
     * @throws com.forcitis.one.kernel.DomainException when the request does not exist, is no longer pending, or the caller is its requester or the employee themself
     */
    ProfileChangeRequestView confirm(ProfileChangeRequestId id, ExpectedVersion expected);

    /**
     * Ends the request REJECTED with the reason: by another holder of the permission, or by the requester as a withdrawal; never by the employee themself.
     *
     * @throws com.forcitis.one.kernel.DomainException when the request does not exist, is no longer pending, or the caller is the employee themself
     */
    ProfileChangeRequestView reject(ProfileChangeRequestId id, String reason, ExpectedVersion expected);
}
