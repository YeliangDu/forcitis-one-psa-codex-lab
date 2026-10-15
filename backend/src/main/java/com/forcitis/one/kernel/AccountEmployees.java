package com.forcitis.one.kernel;

import java.util.Optional;

/**
 * The only place where an employee and the account that person signs in with are converted into each other, implemented by whoever owns
 * accounts (ADR-0033 section 3). Empty when there is no such account; the caller decides whether that is a refusal.
 */
public interface AccountEmployees {

    Optional<EmployeeId> employeeOf(UserId account);

    Optional<UserId> accountOf(EmployeeId employee);
}
