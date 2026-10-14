package com.forcitis.one.platform.identity.application;

import com.forcitis.one.platform.identity.domain.EmergencyPasswordPolicy;
import com.forcitis.one.platform.identity.domain.PasswordHasher;
import com.forcitis.one.platform.identity.domain.Totp;
import com.forcitis.one.platform.identity.domain.TotpSecretVault;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/** Generates the password and the one-time-code secret at the moment of use; only the Argon2id hash and the sealed secret are kept. */
@Component
class EmergencyCredentialsFactory {

    private static final String LOWER = "abcdefghijkmnopqrstuvwxyz";
    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String DIGITS = "23456789";
    private static final String SYMBOLS = "!#$%*+-=?@^_";
    private static final int LENGTH = 24;

    private final SecureRandom random = new SecureRandom();
    private final PasswordHasher hasher;
    private final TotpSecretVault vault;

    EmergencyCredentialsFactory(PasswordHasher hasher, TotpSecretVault vault) {
        this.hasher = hasher;
        this.vault = vault;
    }

    record Generated(IssuedEmergencyCredentials shown, String passwordHash, String sealedTotpSecret) {
    }

    Generated generate(String loginName) {
        String password;
        do {
            password = password();
        } while (!EmergencyPasswordPolicy.violations(password, loginName).isEmpty());
        byte[] secret = Totp.newSecret();
        return new Generated(new IssuedEmergencyCredentials(loginName, password, Totp.toBase32(secret)), hasher.hash(password), vault.seal(secret));
    }

    private String password() {
        String all = LOWER + UPPER + DIGITS + SYMBOLS;
        StringBuilder out = new StringBuilder(LENGTH);
        for (String group : new String[] {LOWER, UPPER, DIGITS, SYMBOLS}) {
            out.append(group.charAt(random.nextInt(group.length())));
        }
        while (out.length() < LENGTH) {
            out.append(all.charAt(random.nextInt(all.length())));
        }
        char[] mixed = out.toString().toCharArray();
        for (int i = mixed.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char swap = mixed[i];
            mixed[i] = mixed[j];
            mixed[j] = swap;
        }
        return new String(mixed);
    }
}
