package com.forcitis.one.organization.workforce.api;

import com.forcitis.one.kernel.WecomUserId;

/** The outside sign-in a person is looked up by; both kinds leave the organisation snapshot with DA-3 (ADR-0033 section 4). */
public sealed interface LoginLookup {

    record ByWecomUserId(WecomUserId wecomUserId) implements LoginLookup {
    }

    record ByLoginName(String loginName) implements LoginLookup {
    }
}
