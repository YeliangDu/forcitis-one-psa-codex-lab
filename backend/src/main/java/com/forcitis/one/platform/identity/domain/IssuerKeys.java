package com.forcitis.one.platform.identity.domain;

import java.util.Optional;

/** Port to the key store (environment variable now, KMS later). Keys are never in the database. */
public interface IssuerKeys {

    /** The key behind a reference such as "env:NAME"; empty when unknown, unset or too short (fail-closed). */
    Optional<byte[]> resolve(String keyReference);
}
