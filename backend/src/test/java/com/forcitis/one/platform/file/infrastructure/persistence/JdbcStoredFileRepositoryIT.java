package com.forcitis.one.platform.file.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.SystemActors;
import com.forcitis.one.platform.file.api.ContentHash;
import com.forcitis.one.platform.file.api.DerivationKind;
import com.forcitis.one.platform.file.api.FileId;
import com.forcitis.one.platform.file.api.FileStatus;
import com.forcitis.one.platform.file.api.FileSubject;
import com.forcitis.one.platform.file.api.FileSubjectKind;
import com.forcitis.one.platform.file.api.Lineage;
import com.forcitis.one.platform.file.domain.LineageTarget;
import com.forcitis.one.platform.file.domain.NewStoredFile;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

/** The rows the gate writes pass the schema's own guards: file, name and first transition together. */
class JdbcStoredFileRepositoryIT {
    static final TestDatabase DATABASE = TestPostgres.database(JdbcStoredFileRepositoryIT.class, TestPostgres.MIGRATED);
    static final Instant AT = Instant.parse("2026-10-21T02:00:00Z");
    static final UUID USER = UUID.fromString("01900000-0000-7000-8000-00000000000a");

    final JdbcClient jdbc = JdbcClient.create(new SimpleDriverDataSource(new org.postgresql.Driver(), DATABASE.url(), DATABASE.username(), DATABASE.password()));
    final JdbcStoredFileRepository files = new JdbcStoredFileRepository(jdbc, UUID::randomUUID, new BusinessZone(ZoneId.of("Asia/Shanghai")));

    private NewStoredFile file(FileSubject uploader, FileStatus status, Lineage lineage, String hash) {
        FileId id = new FileId(UUID.randomUUID());
        String key = status == FileStatus.QUARANTINED ? "dev/quarantine/" + id : "dev/files/2026/10/" + id;
        return new NewStoredFile(id, key, new ContentHash(hash), 7, "application/pdf", uploader, status, false, lineage, "ClamAV 1.4.1", "27435",
                status == FileStatus.QUARANTINED ? "Eicar-Signature" : null, false, "合同.pdf", AT);
    }

    @Test
    void aStoredFileIsWrittenWithItsNameAndFirstTransition() {
        NewStoredFile file = file(new FileSubject(FileSubjectKind.USER, USER.toString()), FileStatus.AVAILABLE, new Lineage.FirstVersion(), "a".repeat(64));
        files.insert(file);
        assertThat(jdbc.sql("select original_name from platform_file.stored_file_names where stored_file_id = ?").param(file.id().value())
                .query(String.class).single()).isEqualTo("合同.pdf");
        assertThat(jdbc.sql("select to_status || ' ' || actor_type || ' ' || actor_id from platform_file.state_transitions where aggregate_id = ?")
                .param(file.id().value()).query(String.class).single()).isEqualTo("AVAILABLE USER " + USER);
        assertThat(files.lineageTarget(file.id())).contains(new LineageTarget(FileStatus.AVAILABLE, false));
    }

    @Test
    void linksAndModulesActAsSystemActorsAndLineageIsRecorded() {
        NewStoredFile source = file(new FileSubject(FileSubjectKind.ONE_TIME_LINK, UUID.randomUUID().toString()), FileStatus.AVAILABLE,
                new Lineage.FirstVersion(), "b".repeat(64));
        files.insert(source);
        NewStoredFile signed = file(new FileSubject(FileSubjectKind.PLATFORM_MODULE, "platform.signing"), FileStatus.AVAILABLE,
                new Lineage.DerivedFrom(source.id(), DerivationKind.SIGNING), "c".repeat(64));
        files.insert(signed);
        NewStoredFile next = file(new FileSubject(FileSubjectKind.USER, USER.toString()), FileStatus.AVAILABLE, new Lineage.NextVersion(source.id()), "d".repeat(64));
        files.insert(next);
        assertThat(jdbc.sql("select actor_id from platform_file.state_transitions where aggregate_id = ?").param(source.id().value()).query(UUID.class).single())
                .isEqualTo(SystemActors.idOf(JdbcStoredFileRepository.ONE_TIME_LINK_HOLDER));
        assertThat(jdbc.sql("select actor_id from platform_file.state_transitions where aggregate_id = ?").param(signed.id().value()).query(UUID.class).single())
                .isEqualTo(SystemActors.idOf(JdbcStoredFileRepository.PLATFORM_MODULE_CALL));
        assertThat(files.lineageTarget(source.id())).contains(new LineageTarget(FileStatus.AVAILABLE, true));
    }

