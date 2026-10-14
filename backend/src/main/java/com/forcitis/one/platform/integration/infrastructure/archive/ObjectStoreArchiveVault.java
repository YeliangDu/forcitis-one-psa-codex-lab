package com.forcitis.one.platform.integration.infrastructure.archive;

import com.forcitis.one.kernel.ExternalSystemUnavailableException;
import com.forcitis.one.platform.integration.api.ArchiveVault;
import com.forcitis.one.platform.integration.api.ArchivedObject;
import com.forcitis.one.platform.integration.domain.ObjectStore;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

/**
 * temporary file in the local directory, SHA-256, store, compare what the store confirms, then make the file the newest local copy of
 * its kind. Each object has its own name (kind, time, hash prefix), so two nodes never write the same object, and the store refuses
 * to overwrite.
 */
final class ObjectStoreArchiveVault implements ArchiveVault {
    private static final Pattern KIND = Pattern.compile("[a-z]+");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    private final ObjectStore store;
    private final Path localDirectory;
    private final String prefix;
    private final Clock clock;
    private final int wormLockDays;
    private final int totalKeepYears;

    ObjectStoreArchiveVault(ObjectStore store, Path localDirectory, String prefix, int wormLockDays, int totalKeepYears, Clock clock) {
        this.store = store;
        this.localDirectory = localDirectory;
        this.prefix = prefix;
        this.wormLockDays = wormLockDays;
        this.totalKeepYears = totalKeepYears;
        this.clock = clock;
    }

    @Override
    public void requireTotalKeepPeriod(String what, Period online) {
        KeepPeriodRule.require(what, online, wormLockDays, totalKeepYears);
    }

    @Override
    public ArchivedObject archive(String kind, List<String> lines) {
        Path directory = kindDirectory(kind);
        Path pending = directory.resolve("pending-" + UUID.randomUUID() + ".ndjson.gz");
        try {
            Files.createDirectories(directory);
            String sha256 = write(pending, lines);
            var now = clock.instant().atOffset(ZoneOffset.UTC);
            String key = prefix + "/" + kind + "/" + now.getYear() + "/" + kind + "-" + STAMP.format(now) + "-" + sha256.substring(0, 12) + ".ndjson.gz";
            ArchivedObject stored = store.put(key, pending, sha256);
            if (stored == null || stored.size() != Files.size(pending) || !sha256.equals(stored.sha256())) {
                throw new ExternalSystemUnavailableException("the object store confirmed " + stored + " for " + key + ", expected " + Files.size(pending)
                        + " bytes with SHA-256 " + sha256);
            }
            Files.move(pending, directory.resolve("latest.ndjson.gz"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return stored;
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write the local archive file in " + directory, e);
        } finally {
            try {
                Files.deleteIfExists(pending);
            } catch (IOException ignored) {
                // a leftover pending file is overwritten by nothing and removed by the next probe
            }
        }
    }

    @Override
    public void probe(String kind) {
        Path directory = kindDirectory(kind);
        try {
            Files.createDirectories(directory);
            Path probe = Files.createTempFile(directory, "probe-", ".tmp");
            Files.writeString(probe, "probe");
            Files.delete(probe);
            try (Stream<Path> leftovers = Files.list(directory)) {
                for (Path file : leftovers.filter(f -> f.getFileName().toString().startsWith("pending-")).toList()) {
                    Files.deleteIfExists(file);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write to the local archive directory " + directory, e);
        }
        store.probe();
    }

    private Path kindDirectory(String kind) {
        if (!KIND.matcher(kind).matches()) {
            throw new IllegalArgumentException("archive kind is lower-case letters: " + kind);
        }
        return localDirectory.resolve(kind);
    }

    private static String write(Path file, List<String> lines) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (OutputStream out = new DigestOutputStream(Files.newOutputStream(file), digest);
                 GZIPOutputStream gzip = new GZIPOutputStream(out)) {
                for (String line : lines) {
                    gzip.write((line + "\n").getBytes(StandardCharsets.UTF_8));
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
