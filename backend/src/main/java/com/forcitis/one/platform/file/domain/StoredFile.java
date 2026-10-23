package com.forcitis.one.platform.file.domain;

import com.forcitis.one.kernel.AggregateRoot;
import com.forcitis.one.kernel.StateMachine;
import com.forcitis.one.platform.file.api.FileId;
import com.forcitis.one.platform.file.api.FileStatus;
import com.forcitis.one.platform.file.api.FileSubjectKind;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * A stored file as far as its lifecycle goes: a file that no document has linked within the time its staging copy is kept goes from
 * AVAILABLE to EXPIRED_UNBOUND, and that is final (ADR-0060 section 5). Quarantine is decided when the row is written and never changes here.
 */
public final class StoredFile extends AggregateRoot {
    public static final StateMachine<FileStatus> MACHINE = StateMachine.of(FileStatus.class)
            .allow(FileStatus.AVAILABLE, FileStatus.EXPIRED_UNBOUND)
            .build();

    private final FileId id;
    private final FileSubjectKind uploaderKind;
    private final Instant storedAt;
    private final boolean bound;
    private final FileStatus loadedStatus;
    private FileStatus status;

    private StoredFile(FileId id, FileSubjectKind uploaderKind, Instant storedAt, boolean bound, FileStatus status, long version) {
        super(version, 0);
        this.id = Objects.requireNonNull(id, "id");
        this.uploaderKind = Objects.requireNonNull(uploaderKind, "uploaderKind");
        this.storedAt = Objects.requireNonNull(storedAt, "storedAt");
        this.bound = bound;
        this.status = Objects.requireNonNull(status, "status");
        this.loadedStatus = status;
    }

    /** Rebuilds a stored file; bound says whether any document has it. */
    public static StoredFile restore(FileId id, FileSubjectKind uploaderKind, Instant storedAt, boolean bound, FileStatus status, long version) {
        return new StoredFile(id, uploaderKind, storedAt, bound, status, version);
    }

    /** True when the file is available, has no document and was stored at least keptFor ago: the only case in which {@link #expire} applies. */
    public boolean isUnboundAndOlderThan(Instant now, Duration keptFor) {
        return status == FileStatus.AVAILABLE && !bound && !storedAt.plus(keptFor).isAfter(now);
    }

    /** Only an available file that no document has and that was stored at least keptFor ago; anything else is refused. */
    public void expire(Instant now, Duration keptFor) {
        if (bound) {
            throw new IllegalStateException("file " + id + " is linked to a document and does not expire");
        }
        if (storedAt.plus(keptFor).isAfter(now)) {
            throw new IllegalStateException("file " + id + " is still within the time its staging copy is kept");
        }
        status = MACHINE.require(status, FileStatus.EXPIRED_UNBOUND);
    }

    public FileId id() {
        return id;
    }

    public FileSubjectKind uploaderKind() {
        return uploaderKind;
    }

    public FileStatus status() {
        return status;
    }

    public FileStatus loadedStatus() {
        return loadedStatus;
    }
}
