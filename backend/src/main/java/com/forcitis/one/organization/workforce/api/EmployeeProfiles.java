package com.forcitis.one.organization.workforce.api;

import java.util.Optional;

/**
 * Changes the current profile of an employee (W2 design section 3.1; user answer Q1 of 2026-10-30). Needs organization.workforce:personnel-command:create,
 * checked before anything about the content. The employee code is never part of it.
 */
public interface EmployeeProfiles {

    /**
     * Replaces the display name, gender, email and WeCom user id with the ones carried, at once; no version row is written and a change that alters nothing
     * writes nothing. When the account holds a role beyond employee and its set WeCom user id would be moved or cleared (or filled in within 30 days after a clear a
     * second person confirmed), nothing is written: the whole proposed profile waits for a second person and the request id is returned.
     *
     * @return the id of the request that waits for a second person, empty when the change was applied at once
     * @throws com.forcitis.one.kernel.DomainException when the employee does not exist, the profile is invalid, the effective date is later than today,
     *                                                 the WeCom user id is held by another employee (separated ones included), the caller is the employee themself,
     *                                                 or a request for the employee already waits
     */
    Optional<ProfileChangeRequestId> change(ChangeEmployeeProfile command);
}
