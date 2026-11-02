package com.forcitis.one.organization.workforce.api;

/**
 * Changes the current profile of an employee (W2 design section 3.1; user answer Q1 of 2026-10-30). Needs organization.workforce:personnel-command:create,
 * checked before anything about the content. The employee code is never part of it.
 */
public interface EmployeeProfiles {

    /**
     * Replaces the display name, gender, email and WeCom user id with the ones carried, at once; no version row is written and a change that alters nothing
     * writes nothing.
     *
     * @throws com.forcitis.one.kernel.DomainException when the employee does not exist, the profile is invalid, the effective date is later than today
     *                                                 the WeCom user id is held by another employee (separated ones included), the caller is the employee themself, or the account holds a role beyond employee and its WeCom user id would be moved or cleared
     */
    void change(ChangeEmployeeProfile command);
}
