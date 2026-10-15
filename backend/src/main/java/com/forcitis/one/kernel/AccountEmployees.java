package com.forcitis.one.kernel;

/**
 * The only place where an employee and the account that person signs in with are converted into each other. Implemented by
 * whoever owns accounts (ADR-0033 section 3). Both ids hold the same number until the account gets an id of its own.
 */
public interface AccountEmployees {

    EmployeeId employeeOf(UserId account);

    UserId accountOf(EmployeeId employee);
}
