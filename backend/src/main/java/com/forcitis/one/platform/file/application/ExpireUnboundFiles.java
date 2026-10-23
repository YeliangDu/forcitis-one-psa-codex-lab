package com.forcitis.one.platform.file.application;

import com.forcitis.one.platform.file.api.FileId;
import com.forcitis.one.platform.file.api.FileSubjectKind;
import com.forcitis.one.platform.file.domain.StoredFile;
import com.forcitis.one.platform.file.domain.StoredFileRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The staging copy of a file that no document linked is removed by the bucket's own lifecycle rule (ADR-0060 section 5); this job makes the
 * row say so. It moves a file to EXPIRED_UNBOUND once its staging time for the kind of uploader has passed, one transaction per file under the
 * row lock that linking also takes, so a link that wins the lock keeps the file and an expiry that wins refuses the link.
 */
public final class ExpireUnboundFiles {
    static final int BATCH = 200;
    private static final Logger LOG = LoggerFactory.getLogger(ExpireUnboundFiles.class);

    private final StoredFileRepository files;
    private final Map<FileSubjectKind, Duration> keptFor;
    private final Clock clock;
    private final TransactionTemplate transaction;
    private final Counter expired;
    private final Counter failed;

    public ExpireUnboundFiles(StoredFileRepository files, Map<FileSubjectKind, Duration> keptFor, Clock clock, PlatformTransactionManager transactions,
                              MeterRegistry meters) {
        for (FileSubjectKind kind : FileSubjectKind.values()) {
            if (keptFor.get(kind) == null) {
                throw new IllegalArgumentException("no staging time for uploaders of kind " + kind);
            }
        }
        this.files = files;
        this.keptFor = Map.copyOf(keptFor);
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactions);
        this.expired = Counter.builder("forcitis.platform.file.unbound.expired").register(meters);
        this.failed = Counter.builder("forcitis.platform.file.unbound.expiry.failures").register(meters);
    }

    /** Returns how many files moved to EXPIRED_UNBOUND; a file that cannot be moved is reported and left for the next run. */
    public int execute() {
        int total = 0;
        for (FileSubjectKind kind : FileSubjectKind.values()) {
            Duration age = keptFor.get(kind);
            List<FileId> batch;
            int moved;
            do {
                batch = files.unboundStoredBefore(kind, clock.instant().minus(age), BATCH);
                moved = 0;
                for (FileId file : batch) {
                    moved += expire(file, age) ? 1 : 0;
                }
                total += moved;
            } while (batch.size() == BATCH && moved > 0);
        }
        return total;
    }

    private boolean expire(FileId file, Duration age) {
        try {
            Boolean moved = transaction.execute(tx -> {
                Optional<StoredFile> locked = files.lockForLifecycle(file);
                Instant now = clock.instant();
                if (locked.isEmpty() || !locked.get().isUnboundAndOlderThan(now, age)) {
                    return false;
                }
                StoredFile stored = locked.get();
                stored.expire(now, age);
                files.saveExpiry(stored, now);
                return true;
            });
            if (Boolean.TRUE.equals(moved)) {
                expired.increment();
                return true;
            }
            return false;
        } catch (RuntimeException e) {
            LOG.error("ALERT platform.file file {} could not be moved to EXPIRED_UNBOUND; the next run tries again", file, e);
            failed.increment();
            return false;
        }
    }
}
