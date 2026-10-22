package com.forcitis.one.platform.file.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.DocumentId;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.DocumentTypeCode;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ExternalSystemUnavailableException;
import com.forcitis.one.kernel.Reason;
import com.forcitis.one.platform.file.api.AccessLevel;
import com.forcitis.one.platform.file.api.BindRequest;
import com.forcitis.one.platform.file.api.ContentHash;
import com.forcitis.one.platform.file.api.DownloadLink;
import com.forcitis.one.platform.file.api.FileError;
import com.forcitis.one.platform.file.api.FileId;
import com.forcitis.one.platform.file.api.FileStatus;
import com.forcitis.one.platform.file.api.FileSubject;
import com.forcitis.one.platform.file.api.FileSubjectKind;
import com.forcitis.one.platform.file.api.HoldRequest;
import com.forcitis.one.platform.file.api.Lineage;
import com.forcitis.one.platform.file.api.PersonalInformationMark;
import com.forcitis.one.platform.file.api.PreservationCategoryCode;
import com.forcitis.one.platform.file.api.StoredFileSummary;
import com.forcitis.one.platform.file.application.BindFile;
import com.forcitis.one.platform.file.application.FileKeys;
import com.forcitis.one.platform.file.application.HoldFile;
import com.forcitis.one.platform.file.application.IssueDownloadLink;
import com.forcitis.one.platform.file.application.ListDocumentFiles;
import com.forcitis.one.platform.file.application.ReadFileContent;
import com.forcitis.one.platform.file.domain.BindingAudit;
import com.forcitis.one.platform.file.domain.BindingTarget;
import com.forcitis.one.platform.file.domain.Classification;
import com.forcitis.one.platform.file.domain.NewStoredFile;
import com.forcitis.one.platform.integration.api.BusinessFileStore;
import com.forcitis.one.platform.integration.api.StoredObjectKey;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

/** ADR-0060 sections 2 and 5: nothing is downloadable or readable before it is linked to the document asked for, and the rows pass the schema. */
class FileBindingIT {
    static final TestDatabase DATABASE = TestPostgres.database(FileBindingIT.class, TestPostgres.MIGRATED);
    static final Instant AT = Instant.parse("2026-10-21T02:00:00Z");
    static final byte[] CONTENT = "合同正文".getBytes(StandardCharsets.UTF_8);
    static final DocumentTypeCode CONTRACT = new DocumentTypeCode("CONTRACT");

    final DataSource source = new SimpleDriverDataSource(new org.postgresql.Driver(), DATABASE.url(), DATABASE.username(), DATABASE.password());
    final JdbcClient jdbc = JdbcClient.create(source);
    final DataSourceTransactionManager transactions = new DataSourceTransactionManager(source);
    final Clock clock = Clock.fixed(AT, ZoneOffset.UTC);
    final JdbcStoredFileRepository files = new JdbcStoredFileRepository(jdbc, UUID::randomUUID, new BusinessZone(ZoneId.of("Asia/Shanghai")));
    final JdbcFileBindingRepository bindings = new JdbcFileBindingRepository(jdbc, UUID::randomUUID);
    final FileKeys keys = new FileKeys("dev");

    final List<String> promoted = new ArrayList<>();
    final List<String> signed = new ArrayList<>();
    final List<String> audited = new ArrayList<>();
    RuntimeException promoteFailure;
    byte[] stored = CONTENT;

