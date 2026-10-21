package com.forcitis.one.platform.file.application;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ExternalSystemUnavailableException;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.platform.file.api.ContentHash;
import com.forcitis.one.platform.file.api.FileError;
import com.forcitis.one.platform.file.api.FileId;
import com.forcitis.one.platform.file.api.FileStatus;
import com.forcitis.one.platform.file.api.FileSubject;
import com.forcitis.one.platform.file.api.Lineage;
import com.forcitis.one.platform.file.api.StoreRequest;
import com.forcitis.one.platform.file.api.StoreResult;
import com.forcitis.one.platform.file.domain.ContentChecks;
import com.forcitis.one.platform.file.domain.ContentFacts;
import com.forcitis.one.platform.file.domain.LineageTarget;
import com.forcitis.one.platform.file.domain.MacroNames;
import com.forcitis.one.platform.file.domain.NewStoredFile;
import com.forcitis.one.platform.file.domain.ScanStaging;
import com.forcitis.one.platform.file.domain.StagingContent;
import com.forcitis.one.platform.file.domain.StoreAudit;
import com.forcitis.one.platform.file.domain.StoreAudit.UnavailableCause;
import com.forcitis.one.platform.file.domain.StoredFileRepository;
import com.forcitis.one.platform.integration.api.BusinessFileStore;
import com.forcitis.one.platform.integration.api.MalwareScanner;
import com.forcitis.one.platform.integration.api.ScanOutcome;
import com.forcitis.one.platform.integration.api.ScanVerdict;
import com.forcitis.one.platform.integration.api.StoredObjectKey;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Stores content behind the malware gate (ADR-0060 section 5, ADR-0070 section 2): one local copy is hashed, scanned in the request and
 * inspected; only then is it written to the bucket and only then does the file row exist. Encrypted content and an unavailable scanner
 * create no file; infected content is quarantined; nothing is ever passed unscanned.
 */
public final class StoreContent {
    private static final Logger LOG = LoggerFactory.getLogger(StoreContent.class);

    private final ScanStaging staging;
    private final MalwareScanner scanner;
    private final ContentChecks checks;
    private final BusinessFileStore store;
    private final StoredFileRepository files;
    private final StoreAudit audit;
    private final FileKeys keys;
    private final Set<String> allowedMediaTypes;
    private final IdGenerator ids;
    private final Clock clock;
    private final TransactionTemplate transaction;
    private final MeterRegistry meters;
    private final Timer scanDuration;

