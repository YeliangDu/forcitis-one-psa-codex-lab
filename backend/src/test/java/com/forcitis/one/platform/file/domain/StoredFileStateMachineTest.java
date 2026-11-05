package com.forcitis.one.platform.file.domain;

import static com.forcitis.one.platform.file.api.FileStatus.AVAILABLE;
import static com.forcitis.one.platform.file.api.FileStatus.EXPIRED_UNBOUND;

import com.forcitis.one.TestIds;
import com.forcitis.one.kernel.TransitionTable;
import com.forcitis.one.platform.file.api.FileId;
import com.forcitis.one.platform.file.api.FileStatus;
import com.forcitis.one.platform.file.api.FileSubjectKind;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StoredFileStateMachineTest {
    static final Instant STORED = Instant.parse("2026-10-01T02:00:00Z");
    static final Instant NOW = STORED.plus(Duration.ofDays(3));

    static StoredFile at(FileStatus status) {
        return StoredFile.restore(new FileId(TestIds.uuid(1)), FileSubjectKind.USER, STORED, false, status, 0);
    }

    @Test
    @DisplayName("expire is tried from every status, with an unbound file kept long enough: it leaves only AVAILABLE")
    void exhaustiveTransitions() {
        new TransitionTable<FileStatus, StoredFile>(FileStatus.class, StoredFileStateMachineTest::at, StoredFile::status)
                .operation("expire", f -> f.expire(NOW, Duration.ofDays(1)), AVAILABLE, EXPIRED_UNBOUND)
                .verify();
    }
}