    final BusinessFileStore store = new BusinessFileStore() {
        @Override
        public void put(StoredObjectKey key, Path source, String sha256Hex, long size) {
            throw new AssertionError("not used when linking");
        }

        @Override
        public void promote(StoredObjectKey staging, StoredObjectKey formal, String sha256Hex, long size) {
            if (promoteFailure != null) {
                throw promoteFailure;
            }
            promoted.add(staging.value() + " -> " + formal.value());
        }

        @Override
        public Duration maxLinkValidity() {
            return Duration.ofMinutes(15);
        }

        @Override
        public URI presignDownload(StoredObjectKey formal, Duration validity, String fileName, String mediaType) {
            signed.add(formal.value() + " " + validity + " " + fileName + " " + mediaType);
            return URI.create("https://bucket.example/" + formal.value() + "?signature=s");
        }

        @Override
        public void read(StoredObjectKey key, OutputStream sink) {
            try {
                sink.write(stored);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    };

    final BindingAudit audit = new BindingAudit() {
        @Override
        public void bound(FileId file, DocumentReference document, Classification classification, FileSubject boundBy) {
            audited.add("bound " + classification.accessLevel());
        }

        @Override
        public void held(FileId file, DocumentReference initiator, FileSubject heldBy) {
            audited.add("held");
        }

        @Override
        public void downloadLinkIssued(FileId file, DocumentReference document, FileSubject requester, Duration validity) {
            audited.add("link " + validity);
        }

        @Override
        public void contentRead(FileId file, DocumentReference document, FileSubject requester) {
            audited.add("read");
        }

        @Override
        public void hashMismatch(FileId file, DocumentReference document, FileSubject requester, ContentHash expected) {
            audited.add("mismatch");
        }
    };

    final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    final BindFile bind = new BindFile(bindings, store, audit, keys, clock, new BusinessZone(ZoneId.of("Asia/Shanghai")), transactions, meters);
    final HoldFile hold = new HoldFile(bindings, audit, clock, transactions);
    final IssueDownloadLink links = new IssueDownloadLink(bindings, store, audit, Duration.ofMinutes(5), Duration.ofMinutes(15), clock);
    final ReadFileContent content = new ReadFileContent(bindings, store, audit, new SimpleMeterRegistry());
    final ListDocumentFiles list = new ListDocumentFiles(bindings);

    final FileSubject uploader = user();
    final PreservationCategoryCode category = category();

    private static FileSubject user() {
        return new FileSubject(FileSubjectKind.USER, UUID.randomUUID().toString());
    }

    private static DocumentReference document() {
        return new DocumentReference(CONTRACT, new DocumentId(UUID.randomUUID()));
    }

    private PreservationCategoryCode category() {
        UUID id = UUID.randomUUID();
        String code = "C" + id.toString().replace("-", "").substring(0, 12).toUpperCase();
        jdbc.sql("insert into platform_file.preservation_categories (preservation_category_id, category_code, category_name, created_at, updated_at)"
                + " values (?, ?, '合同', now(), now())").params(id, code).update();
        jdbc.sql("insert into platform_file.preservation_category_versions (preservation_category_version_id, preservation_category_id, version_number,"
                + " start_basis_text, effective_from_date, decision_ref_key, confirmation_status, authored_at, created_at, updated_at)"
                + " values (?, ?, 1, '合同终止', date '2026-10-21', 'ADR-0061', 'PENDING', now(), now(), now())").params(UUID.randomUUID(), id).update();
        return new PreservationCategoryCode(code);
    }

    private FileId stored(FileStatus status, Lineage lineage) {
        return stored(status, lineage, uploader);
    }

    private FileId stored(FileStatus status, Lineage lineage, FileSubject by) {
        FileId id = new FileId(UUID.randomUUID());
        String key = status == FileStatus.QUARANTINED ? keys.quarantine(id).value() : keys.formal(id, AT).value();
        files.insert(new NewStoredFile(id, key, new ContentHash(sha256(CONTENT)), CONTENT.length, "application/pdf", by, status, false, lineage,
                "ClamAV 1.4.1", "27435", status == FileStatus.QUARANTINED ? "Eicar-Signature" : null, false, "合同.pdf", AT));
        return id;
    }

    private BindRequest request(FileId file, DocumentReference document, AccessLevel level, FileSubject by) {
        return new BindRequest(file, document, level, PersonalInformationMark.NONE, category, document, by);
    }

    private int rows(String table, FileId file) {
        return jdbc.sql("select count(*) from platform_file." + table + " where stored_file_id = ?").param(file.value()).query(Integer.class).single();
    }

    @Test
    void theFirstLinkCopiesToTheFormalKeyAndStartsThePreservationAnIdenticalRepeatDoesNothing() {
        FileId file = stored(FileStatus.AVAILABLE, new Lineage.FirstVersion());
        DocumentReference contract = document();
        bind.bind(request(file, contract, AccessLevel.INTERNAL, uploader));
        bind.bind(request(file, contract, AccessLevel.INTERNAL, uploader));
        assertThat(promoted).containsExactly("dev/staging/user/" + file + " -> dev/files/2026/10/" + file);
        assertThat(rows("file_bindings", file)).isEqualTo(1);
        assertThat(jdbc.sql("select preservation_sequence_number || ' ' || anchor_document_id || ' ' || (start_date is null)"
                + " from platform_file.current_file_preservations where stored_file_id = ?").param(file.value()).query(String.class).single())
                .isEqualTo("1 " + contract.documentId() + " true");
        assertThat(audited).containsExactly("bound INTERNAL");
    }

    @Test
    void anotherClassificationOrPreservationIsAConflict() {
        FileId file = stored(FileStatus.AVAILABLE, new Lineage.FirstVersion());
        DocumentReference contract = document();
        bind.bind(request(file, contract, AccessLevel.INTERNAL, uploader));
        assertThatThrownBy(() -> bind.bind(request(file, contract, AccessLevel.RESTRICTED, uploader)))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(FileError.PLATFORM_FILE_BINDING_CONFLICT));
        DocumentReference other = document();
        assertThatThrownBy(() -> bind.bind(new BindRequest(file, other, AccessLevel.INTERNAL, PersonalInformationMark.NONE, category, other, uploader)))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(FileError.PLATFORM_FILE_BINDING_CONFLICT));
        bind.bind(new BindRequest(file, other, AccessLevel.SENSITIVE, PersonalInformationMark.PI, category, contract, uploader));
        assertThat(rows("file_bindings", file)).isEqualTo(2);
        assertThat(rows("file_preservations", file)).isEqualTo(1);
    }

