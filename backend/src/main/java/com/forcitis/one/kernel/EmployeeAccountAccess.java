package com.forcitis.one.kernel;

/**
 * What organization.workforce needs to know about, and to do to, the sign-in access of the account behind an employee when it changes the employee's WeCom
 * user id (the login binding): asked by workforce, implemented by whoever owns accounts and sessions (platform.identity), which depends on workforce.
 * Both run in the caller's transaction, never in one of their own.
 */
public interface EmployeeAccountAccess {

    /**
     * False when the employee's account holds any role besides the plain employee role, custom roles included, whether or not it holds the employee role itself;
     * true for an account with that role alone, with no role at all, and for a person without an account.
     */
    boolean holdsOnlyEmployeeRole(EmployeeId employee);

    /** Ends every session and every browser identifier of the employee's account; no account is a no-op. */
    void revokeSessions(EmployeeId employee);
}
