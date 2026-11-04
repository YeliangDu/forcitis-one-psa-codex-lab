package com.forcitis.one.organization.workforce.api;

import com.forcitis.one.kernel.WecomUserId;

/** The outside sign-in a person is looked up by; the WeCom user id leaves the organisation snapshot with DA-3 (ADR-0033 section 4). A lookup never creates an employee (ADR-0069 section 9). */
public sealed interface LoginLookup {

    record ByWecomUser(WecomUserId wecomUserId) implements LoginLookup {
    }
}
