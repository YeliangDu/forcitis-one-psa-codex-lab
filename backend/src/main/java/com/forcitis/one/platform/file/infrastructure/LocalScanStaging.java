package com.forcitis.one.platform.file.infrastructure;

import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.platform.file.api.ContentHash;
import com.forcitis.one.platform.file.domain.ScanStaging;
import com.forcitis.one.platform.file.domain.StagingContent;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One subdirectory per process under the staging root (ADR-0070 section 3): created at start-up with a fresh id, so a restart never
 * clears another instance's files; old subdirectories are left to the age sweep. Files are readable by the group clamd shares and by
 * nobody else; operations make the root setgid to that group.
 */
final class LocalScanStaging implements ScanStaging {
    private static final Logger LOG = LoggerFactory.getLogger(LocalScanStaging.class);

    private final Path root;
    private final Path instance;
    private final long maxBytes;
    private final Clock clock;

    LocalScanStaging(Path root, long maxBytes, Clock clock) {
        this.root = root;
        this.maxBytes = maxBytes;
        this.clock = clock;
        try {
            this.instance = Files.createDirectory(root.resolve("instance-" + Uuids.timeOrdered(clock)),
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwxr-x---")));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot create this instance's scan staging directory under " + root, e);
        }
    }

    @Override
    public StagingContent write(InputStream content) throws TooLarge {
        Path file = instance.resolve(Uuids.timeOrdered(clock).toString());
        MessageDigest sha256 = sha256();
        long size = 0;
        try (FileChannel out = FileChannel.open(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r-----"));
            byte[] buffer = new byte[64 * 1024];
            for (int read = content.read(buffer); read >= 0; read = content.read(buffer)) {
                size += read;
                if (size > maxBytes) {
                    out.close();
                    Files.deleteIfExists(file);
                    throw new TooLarge(maxBytes);
                }
                sha256.update(buffer, 0, read);
                ByteBuffer chunk = ByteBuffer.wrap(buffer, 0, read);
                while (chunk.hasRemaining()) {
                    out.write(chunk);
                }
            }
            out.force(true);
        } catch (IOException e) {
            deleteQuietly(file);
            throw new UncheckedIOException("cannot write the upload to the scan staging directory", e);
        }
        return new StagingContent(file, new ContentHash(HexFormat.of().formatHex(sha256.digest())), size);
    }

    @Override
    public void delete(StagingContent staged) {
        try {
            Files.deleteIfExists(staged.path());
        } catch (IOException e) {
            LOG.error("ALERT cannot delete a staged upload; the age sweep will retry", e);
        }
    }

    /** Every instance sweeps all subdirectories, without a cluster lock (ADR-0070 section 3 item 3). Returns how many files it removed. */
    int sweep(Duration olderThan) {
        Instant cutoff = clock.instant().minus(olderThan);
        int removed = 0;
        try (Stream<Path> files = Files.walk(root, 2)) {
            for (Path file : files.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)).toList()) {
                if (Files.getLastModifiedTime(file, LinkOption.NOFOLLOW_LINKS).toInstant().isBefore(cutoff) && Files.deleteIfExists(file)) {
                    removed++;
                }
            }
        } catch (IOException e) {
            LOG.error("ALERT the scan staging sweep failed", e);
        }
        if (removed > 0) {
            LOG.error("ALERT the scan staging sweep removed {} leftover uploads", removed);
        }
        return removed;
    }

    Path instanceDirectory() {
        return instance;
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            LOG.error("ALERT cannot delete a failed upload from the scan staging directory", e);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
