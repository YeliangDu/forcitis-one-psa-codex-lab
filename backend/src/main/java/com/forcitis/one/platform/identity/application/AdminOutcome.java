package com.forcitis.one.platform.identity.application;

import com.forcitis.one.platform.identity.api.IdentityError;

public sealed interface AdminOutcome<T> {

    record Done<T>(T value) implements AdminOutcome<T> {
    }

    record Refused<T>(IdentityError error) implements AdminOutcome<T> {
    }

    /** The change needs a second person: nothing is applied yet, the request is open. */
    record Awaiting<T>(String requestId) implements AdminOutcome<T> {
    }
}
