package com.forcitis.one.platform.identity.application;

/** Result of an administration command: done (with a value) or refused with a business error. */
public sealed interface AdminOutcome<T> {

    record Done<T>(T value) implements AdminOutcome<T> {
    }

    record Refused<T>(IdentityError error) implements AdminOutcome<T> {
    }
}
