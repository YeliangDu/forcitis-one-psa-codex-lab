package com.forcitis.one.platform.identity.domain;

public interface PasswordHasher {

    String hash(String password);

    boolean matches(String password, String hash);

    /** Spends the same time as matches() against a throw-away hash, so unknown login names cost the same as wrong passwords. */
    void spendVerificationTime(String password);
}
