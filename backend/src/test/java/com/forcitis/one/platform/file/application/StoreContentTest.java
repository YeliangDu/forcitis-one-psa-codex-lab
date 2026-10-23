package com.forcitis.one.platform.file.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ExternalSystemUnavailableException;
import com.forcitis.one.platform.file.api.ContentHash;
import com.forcitis.one.platform.file.api.FileError;
import com.forcitis.one.platform.file.api.FileId;
import com.forcitis.one.platform.file.api.FileStatus;
import com.forcitis.one.platform.file.api.FileSubject;
import com.forcitis.one.platform.file.api.FileSubjectKind;
import com.forcitis.one.platform.file.api.Lineage;
import com.forcitis.one.platform.file.api.StoreRequest;
import com.forcitis.one.platform.file.api.StoreResult;
import com.forcitis.one.platform.file.domain.ContentFacts;
import com.forcitis.one.platform.file.domain.LineageTarget;
import com.forcitis.one.platform.file.domain.NewStoredFile;
import com.forcitis.one.platform.file.domain.ScanStaging;
import com.forcitis.one.platform.file.domain.StagingContent;
import com.forcitis.one.platform.file.domain.StoreAudit;
import com.forcitis.one.platform.file.domain.StoreAudit.UnavailableCause;
import com.forcitis.one.platform.file.domain.StoredFileRepository;
import com.forcitis.one.platform.integration.api.BusinessFileStore;
import com.forcitis.one.platform.integration.api.ScanOutcome;
import com.forcitis.one.platform.integration.api.ScanVerdict;
import com.forcitis.one.platform.integration.api.StoredObjectKey;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

/** ADR-0070 section 2: what each decision of the malware gate does, and that the staged copy is gone on every exit. */
class StoreContentTest {
    static final String USER = "01900000-0000-7000-8000-00000000000a";
    static final ContentHash HASH = new ContentHash("a".repeat(64));

    @TempDir
    Path directory;

    final List<StagingContent> staged = new ArrayList<>();
    final List<StagingContent> deleted = new ArrayList<>();
    final List<StoredObjectKey> written = new ArrayList<>();
    final List<NewStoredFile> inserted = new ArrayList<>();
    final List<String> audited = new ArrayList<>();
    final Map<FileId, LineageTarget> targets = new HashMap<>();
    final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    ScanVerdict verdict = new ScanVerdict(ScanOutcome.CLEAN, null, "ClamAV 1.4.1", "27435");
    RuntimeException scanFailure;
    RuntimeException storeFailure;
    RuntimeException insertFailure;
    boolean tooLarge;
    boolean diskFull;
    boolean exempted;
    ContentFacts facts = new ContentFacts("application/pdf", false, false);

