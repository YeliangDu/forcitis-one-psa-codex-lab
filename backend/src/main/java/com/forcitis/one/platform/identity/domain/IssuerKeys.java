package com.forcitis.one.platform.identity.domain;

import java.util.Optional;

/** Keys are never in the database. */
public interface IssuerKeys {

    /** Empty when unknown, unset or too short (fail-closed). */
    Optional<byte[]> resolve(String keyReference);
}
