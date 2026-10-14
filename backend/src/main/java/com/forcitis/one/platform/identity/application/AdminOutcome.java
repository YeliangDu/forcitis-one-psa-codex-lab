package com.forcitis.one.platform.identity.application;

public sealed interface AdminOutcome<T> {

    record Done<T>(T value) implements AdminOutcome<T> {
    }

    record Refused<T>(IdentityError error) implements AdminOutcome<T> {
    }
}