    final ScanStaging staging = new ScanStaging() {
        @Override
        public StagingContent write(InputStream content) throws TooLarge {
            if (tooLarge) {
                throw new TooLarge(100);
            }
            if (diskFull) {
                throw new java.io.UncheckedIOException(new java.io.IOException("No space left on device"));
            }
            try {
                StagingContent s = new StagingContent(Files.write(directory.resolve("staged-" + staged.size()), content.readAllBytes()), HASH, 7);
                staged.add(s);
                return s;
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public void delete(StagingContent s) {
            deleted.add(s);
        }
    };

    final BusinessFileStore store = new BusinessFileStore() {
        @Override
        public void put(StoredObjectKey key, Path source, String sha256Hex, long size) {
            if (storeFailure != null) {
                throw storeFailure;
            }
            written.add(key);
        }

        @Override
        public void promote(StoredObjectKey staging, StoredObjectKey formal, String sha256Hex, long size) {
            throw new AssertionError("not used when storing");
        }

        @Override
        public URI presignDownload(StoredObjectKey formal, Duration validity, String fileName, String mediaType) {
            throw new AssertionError("not used when storing");
        }

        @Override
        public java.time.Duration maxLinkValidity() {
            return java.time.Duration.ofMinutes(15);
        }

        @Override
        public void read(StoredObjectKey key, OutputStream sink) {
            throw new AssertionError("not used when storing");
        }
    };

    final StoredFileRepository files = new StoredFileRepository() {
        @Override
        public void insert(NewStoredFile file) {
            if (insertFailure != null) {
                throw insertFailure;
            }
            inserted.add(file);
        }

        @Override
        public Optional<LineageTarget> lineageTarget(FileId file) {
            return Optional.ofNullable(targets.get(file));
        }

        @Override
        public boolean hasScanException(ContentHash contentHash) {
            return exempted;
        }

        @Override
        public Optional<FileStatus> unboundStatusStoredBy(FileId file, java.util.UUID uploader) {
            throw new AssertionError("not used when storing");
        }

        @Override
        public java.util.List<FileId> unboundStoredBefore(com.forcitis.one.platform.file.api.FileSubjectKind uploaderKind, java.time.Instant cutoff, int limit) {
            throw new AssertionError("not used when storing");
        }

        @Override
        public Optional<com.forcitis.one.platform.file.domain.StoredFile> lockForExpiry(FileId file) {
            throw new AssertionError("not used when storing");
        }

        @Override
        public void saveExpiry(com.forcitis.one.platform.file.domain.StoredFile file, java.time.Instant at) {
            throw new AssertionError("not used when storing");
        }
    };

    final StoreAudit audit = new StoreAudit() {
        @Override
        public void stored(NewStoredFile file) {
            audited.add("stored " + file.status() + (file.releasedByException() ? " by exception" : ""));
        }

        @Override
        public void quarantined(NewStoredFile file) {
            audited.add("quarantined " + file.threatName());
        }

        @Override
        public void encryptedRefused(FileSubject uploader, ContentHash contentHash, long size, String mediaType, String originalName) {
            audited.add("encrypted");
        }

        @Override
        public void quarantineFailed(FileSubject uploader, ContentHash contentHash, String threatName) {
            audited.add("quarantine failed " + threatName);
        }

        @Override
        public void unavailable(FileSubject uploader, ContentHash contentHash, UnavailableCause cause) {
            audited.add("unavailable " + cause);
        }
    };

    private StoreContent gate() {
        AtomicLong next = new AtomicLong();
        PlatformTransactionManager transaction = new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
            }

            @Override
            public void rollback(TransactionStatus status) {
            }
        };
        return new StoreContent(staging, path -> {
            if (scanFailure != null) {
                throw scanFailure;
            }
            return verdict;
        }, path -> facts, store, files, audit, new FileKeys("dev"), Set.of("application/pdf"),
                () -> new UUID(0x0199f0c400007000L, 0x8000000000000000L | next.incrementAndGet()),
                Clock.fixed(Instant.parse("2026-10-21T02:00:00Z"), ZoneOffset.UTC), transaction, meters);
    }

    private StoreRequest request(Lineage lineage) {
        return new StoreRequest(new ByteArrayInputStream("content".getBytes()), "合同.pdf", "application/pdf",
                new FileSubject(FileSubjectKind.USER, USER), lineage);
    }

    private StoreResult store() {
        return gate().store(request(new Lineage.FirstVersion()));
    }

    private double outcome(String name) {
        return meters.find("forcitis.platform.file.store.outcomes").tag("outcome", name).counter().count();
    }