    @Test
    void anExceptionCountsUntilItIsRevoked() {
        String hash = "e".repeat(64);
        NewStoredFile quarantined = file(new FileSubject(FileSubjectKind.USER, USER.toString()), FileStatus.QUARANTINED, new Lineage.FirstVersion(), hash);
        files.insert(quarantined);
        assertThat(files.hasScanException(new ContentHash(hash))).isFalse();
        UUID judgment = UUID.randomUUID();
        jdbc.sql("insert into platform_file.false_positive_judgments (false_positive_judgment_id, stored_file_id, content_hash, judgment_status, judgment_reason,"
                + " judged_by, judged_at, created_at, updated_at) values (?, ?, ?, 'PENDING_REVIEW', '误报', ?, now(), now(), now())")
                .params(judgment, quarantined.id().value(), hash, USER).update();
        jdbc.sql("update platform_file.false_positive_judgments set judgment_status = 'CONFIRMED', review_reason = '同意', reviewed_by = ?, reviewed_at = now()"
                + " where false_positive_judgment_id = ?").params(UUID.randomUUID(), judgment).update();
        jdbc.sql("insert into platform_file.scan_exceptions (scan_exception_id, content_hash, false_positive_judgment_id, judgment_status, added_at, created_at,"
                + " updated_at) values (?, ?, ?, 'CONFIRMED', now(), now(), now())").params(UUID.randomUUID(), hash, judgment).update();
        assertThat(files.hasScanException(new ContentHash(hash))).isTrue();
        jdbc.sql("insert into platform_file.scan_exception_revocations (scan_exception_revocation_id, content_hash, revocation_reason, revoked_by, confirmed_by,"
                + " revoked_at, created_at, updated_at) values (?, ?, '误判', ?, ?, now(), now(), now())")
                .params(UUID.randomUUID(), hash, USER, UUID.randomUUID()).update();
        assertThat(files.hasScanException(new ContentHash(hash))).isFalse();
    }

    @Test
    void anUploadStatusIsSeenOnlyByItsUploaderAndOnlyUntilADocumentLinksIt() {
        UUID stranger = UUID.randomUUID();
        NewStoredFile own = file(new FileSubject(FileSubjectKind.USER, USER.toString()), FileStatus.QUARANTINED, new Lineage.FirstVersion(), "f".repeat(64));
        NewStoredFile byLink = file(new FileSubject(FileSubjectKind.ONE_TIME_LINK, USER.toString()), FileStatus.AVAILABLE, new Lineage.FirstVersion(), "9".repeat(64));
        files.insert(own);
        files.insert(byLink);

        assertThat(files.unboundStatusStoredBy(own.id(), USER)).contains(FileStatus.QUARANTINED);
        assertThat(files.unboundStatusStoredBy(own.id(), stranger)).as("someone else's file does not exist for the caller").isEmpty();
        assertThat(files.unboundStatusStoredBy(byLink.id(), USER)).as("a link's upload is not a user's").isEmpty();
        assertThat(files.unboundStatusStoredBy(new FileId(UUID.randomUUID()), USER)).isEmpty();

        jdbc.sql("insert into platform_file.file_bindings (file_binding_id, stored_file_id, document_type, document_id, access_level_code, personal_information_mark_code,"
                + " bound_by_kind, bound_by_user_id, bound_at, created_at, updated_at) values (?, ?, 'CONTRACT', ?, 'INTERNAL', 'NONE', 'USER', ?, now(), now(), now())")
                .params(UUID.randomUUID(), own.id().value(), UUID.randomUUID(), USER).update();
        assertThat(files.unboundStatusStoredBy(own.id(), USER)).as("once linked, the status is no longer the uploader's to read").isEmpty();
    }
}
