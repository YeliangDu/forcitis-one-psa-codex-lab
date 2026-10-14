package com.forcitis.one.platform.identity.domain;

/** Port that encrypts TOTP secrets at rest (AES-GCM with a key from the environment / KMS, in infrastructure). */
public interface TotpSecretVault {

    String seal(byte[] secret);

    /** Throws IllegalStateException when the vault has no key (fail-closed: emergency login is then impossible). */
    byte[] open(String sealed);
}
