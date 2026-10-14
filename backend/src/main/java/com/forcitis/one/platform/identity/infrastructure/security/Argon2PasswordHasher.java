package com.forcitis.one.platform.identity.infrastructure.security;

import com.forcitis.one.platform.identity.domain.PasswordHasher;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;

/** Argon2id at the OWASP minimum (19 MiB, 2 iterations, 1 lane; 16-byte salt, 32-byte hash), via BouncyCastle. Older hashes carry their own parameters and still verify. */
@Component
class Argon2PasswordHasher implements PasswordHasher {

    private final Argon2PasswordEncoder encoder = new Argon2PasswordEncoder(16, 32, 1, 19456, 2);
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
