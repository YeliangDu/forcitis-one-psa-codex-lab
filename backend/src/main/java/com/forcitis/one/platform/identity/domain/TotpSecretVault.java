package com.forcitis.one.platform.identity.domain;

public interface TotpSecretVault {

    String seal(byte[] secret);

    /** Throws IllegalStateException when the vault has no key (fail-closed). */
    byte[] open(String sealed);
}
