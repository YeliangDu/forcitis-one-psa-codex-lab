package com.forcitis.one.platform.file.infrastructure.persistence;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.SystemActors;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.platform.file.api.ContentHash;
import com.forcitis.one.platform.file.api.FileId;
import com.forcitis.one.platform.file.api.FileStatus;
import com.forcitis.one.platform.file.api.FileSubject;
import com.forcitis.one.platform.file.api.FileSubjectKind;
import com.forcitis.one.platform.file.api.Lineage;
import com.forcitis.one.platform.file.domain.LineageTarget;
import com.forcitis.one.platform.file.domain.NewStoredFile;
import com.forcitis.one.platform.file.domain.StoredFile;
import com.forcitis.one.platform.file.domain.StoredFileRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcStoredFileRepository implements StoredFileRepository {
    static final String ONE_TIME_LINK_HOLDER = "platform.file.one-time-link-holder";
    static final String PLATFORM_MODULE_CALL = "platform.file.platform-module-call";
    static final String STAGING_EXPIRE = "platform.file.staging.expire";

    private final JdbcClient jdbc;
    private final IdGenerator ids;
    private final BusinessZone zone;

    JdbcStoredFileRepository(JdbcClient jdbc, IdGenerator ids, BusinessZone zone) {
        this.jdbc = jdbc;
        this.ids = ids;
        this.zone = zone;
    }

    @Override
    public void insert(NewStoredFile file) {
        FileSubject uploader = file.uploader();
        Timestamp at = Timestamp.from(file.storedAt());
        jdbc.sql("""
                        insert into platform_file.stored_files (stored_file_id, object_key, content_hash, byte_count, media_type_code, uploader_kind,
                            uploader_user_id, uploader_link_id, uploader_module_code, file_status, has_macros, previous_file_id, derived_from_file_id,
                            derivation_kind, scan_engine_version_code, scan_definitions_version_code, threat_name, is_released_by_exception,
                            stored_at, created_at, updated_at)
                        values (:id, :key, :hash, :size, :media, :uploaderKind, :userId, :linkId, :moduleCode, :status, :macros, :previous, :derived,
                            :derivation, :engine, :definitions, :threat, :released, :at, :at, :at)
                        """)
                .param("id", file.id().value()).param("key", file.objectKey()).param("hash", file.contentHash().value())
                .param("size", file.size()).param("media", file.mediaType()).param("uploaderKind", uploader.kind().name())
                .param("userId", uploader.kind() == FileSubjectKind.USER ? UUID.fromString(uploader.id()) : null)
                .param("linkId", uploader.kind() == FileSubjectKind.ONE_TIME_LINK ? UUID.fromString(uploader.id()) : null)
                .param("moduleCode", uploader.kind() == FileSubjectKind.PLATFORM_MODULE ? uploader.id() : null)
                .param("status", file.status().name()).param("macros", file.containsMacros())
                .param("previous", file.lineage() instanceof Lineage.NextVersion next ? next.previous().value() : null)
                .param("derived", file.lineage() instanceof Lineage.DerivedFrom derived ? derived.source().value() : null)
                .param("derivation", file.lineage() instanceof Lineage.DerivedFrom derived ? derived.derivation().name() : null)
                .param("engine", file.engineVersion()).param("definitions", file.definitionsVersion()).param("threat", file.threatName())
                .param("released", file.releasedByException()).param("at", at)
                .update();
        jdbc.sql("""
                        insert into platform_file.stored_file_names (stored_file_id, original_name, is_deidentified, created_at, updated_at)
                        values (:id, :name, false, :at, :at)
                        """)
                .param("id", file.id().value()).param("name", file.originalName()).param("at", at)
                .update();
        UUID command = ids.next();
        UUID correlation = WorkContext.correlationId().orElse(command);
        jdbc.sql("""
                        insert into platform_file.state_transitions (id, aggregate_type_code, aggregate_id, transition_sequence_number, from_status, to_status,
                            occurred_on_date, recorded_at, actor_type, actor_id, cause_kind, command_id, correlation_id, created_at, updated_at)
                        values (:id, 'StoredFile', :file, 0, null, :status, :day, :at, :actorType, :actorId, 'COMMAND', :command, :correlation, :at, :at)
                        """)
                .param("id", ids.next()).param("file", file.id().value()).param("status", file.status().name())
                .param("day", zone.dateOf(file.storedAt())).param("at", at)
                .param("actorType", uploader.kind() == FileSubjectKind.USER ? "USER" : "SYSTEM")
                .param("actorId", actorId(uploader)).param("command", command).param("correlation", correlation)
                .update();
    }

    @Override
    public Optional<LineageTarget> lineageTarget(FileId file) {
        return jdbc.sql("""
                        select f.file_status,
                               exists (select 1 from platform_file.stored_files n where n.previous_file_id = f.stored_file_id) as has_successor
                          from platform_file.stored_files f
                         where f.stored_file_id = :id
                        """)
                .param("id", file.value())
                .query((rs, n) -> new LineageTarget(FileStatus.valueOf(rs.getString("file_status")), rs.getBoolean("has_successor")))
                .optional();
    }

    @Override
    public boolean hasScanException(ContentHash contentHash) {
        return jdbc.sql("""
                        select exists (select 1 from platform_file.scan_exceptions e
                                        where e.content_hash = :hash
                                          and not exists (select 1 from platform_file.scan_exception_revocations r where r.content_hash = e.content_hash))
                        """)
                .param("hash", contentHash.value())
                .query(Boolean.class).single();
    }

    @Override
    public Optional<FileStatus> unboundStatusStoredBy(FileId file, UUID uploader) {
        return jdbc.sql("""
                        select f.file_status
                          from platform_file.stored_files f
                         where f.stored_file_id = :id
                           and f.uploader_kind = 'USER' and f.uploader_user_id = :user
                           and not exists (select 1 from platform_file.file_bindings b where b.stored_file_id = f.stored_file_id)
                        """)
                .param("id", file.value()).param("user", uploader)
                .query((rs, n) -> FileStatus.valueOf(rs.getString("file_status")))
                .optional();
    }

    @Override
    public List<FileId> unboundStoredBefore(FileSubjectKind uploaderKind, Instant cutoff, int limit) {
        return jdbc.sql("""
                        select f.stored_file_id
                          from platform_file.stored_files f
                         where f.file_status = 'AVAILABLE' and f.uploader_kind = :kind and f.stored_at < :cutoff
                           and not exists (select 1 from platform_file.file_bindings b where b.stored_file_id = f.stored_file_id)
                         order by f.stored_at, f.stored_file_id
                         limit :limit
                        """)
                .param("kind", uploaderKind.name()).param("cutoff", Timestamp.from(cutoff)).param("limit", limit)
                .query((rs, n) -> new FileId(rs.getObject("stored_file_id", UUID.class))).list();
    }

    @Override
    public Optional<StoredFile> lockForLifecycle(FileId file) {
        return jdbc.sql("""
                        select f.uploader_kind, f.stored_at, f.file_status, f.version,
                               exists (select 1 from platform_file.file_bindings b where b.stored_file_id = f.stored_file_id) as bound
                          from platform_file.stored_files f
                         where f.stored_file_id = :id
                           for no key update
                        """)
                .param("id", file.value())
                .query((rs, n) -> StoredFile.restore(file, FileSubjectKind.valueOf(rs.getString("uploader_kind")), rs.getTimestamp("stored_at").toInstant(),
                        rs.getBoolean("bound"), FileStatus.valueOf(rs.getString("file_status")), rs.getLong("version")))
                .optional();
    }

    @Override
    public void saveExpiry(StoredFile file, Instant at) {
        Timestamp now = Timestamp.from(at);
        int changed = jdbc.sql("""
                        update platform_file.stored_files
                           set file_status = :status, version = :next, updated_at = :at
                         where stored_file_id = :id and version = :version
                        """)
                .param("status", file.status().name()).param("next", file.versionAfterSave()).param("at", now)
                .param("id", file.id().value()).param("version", file.version())
                .update();
        if (changed != 1) {
            throw new IllegalStateException("file " + file.id() + " changed while it was locked");
        }
        jdbc.sql("""
                        insert into platform_file.state_transitions (id, aggregate_type_code, aggregate_id, transition_sequence_number, from_status, to_status,
                            occurred_on_date, recorded_at, actor_type, actor_id, cause_kind, correlation_id, created_at, updated_at)
                        values (:id, 'StoredFile', :file, :sequence, :from, :to, :day, :at, 'SYSTEM', :actor, 'SYSTEM_JOB', :correlation, :at, :at)
                        """)
                .param("id", ids.next()).param("file", file.id().value()).param("sequence", file.versionAfterSave())
                .param("from", file.loadedStatus().name()).param("to", file.status().name()).param("day", zone.dateOf(at)).param("at", now)
                .param("actor", SystemActors.idOf(STAGING_EXPIRE)).param("correlation", WorkContext.correlationId().orElseGet(ids::next))
                .update();
    }

    private static UUID actorId(FileSubject subject) {
        return switch (subject.kind()) {
            case USER -> UUID.fromString(subject.id());
            case ONE_TIME_LINK -> SystemActors.idOf(ONE_TIME_LINK_HOLDER);
            case PLATFORM_MODULE -> SystemActors.idOf(PLATFORM_MODULE_CALL);
        };
    }
}
