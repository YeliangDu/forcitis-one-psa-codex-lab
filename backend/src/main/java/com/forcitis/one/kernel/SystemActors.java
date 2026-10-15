package com.forcitis.one.kernel;

import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The id a job or an event handler acts with when it is the actor of an event (NEXT_BATCH_PLAN 7.2 C5): a name-based UUID (RFC 9562
 * version 5) of the job's name in a namespace of its own, so the same job always has the same id and no account id (version 7) is ever
 * one. Every name that acts is recorded here, which is how the audit trail names the job behind an id.
 */
public final class SystemActors {
    static final UUID NAMESPACE = UUID.fromString("0d4f6c1e-7b2c-4c39-9e5d-3f1b7c2e6d90");

    private static final Map<UUID, String> NAMES = new ConcurrentHashMap<>();

    private SystemActors() {
    }

    public static UUID idOf(String name) {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("a system actor has a name");
        }
        UUID id = fromName(name);
        String earlier = NAMES.putIfAbsent(id, name);
        if (earlier != null && !earlier.equals(name)) {
            throw new IllegalStateException("two system actors share an id: " + earlier + " and " + name);
        }
        return id;
    }

    public static Optional<String> nameOf(UUID id) {
        return Optional.ofNullable(NAMES.get(id));
    }

    static UUID fromName(String name) {
        MessageDigest sha1;
        try {
            sha1 = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException("SHA-1 is part of every Java runtime", missing);
        }
        sha1.update(ByteBuffer.allocate(16).putLong(NAMESPACE.getMostSignificantBits()).putLong(NAMESPACE.getLeastSignificantBits()).array());
        ByteBuffer hash = ByteBuffer.wrap(sha1.digest(name.getBytes(StandardCharsets.UTF_8)));
        long high = (hash.getLong() & ~0xF000L) | 0x5000L;
        long low = (hash.getLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new UUID(high, low);
    }
}