    private void refused(FileError error) {
        assertThatThrownBy(this::store).isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(error));
    }

    @Test
    void cleanContentIsWrittenToStagingThenItsRowExists() {
        StoreResult result = store();
        assertThat(result.status()).isEqualTo(FileStatus.AVAILABLE);
        assertThat(written).singleElement().satisfies(k -> assertThat(k.value()).startsWith("dev/staging/user/"));
        assertThat(inserted).singleElement().satisfies(f -> assertThat(f.objectKey()).startsWith("dev/files/2026/10/").endsWith(f.id().toString()));
        assertThat(audited).containsExactly("stored AVAILABLE");
        assertThat(deleted).isEqualTo(staged);
        assertThat(outcome("stored")).isEqualTo(1);
    }

    @Test
    void infectedContentIsQuarantinedNotStaged() {
        verdict = new ScanVerdict(ScanOutcome.INFECTED, "Eicar-Signature", "ClamAV 1.4.1", "27435");
        StoreResult result = store();
        assertThat(result.status()).isEqualTo(FileStatus.QUARANTINED);
        assertThat(written).singleElement().satisfies(k -> assertThat(k.value()).startsWith("dev/quarantine/"));
        assertThat(inserted).singleElement().satisfies(f -> assertThat(f.threatName()).isEqualTo("Eicar-Signature"));
        assertThat(audited).containsExactly("quarantined Eicar-Signature");
        assertThat(deleted).isEqualTo(staged);
    }

    @Test
    void infectedContentOnTheExceptionListIsReleasedAndSaysSo() {
        verdict = new ScanVerdict(ScanOutcome.INFECTED, "Heuristic.FalseHit", "ClamAV 1.4.1", "27435");
        exempted = true;
        assertThat(store().status()).isEqualTo(FileStatus.AVAILABLE);
        assertThat(inserted).singleElement().satisfies(f -> {
            assertThat(f.releasedByException()).isTrue();
            assertThat(f.threatName()).isEqualTo("Heuristic.FalseHit");
        });
        assertThat(audited).containsExactly("stored AVAILABLE by exception");
        assertThat(outcome("released_by_exception")).isEqualTo(1);
    }

    @Test
    void encryptedContentCreatesNoFile() {
        verdict = new ScanVerdict(ScanOutcome.ENCRYPTED, null, "ClamAV 1.4.1", "27435");
        refused(FileError.PLATFORM_FILE_ENCRYPTED_REJECTED);
        facts = new ContentFacts("application/pdf", true, false);
        verdict = new ScanVerdict(ScanOutcome.CLEAN, null, "ClamAV 1.4.1", "27435");
        refused(FileError.PLATFORM_FILE_ENCRYPTED_REJECTED);
        assertThat(written).isEmpty();
        assertThat(inserted).isEmpty();
        assertThat(audited).containsExactly("encrypted", "encrypted");
        assertThat(deleted).isEqualTo(staged);
        assertThat(outcome("encrypted")).isEqualTo(2);
    }

    @Test
    void anUnavailableScannerOrStoreCreatesNoFileAndIsNeverPassed() {
        scanFailure = new ExternalSystemUnavailableException("clamd unreachable");
        refused(FileError.PLATFORM_FILE_SCAN_UNAVAILABLE);
        scanFailure = null;
        storeFailure = new ExternalSystemUnavailableException("read back differs");
        refused(FileError.PLATFORM_FILE_SCAN_UNAVAILABLE);
        assertThat(inserted).isEmpty();
        assertThat(audited).containsExactly("unavailable SCANNER", "unavailable OBJECT_STORE");
        assertThat(deleted).isEqualTo(staged);
        assertThat(outcome("unavailable")).isEqualTo(2);
    }

    @Test
    void aFailedQuarantineIsAuditedWithItsThreatAndRefused() {
        verdict = new ScanVerdict(ScanOutcome.INFECTED, "Eicar-Signature", "ClamAV 1.4.1", "27435");
        storeFailure = new ExternalSystemUnavailableException("quarantine write failed");
        refused(FileError.PLATFORM_FILE_SCAN_UNAVAILABLE);
        storeFailure = null;
        insertFailure = new IllegalStateException("database down");
        refused(FileError.PLATFORM_FILE_SCAN_UNAVAILABLE);
        assertThat(inserted).isEmpty();
        assertThat(audited).containsExactly("quarantine failed Eicar-Signature", "quarantine failed Eicar-Signature");
        assertThat(outcome("infected")).isEqualTo(2);
        assertThat(deleted).isEqualTo(staged);
    }

    @Test
    void infectedContentOfAnyTypeIsQuarantinedNotJustRefused() {
        verdict = new ScanVerdict(ScanOutcome.INFECTED, "Win.Trojan", "ClamAV 1.4.1", "27435");
        facts = new ContentFacts("application/x-msdownload", false, false);
        assertThat(store().status()).isEqualTo(FileStatus.QUARANTINED);
        assertThat(audited).containsExactly("quarantined Win.Trojan");
    }

    @Test
    void infectedContentThatAlsoLooksEncryptedIsQuarantined() {
        verdict = new ScanVerdict(ScanOutcome.INFECTED, "Doc.Dropper", "ClamAV 1.4.1", "27435");
        facts = new ContentFacts("application/x-ole-storage", true, true);
        assertThat(store().status()).isEqualTo(FileStatus.QUARANTINED);
        assertThat(audited).containsExactly("quarantined Doc.Dropper");
    }

    @Test
    void aQuarantinedFileDeclaresNoLineage() {
        FileId previous = new FileId(UUID.fromString("0199f0c4-0000-7000-8000-0000000000f1"));
        targets.put(previous, new LineageTarget(FileStatus.AVAILABLE, false));
        verdict = new ScanVerdict(ScanOutcome.INFECTED, "Eicar-Signature", "ClamAV 1.4.1", "27435");
        gate().store(request(new Lineage.NextVersion(previous)));
        assertThat(inserted).singleElement().satisfies(f -> assertThat(f.lineage()).isInstanceOf(Lineage.FirstVersion.class));
    }

    @Test
    void aFullStagingDiskIsUnavailable() {
        diskFull = true;
        refused(FileError.PLATFORM_FILE_SCAN_UNAVAILABLE);
        assertThat(audited).containsExactly("unavailable STAGING");
    }

    @Test
    void aMacroExtensionCountsTogetherWithTheContent() {
        StoreResult result = gate().store(new StoreRequest(new ByteArrayInputStream("content".getBytes()), "报价.XLSM ", "application/pdf",
                new FileSubject(FileSubjectKind.USER, USER), new Lineage.FirstVersion()));
        assertThat(result.containsMacros()).isTrue();
        assertThat(store().containsMacros()).isFalse();
    }

    @Test
    void tooLargeIsRefusedBeforeAnyScan() {
        tooLarge = true;
        refused(FileError.PLATFORM_FILE_TOO_LARGE);
        assertThat(audited).isEmpty();
        assertThat(outcome("too_large")).isEqualTo(1);
    }

    @Test
    void aMediaTypeOutsideTheListIsRefusedAndNothingIsWritten() {
        facts = new ContentFacts("application/x-msdownload", false, false);
        refused(FileError.PLATFORM_FILE_MEDIA_TYPE_NOT_ALLOWED);
        assertThat(written).isEmpty();
        assertThat(deleted).isEqualTo(staged);
    }

    @Test
    void theStagedCopyIsDeletedWhenTheRowCannotBeWritten() {
        insertFailure = new IllegalStateException("database down");
        assertThatThrownBy(this::store).isSameAs(insertFailure);
        assertThat(deleted).isEqualTo(staged);
    }

    @Test
    void aLineageMustPointAtAnAvailableFileWithoutSuccessor() {
        FileId previous = new FileId(UUID.fromString("0199f0c4-0000-7000-8000-0000000000f1"));
        assertThatThrownBy(() -> gate().store(request(new Lineage.NextVersion(previous))))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(FileError.PLATFORM_FILE_NOT_FOUND));
        targets.put(previous, new LineageTarget(FileStatus.AVAILABLE, true));
        assertThatThrownBy(() -> gate().store(request(new Lineage.NextVersion(previous))))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(FileError.PLATFORM_FILE_PREVIOUS_VERSION_HAS_SUCCESSOR));
        targets.put(previous, new LineageTarget(FileStatus.QUARANTINED, false));
        assertThatThrownBy(() -> gate().store(request(new Lineage.DerivedFrom(previous, com.forcitis.one.platform.file.api.DerivationKind.SIGNING))))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(FileError.PLATFORM_FILE_NOT_AVAILABLE));
        assertThat(staged).isEmpty();
    }

    @Test
    void aSubjectIdMustFitItsKind() {
        assertThatThrownBy(() -> StoreContent.requireSubject(new FileSubject(FileSubjectKind.USER, "alice"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StoreContent.requireSubject(new FileSubject(FileSubjectKind.PLATFORM_MODULE, "cost.timesheet")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
