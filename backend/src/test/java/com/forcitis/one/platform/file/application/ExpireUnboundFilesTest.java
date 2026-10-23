package com.forcitis.one.platform.file.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.forcitis.one.platform.file.api.FileId;
import com.forcitis.one.platform.file.api.FileStatus;
import com.forcitis.one.platform.file.api.FileSubjectKind;
import com.forcitis.one.platform.file.domain.StoredFile;
import com.forcitis.one.platform.file.domain.StoredFileRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class ExpireUnboundFilesTest {
    static final Instant NOW = Instant.parse("2026-10-23T02:00:00Z");
    static final Instant OLD = NOW.minus(Duration.ofDays(3));
    static final Map<FileSubjectKind, Duration> KEPT = Map.of(FileSubjectKind.USER, Duration.ofDays(2), FileSubjectKind.PLATFORM_MODULE, Duration.ofDays(2),
            FileSubjectKind.ONE_TIME_LINK, Duration.ofDays(16));

    final StoredFileRepository files = mock(StoredFileRepository.class);
    final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    final ExpireUnboundFiles job;

    ExpireUnboundFilesTest() {
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        job = new ExpireUnboundFiles(files, KEPT, Clock.fixed(NOW, ZoneOffset.UTC), transactions, meters);
    }

    private static StoredFile stored(FileId id, boolean bound) {
        return StoredFile.restore(id, FileSubjectKind.USER, OLD, bound, FileStatus.AVAILABLE, 0);
    }

    @Test
    @DisplayName("each kind of uploader is judged with its own staging time")
    void ownTimePerKind() {
        job.execute();
        verify(files).unboundStoredBefore(FileSubjectKind.USER, NOW.minus(Duration.ofDays(2)), ExpireUnboundFiles.BATCH);
        verify(files).unboundStoredBefore(FileSubjectKind.PLATFORM_MODULE, NOW.minus(Duration.ofDays(2)), ExpireUnboundFiles.BATCH);
        verify(files).unboundStoredBefore(FileSubjectKind.ONE_TIME_LINK, NOW.minus(Duration.ofDays(16)), ExpireUnboundFiles.BATCH);
    }

    @Test
    @DisplayName("a candidate is expired under the lock and counted; one a document linked meanwhile is left alone")
    void expiresUnderTheLock() {
        FileId free = new FileId(UUID.randomUUID());
        FileId linked = new FileId(UUID.randomUUID());
        when(files.unboundStoredBefore(eq(FileSubjectKind.USER), any(), eq(ExpireUnboundFiles.BATCH))).thenReturn(List.of(free, linked));
        when(files.lockForExpiry(free)).thenReturn(Optional.of(stored(free, false)));
        when(files.lockForExpiry(linked)).thenReturn(Optional.of(stored(linked, true)));

        assertThat(job.execute()).isEqualTo(1);

        verify(files).saveExpiry(any(StoredFile.class), eq(NOW));
        assertThat(meters.get("forcitis.platform.file.unbound.expired").counter().count()).isEqualTo(1);
    }

    @Test
    @DisplayName("a file that cannot be written is reported, counted, and does not stop the others")
    void oneFailureDoesNotStopTheRun() {
        FileId broken = new FileId(UUID.randomUUID());
        FileId fine = new FileId(UUID.randomUUID());
        when(files.unboundStoredBefore(eq(FileSubjectKind.USER), any(), eq(ExpireUnboundFiles.BATCH))).thenReturn(List.of(broken, fine));
        when(files.lockForExpiry(broken)).thenReturn(Optional.of(stored(broken, false)));
        when(files.lockForExpiry(fine)).thenReturn(Optional.of(stored(fine, false)));
        doThrow(new IllegalStateException("changed")).when(files).saveExpiry(any(StoredFile.class), any());
        assertThat(job.execute()).isZero();
        assertThat(meters.get("forcitis.platform.file.unbound.expiry.failures").counter().count()).isEqualTo(2);

        org.mockito.Mockito.reset(files);
        when(files.unboundStoredBefore(eq(FileSubjectKind.USER), any(), eq(ExpireUnboundFiles.BATCH))).thenReturn(List.of(fine));
        when(files.lockForExpiry(fine)).thenReturn(Optional.of(stored(fine, false)));
        assertThat(job.execute()).isEqualTo(1);
    }

    @Test
    @DisplayName("a missing staging time for a kind of uploader stops the construction")
    void everyKindNeedsATime() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new ExpireUnboundFiles(files, Map.of(FileSubjectKind.USER, Duration.ofDays(2)),
                Clock.systemUTC(), transactions, meters)).isInstanceOf(IllegalArgumentException.class);
        verify(files, never()).saveExpiry(any(), any());
    }
}