    public StoreContent(ScanStaging staging, MalwareScanner scanner, ContentChecks checks, BusinessFileStore store, StoredFileRepository files,
                        StoreAudit audit, FileKeys keys, Set<String> allowedMediaTypes, IdGenerator ids, Clock clock, PlatformTransactionManager transactions,
                        MeterRegistry meters) {
        this.staging = staging;
        this.scanner = scanner;
        this.checks = checks;
        this.store = store;
        this.files = files;
        this.audit = audit;
        this.keys = keys;
        this.allowedMediaTypes = Set.copyOf(allowedMediaTypes);
        this.ids = ids;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactions);
        this.meters = meters;
        this.scanDuration = Timer.builder("forcitis.platform.file.scans").register(meters);
    }

    public StoreResult store(StoreRequest request) {
        requireSubject(request.uploader());
        requireLineage(request.lineage());
        StagingContent staged;
        try {
            staged = staging.write(request.content());
        } catch (ScanStaging.TooLarge e) {
            count("too_large");
            throw FileError.PLATFORM_FILE_TOO_LARGE.refusal(e.getMessage());
        } catch (UncheckedIOException e) {
            throw unavailable(request.uploader(), null, UnavailableCause.STAGING, e.getMessage());
        }
        try {
            return decide(request, staged);
        } finally {
            staging.delete(staged);
        }
    }

    private StoreResult decide(StoreRequest request, StagingContent staged) {
        ScanVerdict verdict = scan(request.uploader(), staged);
        ContentFacts facts = checks.check(staged.path());
        if (verdict.outcome() == ScanOutcome.ENCRYPTED || facts.encrypted()) {
            audit.encryptedRefused(request.uploader(), staged.contentHash(), request.originalName());
            LOG.error("ALERT encrypted upload refused: it cannot be scanned");
            count("encrypted");
            throw FileError.PLATFORM_FILE_ENCRYPTED_REJECTED.refusal("encrypted content cannot be scanned; remove the open password and upload again");
        }
        boolean infected = verdict.outcome() == ScanOutcome.INFECTED;
        boolean released = infected && files.hasScanException(staged.contentHash());
        FileStatus status = NewStoredFile.decidedBy(infected, released);
        FileId id = new FileId(ids.next());
        Instant now = clock.instant();
        boolean macros = facts.containsMacros() || MacroNames.matches(request.originalName());
        if (status == FileStatus.QUARANTINED) {
            return quarantine(request, staged, facts, verdict, id, status, macros, now);
        }
        if (!allowedMediaTypes.contains(facts.mediaType())) {
            count("media_type_not_allowed");
            throw FileError.PLATFORM_FILE_MEDIA_TYPE_NOT_ALLOWED.refusal("the content is " + facts.mediaType());
        }
        NewStoredFile file = file(request, request.lineage(), staged, facts, verdict, id, keys.formal(id, now), status, released, macros, now);
        write(request.uploader(), staged, keys.staging(id, request.uploader().kind()));
        transaction.executeWithoutResult(tx -> {
            files.insert(file);
            audit.stored(file);
        });
        count(released ? "released_by_exception" : "stored");
        return result(file);
    }

    /**
     * Malware is reported before anything else can fail, and a failed quarantine is audited with its threat (ADR-0070 section 2 item 4).
     * A quarantined file declares no lineage, so it never takes the successor place of a previous version.
     */
    private StoreResult quarantine(StoreRequest request, StagingContent staged, ContentFacts facts, ScanVerdict verdict, FileId id, FileStatus status,
                                   boolean macros, Instant now) {
        LOG.error("ALERT malware found in an upload: file {}, threat {}", id, verdict.threatName());
        count("infected");
        NewStoredFile file = file(request, new Lineage.FirstVersion(), staged, facts, verdict, id, keys.quarantine(id), status, false, macros, now);
        try {
            store.put(keys.quarantine(id), staged.path(), staged.contentHash().value(), staged.size());
            transaction.executeWithoutResult(tx -> {
                files.insert(file);
                audit.quarantined(file);
            });
        } catch (RuntimeException e) {
            audit.quarantineFailed(request.uploader(), staged.contentHash(), verdict.threatName());
            LOG.error("ALERT the quarantine of file {} failed; the upload is refused", id, e);
            throw FileError.PLATFORM_FILE_SCAN_UNAVAILABLE.refusal("the file cannot be checked now; try again later");
        }
        return result(file);
    }

    private ScanVerdict scan(FileSubject uploader, StagingContent staged) {
        Timer.Sample sample = Timer.start(meters);
        try {
            return scanner.scan(staged.path());
        } catch (ExternalSystemUnavailableException e) {
            throw unavailable(uploader, staged.contentHash(), UnavailableCause.SCANNER, e.getMessage());
        } finally {
            sample.stop(scanDuration);
        }
    }

    private void write(FileSubject uploader, StagingContent staged, StoredObjectKey key) {
        try {
            store.put(key, staged.path(), staged.contentHash().value(), staged.size());
        } catch (ExternalSystemUnavailableException e) {
            throw unavailable(uploader, staged.contentHash(), UnavailableCause.OBJECT_STORE, e.getMessage());
        }
    }

    private DomainException unavailable(FileSubject uploader, ContentHash contentHash, UnavailableCause cause, String detail) {
        audit.unavailable(uploader, contentHash, cause);
        LOG.error("ALERT file upload refused, the malware gate is unavailable ({}): {}", cause, detail);
        count("unavailable");
        return FileError.PLATFORM_FILE_SCAN_UNAVAILABLE.refusal("the file cannot be checked now; try again later");
    }

    /** The subject id is the one column its kind names (one-data H4): a user or link id, or a platform module code. */
    static void requireSubject(FileSubject subject) {
        boolean wellFormed = switch (subject.kind()) {
            case USER, ONE_TIME_LINK -> subject.id().matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
            case PLATFORM_MODULE -> subject.id().matches("platform\\.[a-z]+");
        };
        if (!wellFormed) {
            throw new IllegalArgumentException("a " + subject.kind() + " subject has a malformed id");
        }
    }

    private void requireLineage(Lineage lineage) {
        switch (lineage) {
            case Lineage.FirstVersion first -> {
            }
            case Lineage.NextVersion next -> {
                LineageTarget previous = target(next.previous());
                if (previous.hasSuccessor()) {
                    throw FileError.PLATFORM_FILE_PREVIOUS_VERSION_HAS_SUCCESSOR.refusal("the previous version already has a next version");
                }
            }
            case Lineage.DerivedFrom derived -> target(derived.source());
        }
    }

    private LineageTarget target(FileId file) {
        LineageTarget target = files.lineageTarget(file)
                .orElseThrow(() -> FileError.PLATFORM_FILE_NOT_FOUND.refusal("the referenced file does not exist"));
        if (target.status() != FileStatus.AVAILABLE) {
            throw FileError.PLATFORM_FILE_NOT_AVAILABLE.refusal("the referenced file is not available");
        }
        return target;
    }

    private NewStoredFile file(StoreRequest request, Lineage lineage, StagingContent staged, ContentFacts facts, ScanVerdict verdict, FileId id,
                               StoredObjectKey key, FileStatus status, boolean released, boolean macros, Instant now) {
        String threat = infectedVerdict(verdict) ? verdict.threatName() : null;
        return new NewStoredFile(id, key.value(), staged.contentHash(), staged.size(), facts.mediaType(), request.uploader(), status,
                macros, lineage, verdict.engineVersion(), verdict.definitionsVersion(), threat, released,
                request.originalName(), now);
    }

    private static boolean infectedVerdict(ScanVerdict verdict) {
        return verdict.outcome() == ScanOutcome.INFECTED;
    }

    private static StoreResult result(NewStoredFile file) {
        return new StoreResult(file.id(), file.contentHash(), file.size(), file.mediaType(), file.status(), file.containsMacros());
    }

    private void count(String outcome) {
        Counter.builder("forcitis.platform.file.store.outcomes").tag("outcome", outcome.toLowerCase(Locale.ROOT)).register(meters).increment();
    }
}
