package com.forcitis.one.platform.identity.infrastructure.security;

import com.forcitis.one.platform.identity.application.EmergencySettings;
import com.forcitis.one.platform.identity.domain.TotpSecretVault;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/** AES-256-GCM for TOTP secrets at rest; key (Base64, 32 bytes) from the environment / KMS, never from the database. */
@Component
class AesTotpSecretVault implements TotpSecretVault {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final String encodedKey;
    private final SecureRandom random = new SecureRandom();

    AesTotpSecretVault(EmergencySettings settings) {
        this.encodedKey = settings.totpEncryptionKey();
    }

    @Override
    public String seal(byte[] secret) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] sealed = cipher.doFinal(secret);
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + sealed.length).put(iv).put(sealed).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("cannot seal the TOTP secret", e);
        }
    }

    @Override
    public byte[] open(String sealed) {
        try {
            byte[] all = Base64.getDecoder().decode(sealed);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
            return cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("cannot open the TOTP secret", e);
        }
    }

    private SecretKeySpec key() {
        if (encodedKey == null || encodedKey.isBlank()) {
            throw new IllegalStateException("no TOTP encryption key configured: emergency login is disabled");
        }
        byte[] key = Base64.getDecoder().decode(encodedKey);
        if (key.length != 32) {
            throw new IllegalStateException("the TOTP encryption key must be 32 bytes (Base64)");
        }
        return new SecretKeySpec(key, "AES");
    }
}
