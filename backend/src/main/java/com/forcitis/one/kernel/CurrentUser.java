package com.forcitis.one.kernel;

/**
 * The signed-in caller, implemented by whoever owns sessions so that modules it depends on can ask too. Never taken from request bodies
 * or headers. userId is the account; employeeId is the person behind it, for what belongs to a person.
 */
public interface CurrentUser {

    UserId userId();

    EmployeeId employeeId();

    LegalEntityId legalEntityId();
}