    @Test
    void onlyTheUploaderOrAPlatformModuleLinksAFileAnyoneElseFindsNoFile() {
        FileId file = stored(FileStatus.AVAILABLE, new Lineage.FirstVersion());
        DocumentReference contract = document();
        FileSubject stranger = user();
        assertThatThrownBy(() -> bind.bind(request(file, contract, AccessLevel.INTERNAL, stranger)))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(FileError.PLATFORM_FILE_NOT_FOUND));
        assertThat(promoted).isEmpty();
        FileSubject signing = new FileSubject(FileSubjectKind.PLATFORM_MODULE, "platform.signing");
        bind.bind(request(file, contract, AccessLevel.RESTRICTED, signing));
        DocumentReference own = document();
        assertThatThrownBy(() -> bind.bind(new BindRequest(file, own, AccessLevel.PUBLIC, PersonalInformationMark.NONE, category, contract, stranger)))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(FileError.PLATFORM_FILE_NOT_FOUND));
        assertThatThrownBy(() -> bind.bind(new BindRequest(file, own, AccessLevel.PUBLIC, PersonalInformationMark.NONE, category(), own, stranger)))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(FileError.PLATFORM_FILE_NOT_FOUND));
        bind.bind(new BindRequest(file, document(), AccessLevel.INTERNAL, PersonalInformationMark.NONE, category, contract, uploader));
        bind.bind(new BindRequest(file, document(), AccessLevel.INTERNAL, PersonalInformationMark.NONE, category, contract, signing));
        assertThat(rows("file_bindings", file)).isEqualTo(3);
    }

    @Test
    void quarantinedSensitiveAndUncategorisedFilesAreNotLinked() {
        FileId quarantined = stored(FileStatus.QUARANTINED, new Lineage.FirstVersion());
        DocumentReference contract = document();
        assertThatThrownBy(() -> bind.bind(request(quarantined, contract, AccessLevel.INTERNAL, uploader)))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(FileError.PLATFORM_FILE_QUARANTINED));
        FileId file = stored(FileStatus.AVAILABLE, new Lineage.FirstVersion());
        assertThatThrownBy(() -> bind.bind(new BindRequest(file, contract, AccessLevel.INTERNAL, PersonalInformationMark.SPI, category, contract, uploader)))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(FileError.PLATFORM_FILE_SPI_NOT_ACCEPTED));
        assertThatThrownBy(() -> bind.bind(new BindRequest(file, contract, AccessLevel.INTERNAL, PersonalInformationMark.NONE,
                new PreservationCategoryCode("NO_SUCH_CATEGORY"), contract, uploader)))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(FileError.PLATFORM_FILE_PRESERVATION_CATEGORY_UNKNOWN));
        assertThat(rows("file_bindings", quarantined) + rows("file_bindings", file)).isZero();
    }

    @Test
    void aNextVersionNeedsItsPreviousVersionOnTheSameDocument() {
        FileId first = stored(FileStatus.AVAILABLE, new Lineage.FirstVersion());
        FileId next = stored(FileStatus.AVAILABLE, new Lineage.NextVersion(first));
        DocumentReference contract = document();
        assertThatThrownBy(() -> bind.bind(request(next, contract, AccessLevel.INTERNAL, uploader)))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(FileError.PLATFORM_FILE_PREVIOUS_VERSION_NOT_BOUND));
        bind.bind(request(first, contract, AccessLevel.INTERNAL, uploader));
        bind.bind(request(next, contract, AccessLevel.INTERNAL, uploader));
        assertThat(list.list(contract)).extracting(StoredFileSummary::id).containsExactlyInAnyOrder(first, next);
        assertThat(list.list(contract)).filteredOn(s -> s.id().equals(next)).singleElement()
                .satisfies(s -> assertThat(s.lineage()).isEqualTo(new Lineage.NextVersion(first)));
    }

    @Test
    void aFailedCopyLeavesTheFileUnlinked() {
        FileId file = stored(FileStatus.AVAILABLE, new Lineage.FirstVersion());
        promoteFailure = new ExternalSystemUnavailableException("copy failed");
        assertThatThrownBy(() -> bind.bind(request(file, document(), AccessLevel.INTERNAL, uploader))).isSameAs(promoteFailure);
        assertThat(rows("file_bindings", file)).isZero();
        assertThat(audited).isEmpty();
    }

    @Test
    void aCopyLeftUnlinkedIsCountedAndARefusalBeforeTheCopyIsNot() {
        FileId file = stored(FileStatus.AVAILABLE, new Lineage.FirstVersion());
        DocumentReference contract = document();
        bind.bind(request(file, contract, AccessLevel.INTERNAL, uploader));
        assertThatThrownBy(() -> bind.bind(request(file, contract, AccessLevel.RESTRICTED, uploader))).isInstanceOf(DomainException.class);
        assertThat(meters.counter("forcitis.platform.file.binding.failures").count()).isZero();
        FileId next = stored(FileStatus.AVAILABLE, new Lineage.FirstVersion());
        DomainException late = FileError.PLATFORM_FILE_BINDING_CONFLICT.refusal("raced");
        BindFile racing = new BindFile(new JdbcFileBindingRepository(jdbc, UUID::randomUUID) {
            int locks;

            @Override
            public Optional<BindingTarget> lockForBinding(FileId id) {
                if (++locks == 2) {
                    throw late;
                }
                return super.lockForBinding(id);
            }
        }, store, audit, keys, clock, new BusinessZone(ZoneId.of("Asia/Shanghai")), transactions, meters);
        assertThatThrownBy(() -> racing.bind(request(next, document(), AccessLevel.INTERNAL, uploader))).isSameAs(late);
        assertThat(meters.counter("forcitis.platform.file.binding.failures").count()).isEqualTo(1);
        assertThat(rows("file_bindings", next)).isZero();
    }

    @Test
    void aFileFromAOneTimeLinkIsLinkedByThatLinkOnly() {
        FileSubject link = new FileSubject(FileSubjectKind.ONE_TIME_LINK, UUID.randomUUID().toString());
        FileId file = stored(FileStatus.AVAILABLE, new Lineage.FirstVersion(), link);
        DocumentReference contract = document();
        assertThatThrownBy(() -> bind.bind(request(file, contract, AccessLevel.INTERNAL, uploader)))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(FileError.PLATFORM_FILE_NOT_FOUND));
        bind.bind(request(file, contract, AccessLevel.INTERNAL, link));
        assertThat(promoted).containsExactly("dev/staging/one_time_link/" + file + " -> dev/files/2026/10/" + file);
    }

    @Test
    void theCategoryVersionInEffectTodayIsUsedBeforeANewerOneNotYetInEffect() {
        UUID category = jdbc.sql("select c.preservation_category_id from platform_file.preservation_categories c where c.category_code = ?")
                .param(this.category.value()).query(UUID.class).single();
        UUID first = jdbc.sql("select preservation_category_version_id from platform_file.preservation_category_versions where preservation_category_id = ?")
                .param(category).query(UUID.class).single();
        jdbc.sql("update platform_file.preservation_category_versions set confirmation_status = 'CONFIRMED', confirmed_by = ?, confirmed_at = now(),"
                + " updated_at = now() where preservation_category_version_id = ?").params(UUID.randomUUID(), first).update();
        UUID later = UUID.randomUUID();
        jdbc.sql("insert into platform_file.preservation_category_versions (preservation_category_version_id, preservation_category_id, version_number,"
                + " start_basis_text, effective_from_date, decision_ref_key, confirmation_status, authored_at, created_at, updated_at)"
                + " values (?, ?, 2, '合同终止', date '2026-12-01', 'ADR-0061', 'PENDING', now(), now(), now())").params(later, category).update();
        jdbc.sql("update platform_file.preservation_category_versions set confirmation_status = 'CONFIRMED', confirmed_by = ?, confirmed_at = now(),"
                + " updated_at = now() where preservation_category_version_id = ?").params(UUID.randomUUID(), later).update();
        assertThat(bindings.currentCategoryVersion(this.category, LocalDate.parse("2026-10-21"))).contains(first);
        assertThat(bindings.currentCategoryVersion(this.category, LocalDate.parse("2026-12-01"))).contains(later);
    }

    @Test
    void aPendingVersionIsUsedWhileNoConfirmedOneIsInEffect() {
        UUID pending = jdbc.sql("select v.preservation_category_version_id from platform_file.preservation_category_versions v"
                + " join platform_file.preservation_categories c on c.preservation_category_id = v.preservation_category_id where c.category_code = ?")
                .param(category.value()).query(UUID.class).single();
        assertThat(bindings.currentCategoryVersion(category, LocalDate.parse("2026-10-21"))).contains(pending);
    }

    @Test
    void aLinkIsSignedOnlyForTheDocumentTheFileIsLinkedTo() {
        FileId file = stored(FileStatus.AVAILABLE, new Lineage.FirstVersion());
        DocumentReference contract = document();
        FileSubject requester = user();
        assertThatThrownBy(() -> links.issue(file, contract, requester, null))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(FileError.PLATFORM_FILE_NOT_BOUND_TO_DOCUMENT));
        assertThatThrownBy(() -> links.issue(new FileId(UUID.randomUUID()), contract, requester, null))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(FileError.PLATFORM_FILE_NOT_BOUND_TO_DOCUMENT));
        bind.bind(request(file, contract, AccessLevel.INTERNAL, uploader));
        assertThatThrownBy(() -> links.issue(file, document(), requester, null))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(FileError.PLATFORM_FILE_NOT_BOUND_TO_DOCUMENT));
        assertThatThrownBy(() -> links.issue(file, contract, requester, Duration.ofMinutes(16)))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(FileError.PLATFORM_FILE_LINK_VALIDITY_EXCEEDED));
        assertThatThrownBy(() -> links.issue(file, contract, requester, Duration.ZERO))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(FileError.PLATFORM_FILE_LINK_VALIDITY_INVALID));
        assertThatThrownBy(() -> links.issue(file, contract, requester, Duration.ofSeconds(-1)))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(FileError.PLATFORM_FILE_LINK_VALIDITY_INVALID));
        DownloadLink link = links.issue(file, contract, requester, null);
        assertThat(link.expiresAt()).isEqualTo(AT.plus(Duration.ofMinutes(5)));
        assertThat(signed).containsExactly("dev/files/2026/10/" + file + " PT5M 合同.pdf application/pdf");
        assertThat(audited).containsExactly("bound INTERNAL", "link PT5M");
        assertThat(link.toString()).doesNotContain("signature");
    }

    @Test
    void contentIsReadOnlyThroughItsDocumentAndAChangedContentFailsTheRead() {
        FileId file = stored(FileStatus.AVAILABLE, new Lineage.FirstVersion());
        DocumentReference contract = document();
        FileSubject signing = new FileSubject(FileSubjectKind.PLATFORM_MODULE, "platform.signing");
        assertThatThrownBy(() -> content.readContent(file, contract, signing, new ByteArrayOutputStream()))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(FileError.PLATFORM_FILE_NOT_BOUND_TO_DOCUMENT));
        bind.bind(request(file, contract, AccessLevel.INTERNAL, uploader));
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        content.readContent(file, contract, signing, sink);
        assertThat(sink.toByteArray()).isEqualTo(CONTENT);
        stored = "改过的正文".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> content.readContent(file, contract, signing, new ByteArrayOutputStream()))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(FileError.PLATFORM_FILE_HASH_MISMATCH));
        assertThat(audited).containsExactly("bound INTERNAL", "read", "read", "mismatch");
    }

    @Test
    void aFailingSinkIsNotAStorageFaultNorAHashMismatch() {
        FileId file = stored(FileStatus.AVAILABLE, new Lineage.FirstVersion());
        DocumentReference contract = document();
        FileSubject signing = new FileSubject(FileSubjectKind.PLATFORM_MODULE, "platform.signing");
        bind.bind(request(file, contract, AccessLevel.INTERNAL, uploader));
        OutputStream broken = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("reader went away");
            }
        };
        assertThatThrownBy(() -> content.readContent(file, contract, signing, broken)).isInstanceOf(UncheckedIOException.class);
        assertThat(audited).containsExactly("bound INTERNAL", "read");
    }

    @Test
    void aHoldNeedsTheFileOnItsDocumentAndARepeatKeepsTheFirstReason() {
        FileId file = stored(FileStatus.AVAILABLE, new Lineage.FirstVersion());
        DocumentReference contract = document();
        assertThatThrownBy(() -> hold.hold(new HoldRequest(file, contract, uploader, new Reason("合同争议"))))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(FileError.PLATFORM_FILE_NOT_BOUND_TO_DOCUMENT));
        bind.bind(request(file, contract, AccessLevel.INTERNAL, uploader));
        hold.hold(new HoldRequest(file, contract, uploader, new Reason("合同争议")));
        hold.hold(new HoldRequest(file, contract, uploader, new Reason("另一个理由")));
        assertThat(jdbc.sql("select hold_reason from platform_file.file_holds where stored_file_id = ?").param(file.value()).query(String.class).list())
                .containsExactly("合同争议");
        assertThat(audited).containsExactly("bound INTERNAL", "held");
        assertThatThrownBy(() -> hold.hold(new HoldRequest(file, document(), uploader, new Reason("理".repeat(501)))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
