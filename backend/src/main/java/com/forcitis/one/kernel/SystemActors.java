package com.forcitis.one.kernel;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The id a job, an event handler or an operations entry acts with when it is the actor of an event (RULING-P2-C5): a name-based UUID
 * (RFC 9562 version 5) of a name declared in architecture/system-actors.txt. The catalogue is read once when the class loads, so every
 * instance can name the job behind an id at any time; a name that is not declared cannot act.
 */
public final class SystemActors {
    static final UUID NAMESPACE = UUID.fromString("0d4f6c1e-7b2c-4c39-9e5d-3f1b7c2e6d90");
    public static final String CATALOGUE = "architecture/system-actors.txt";

    private static final Map<String, UUID> IDS;
    private static final Map<UUID, String> NAMES;

    static {
        Map<String, UUID> ids = new HashMap<>();
        Map<UUID, String> names = new HashMap<>();
        try {
            for (URL catalogue : Collections.list(SystemActors.class.getClassLoader().getResources(CATALOGUE))) {
                try (InputStream in = catalogue.openStream()) {
                    read(new String(in.readAllBytes(), StandardCharsets.UTF_8), catalogue.toString(), ids, names);
                }
            }
        } catch (IOException unreadable) {
            throw new IllegalStateException("the system actor catalogue " + CATALOGUE + " cannot be read", unreadable);
        }
        if (ids.isEmpty()) {
            throw new IllegalStateException("the system actor catalogue " + CATALOGUE + " is missing from the class path");
        }
        IDS = Map.copyOf(ids);
        NAMES = Map.copyOf(names);
    }

    private SystemActors() {
    }

    static void read(String text, String where, Map<String, UUID> ids, Map<UUID, String> names) {
        for (String line : text.split("\n")) {
            String row = line.strip();
            if (row.isEmpty() || row.startsWith("#")) {
                continue;
            }
            String[] fields = row.split("\\|");
            if (fields.length != 4) {
                throw new IllegalStateException(where + ": a line is <name> | <uuid> | <module> | <reason>: " + row);
            }
            String name = fields[0].strip();
            UUID id = UUID.fromString(fields[1].strip());
            if (!id.equals(fromName(name))) {
                throw new IllegalStateException(where + ": " + name + " has the id " + fromName(name) + ", not " + id);
            }
            if (ids.putIfAbsent(name, id) != null) {
                throw new IllegalStateException(where + ": " + name + " is declared twice");
            }
            names.put(id, name);
        }
    }

    /** The id of a declared actor; an undeclared name is a programming error. */
    public static UUID idOf(String name) {
        Objects.requireNonNull(name, "name");
        UUID id = IDS.get(name);
        if (id == null) {
            throw new IllegalStateException("system actor " + name + " is not declared in " + CATALOGUE);
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
