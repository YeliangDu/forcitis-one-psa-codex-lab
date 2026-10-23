package com.forcitis.one.platform.file.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.platform.file.api.FileId;
import com.forcitis.one.platform.file.api.FileStatus;
import com.forcitis.one.platform.file.api.FileSubjectKind;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StoredFileTest {
    static final Instant STORED = Instant.parse("2026-10-20T02:00:00Z");
    static final Duration KEPT = Duration.ofDays(2);

    private static StoredFile file(boolean bound, FileStatus status) {
        return StoredFile.restore(new FileId(UUID.randomUUID()), FileSubjectKind.USER, STORED, bound, status, 0);
    }

    @Test
    @DisplayName("an available file that no document has and whose staging time has passed expires; the status after is final")
    void expires() {
        StoredFile file = file(false, FileStatus.AVAILABLE);
        file.expire(STORED.plus(KEPT), KEPT);
        assertThat(file.status()).isEqualTo(FileStatus.EXPIRED_UNBOUND);
        assertThat(file.loadedStatus()).isEqualTo(FileStatus.AVAILABLE);
        assertThat(StoredFile.MACHINE.successors(FileStatus.EXPIRED_UNBOUND)).isEmpty();
        assertThat(StoredFile.MACHINE.successors(FileStatus.QUARANTINED)).isEmpty();
    }

    @Test
    @DisplayName("a file still within its staging time, one that a document has, and one in another status are refused")
    void refused() {
        assertThat(file(false, FileStatus.AVAILABLE).hasExpired(STORED.plus(KEPT).minusSeconds(1), KEPT)).isFalse();
        assertThat(file(true, FileStatus.AVAILABLE).hasExpired(STORED.plus(KEPT), KEPT)).isFalse();
        assertThat(file(false, FileStatus.QUARANTINED).hasExpired(STORED.plus(KEPT), KEPT)).isFalse();
        assertThatThrownBy(() -> file(false, FileStatus.AVAILABLE).expire(STORED.plus(KEPT).minusSeconds(1), KEPT)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> file(true, FileStatus.AVAILABLE).expire(STORED.plus(KEPT), KEPT)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> file(false, FileStatus.QUARANTINED).expire(STORED.plus(KEPT), KEPT)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> file(false, FileStatus.EXPIRED_UNBOUND).expire(STORED.plus(KEPT), KEPT)).isInstanceOf(DomainException.class);
    }
}
