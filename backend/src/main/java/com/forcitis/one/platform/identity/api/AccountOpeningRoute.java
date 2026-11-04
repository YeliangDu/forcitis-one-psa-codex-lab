package com.forcitis.one.platform.identity.api;

/** Which way an account came to be opened (ADR-0069 section 9); carried on {@code AccountOpened} only, not stored on the account. */
public enum AccountOpeningRoute {
    /** The hiring of the person ({@code EmployeeHired}). */
    HIRE,
    /** The person signed in as an employed person who had no account yet. */
    LOGIN,
    /** An operations command named the person (grant a role, register an emergency administrator). */
    OPERATIONS
}
