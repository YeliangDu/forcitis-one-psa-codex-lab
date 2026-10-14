package com.forcitis.one.platform.identity.domain;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Wire form: base64url(payload) "." base64url(HMAC-SHA256(key, payload)), payload = kid|issuer|subject|nonce|issuedAt|expiresAt (epoch seconds). */
public final class SignedTicket {

    public static final Duration MAX_LIFETIME = Duration.ofSeconds(60);
    public static final int MAX_LENGTH = 1024;
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(5);
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_.:-]{1,64}");
    private static final Pattern NONCE = Pattern.compile("[A-Za-z0-9_-]{16,64}");
    private static final Pattern NUMBER = Pattern.compile("-?\\d{1,18}");

    private final String kid;
    private final String issuer;
    private final String subject;
    private final String nonce;
    private final Instant issuedAt;
    private final Instant expiresAt;
    private final byte[] payload;
    private final byte[] signature;

    private SignedTicket(String kid, String issuer, String subject, String nonce, Instant issuedAt, Instant expiresAt,
            byte[] payload, byte[] signature) {
        this.kid = kid;
        this.issuer = issuer;
        this.subject = subject;
        this.nonce = nonce;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.payload = payload;
        this.signature = signature;
    }

    /** The caller must still verify the signature and the time window. */
    public static Optional<SignedTicket> parse(String raw) {
        if (raw == null || raw.length() > MAX_LENGTH) {
            return Optional.empty();
        }
        String[] parts = raw.split("\\.", -1);
        if (parts.length != 2) {
            return Optional.empty();
        }
        try {
            byte[] payload = Base64.getUrlDecoder().decode(parts[0]);
            byte[] signature = Base64.getUrlDecoder().decode(parts[1]);
            String[] fields = new String(payload, StandardCharsets.UTF_8).split("\\|", -1);
            if (fields.length != 6 || !TOKEN.matcher(fields[0]).matches() || !TOKEN.matcher(fields[1]).matches()
                    || !NUMBER.matcher(fields[2]).matches() || !NONCE.matcher(fields[3]).matches()
                    || !NUMBER.matcher(fields[4]).matches() || !NUMBER.matcher(fields[5]).matches()) {
                return Optional.empty();
            }
            return Optional.of(new SignedTicket(fields[0], fields[1], fields[2], fields[3],
                    Instant.ofEpochSecond(Long.parseLong(fields[4])), Instant.ofEpochSecond(Long.parseLong(fields[5])),
                    payload, signature));
        } catch (IllegalArgumentException | java.time.DateTimeException e) {
            return Optional.empty();
        }
    }

    public static String sign(byte[] key, String kid, String issuer, long subject, String nonce, Instant issuedAt, Instant expiresAt) {
        byte[] payload = (kid + "|" + issuer + "|" + subject + "|" + nonce + "|" + issuedAt.getEpochSecond() + "|"
                + expiresAt.getEpochSecond()).getBytes(StandardCharsets.UTF_8);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload) + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(mac(key, payload));
    }

    public String kid() {
        return kid;
    }

    public String issuer() {
        return issuer;
    }

    public long subject() {
        return Long.parseLong(subject);
    }

    public String nonce() {
        return nonce;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public boolean isSignedWith(byte[] key) {
        return MessageDigest.isEqual(mac(key, payload), signature);
    }

    public Optional<String> timeProblem(Instant now) {
        if (!expiresAt.isAfter(issuedAt) || Duration.between(issuedAt, expiresAt).compareTo(MAX_LIFETIME) > 0) {
            return Optional.of("lifetime must be positive and at most 60 seconds");
        }
        if (!now.isBefore(expiresAt)) {
            return Optional.of("expired");
        }
        if (issuedAt.isAfter(now.plus(CLOCK_SKEW))) {
            return Optional.of("issued in the future");
        }
        return Optional.empty();
    }

    private static byte[] mac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }
}
