package com.forcitis.one.platform.identity.infrastructure.security;

import com.forcitis.one.platform.identity.domain.PasswordHasher;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;

/** Argon2id (Spring Security 5.8+ defaults: 16-byte salt, 32-byte hash, 1 iteration, 16 MiB, 1 lane), via BouncyCastle. */
@Component
class Argon2PasswordHasher implements PasswordHasher {

    private final Argon2PasswordEncoder encoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    private volatile String throwAwayHash;

    @Override
    public String hash(String password) {
        return encoder.encode(password);
    }

    @Override
    public boolean matches(String password, String hash) {
        return encoder.matches(password, hash);
    }

    @Override
    public void spendVerificationTime(String password) {
        if (throwAwayHash == null) {
            throwAwayHash = encoder.encode("throw-away value for constant-time answers");
        }
        encoder.matches(password, throwAwayHash);
    }
}
