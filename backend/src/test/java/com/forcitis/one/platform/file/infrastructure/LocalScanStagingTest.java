package com.forcitis.one.platform.file.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.platform.file.domain.ScanStaging;
import com.forcitis.one.platform.file.domain.StagingContent;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** ADR-0070 section 3: one write while hashing, nothing left beyond the limit, group-readable only, the age sweep spares fresh files. */
class LocalScanStagingTest {
    static final Instant NOW = Instant.parse("2026-10-21T02:00:00Z");

    @TempDir
    Path root;

    private LocalScanStaging staging(long max) {
        return new LocalScanStaging(root, max, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void contentIsWrittenOnceWithItsSha256AndOnlyTheGroupCanReadIt() throws Exception {
        LocalScanStaging staging = staging(100);
        StagingContent staged = staging.write(new ByteArrayInputStream("content".getBytes()));
        assertThat(staged.contentHash().value()).isEqualTo("ed7002b439e9ac845f22357d822bac1444730fbdb6016d3ec9432297b9ec9f73");
        assertThat(staged.size()).isEqualTo(7);
        assertThat(staged.path().getParent()).isEqualTo(staging.instanceDirectory());
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(staged.path()))).isEqualTo("rw-r-----");
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(staging.instanceDirectory()))).isEqualTo("rwxr-x---");
        staging.delete(staged);
        assertThat(staged.path()).doesNotExist();
    }

    @Test
    void contentBeyondTheLimitLeavesNothingBehind() {
        LocalScanStaging staging = staging(4);
        assertThatThrownBy(() -> staging.write(new ByteArrayInputStream("content".getBytes()))).isInstanceOf(ScanStaging.TooLarge.class);
        assertThat(staging.instanceDirectory()).isEmptyDirectory();
    }

    @Test
    void eachInstanceGetsItsOwnDirectoryAndTheSweepRemovesOnlyOldFilesOfAll() throws Exception {
        LocalScanStaging first = staging(100);
        LocalScanStaging second = staging(100);
        assertThat(first.instanceDirectory()).isNotEqualTo(second.instanceDirectory());
        StagingContent old = first.write(new ByteArrayInputStream("old".getBytes()));
        StagingContent fresh = second.write(new ByteArrayInputStream("fresh".getBytes()));
        Files.setLastModifiedTime(old.path(), FileTime.from(NOW.minus(Duration.ofHours(2))));
        Files.setLastModifiedTime(fresh.path(), FileTime.from(NOW.minus(Duration.ofMinutes(5))));
        assertThat(second.sweep(Duration.ofHours(1))).isEqualTo(1);
        assertThat(old.path()).doesNotExist();
        assertThat(fresh.path()).exists();
    }
}
