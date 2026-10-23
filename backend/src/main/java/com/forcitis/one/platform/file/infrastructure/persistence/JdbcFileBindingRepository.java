package com.forcitis.one.platform.file.infrastructure.persistence;

import com.forcitis.one.kernel.DocumentId;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.DocumentTypeCode;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.Reason;
import com.forcitis.one.platform.file.api.AccessLevel;
import com.forcitis.one.platform.file.api.ContentHash;
import com.forcitis.one.platform.file.api.DerivationKind;
import com.forcitis.one.platform.file.api.FileId;
import com.forcitis.one.platform.file.api.FileStatus;
import com.forcitis.one.platform.file.api.FileSubject;
import com.forcitis.one.platform.file.api.FileSubjectKind;
import com.forcitis.one.platform.file.api.Lineage;
import com.forcitis.one.platform.file.api.PersonalInformationMark;
import com.forcitis.one.platform.file.api.PreservationCategoryCode;
import com.forcitis.one.platform.file.api.StoredFileSummary;
import com.forcitis.one.platform.file.domain.BindingTarget;
import com.forcitis.one.platform.file.domain.BoundContent;
import com.forcitis.one.platform.file.domain.Classification;
import com.forcitis.one.platform.file.domain.FileBindingRepository;
import com.forcitis.one.platform.file.domain.FilePreservation;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcFileBindingRepository implements FileBindingRepository {
    private final JdbcClient jdbc;
    private final IdGenerator ids;

    JdbcFileBindingRepository(JdbcClient jdbc, IdGenerator ids) {
        this.jdbc = jdbc;
        this.ids = ids;
    }

    @Override
    public Optional<BindingTarget> lockForBinding(FileId file) {
        // Lock first, ask about bindings in a second statement: a binding committed while this call waited for the lock is invisible to the
        // first statement's snapshot (see JdbcStoredFileRepository.lockForExpiry).
        Optional<BindingTarget> locked = jdbc.sql("""
                        select f.stored_file_id, f.file_status, f.uploader_kind, f.uploader_user_id, f.uploader_link_id, f.uploader_module_code,
                               f.previous_file_id, f.object_key, f.content_hash, f.byte_count
                          from platform_file.stored_files f
                         where f.stored_file_id = :id
                           for no key update
                        """)
                .param("id", file.value())
                .query((rs, n) -> new BindingTarget(new FileId(rs.getObject("stored_file_id", UUID.class)), FileStatus.valueOf(rs.getString("file_status")),
                        subject(rs, "uploader"), Optional.ofNullable(rs.getObject("previous_file_id", UUID.class)).map(FileId::new),
                        rs.getString("object_key"), new ContentHash(rs.getString("content_hash")), rs.getLong("byte_count"), false))
                .optional();
        if (locked.isEmpty()) {
            return locked;
        }
        boolean bound = jdbc.sql("select exists (select 1 from platform_file.file_bindings where stored_file_id = :id)")
                .param("id", file.value()).query(Boolean.class).single();
        BindingTarget t = locked.get();
        return Optional.of(new BindingTarget(t.id(), t.status(), t.uploader(), t.previous(), t.objectKey(), t.contentHash(), t.size(), bound));
    }

    @Override
    public Optional<Classification> classification(FileId file, DocumentReference document) {
        return jdbc.sql("""
                        select access_level_code, personal_information_mark_code
                          from platform_file.file_bindings
                         where stored_file_id = :id and document_type = :type and document_id = :document
                        """)
                .param("id", file.value()).param("type", document.documentType().value()).param("document", document.documentId().value())
                .query((rs, n) -> new Classification(AccessLevel.valueOf(rs.getString("access_level_code")),
                        PersonalInformationMark.valueOf(rs.getString("personal_information_mark_code"))))
                .optional();
    }

    /** The insert trigger keeps category and anchor on every later row, so the first row answers without reading the current one. */
    @Override
    public Optional<FilePreservation> preservation(FileId file) {
        return jdbc.sql("""
                        select c.category_code, p.anchor_document_type, p.anchor_document_id
                          from platform_file.file_preservations p
                          join platform_file.preservation_category_versions v on v.preservation_category_version_id = p.preservation_category_version_id
                          join platform_file.preservation_categories c on c.preservation_category_id = v.preservation_category_id
                         where p.stored_file_id = :id and p.preservation_sequence_number = 1
                        """)
                .param("id", file.value())
                .query((rs, n) -> new FilePreservation(new PreservationCategoryCode(rs.getString("category_code")),
                        document(rs, "anchor_document_type", "anchor_document_id")))
                .optional();
    }

    @Override
    public Optional<UUID> currentCategoryVersion(PreservationCategoryCode category, LocalDate day) {
        return jdbc.sql("""
                        select v.preservation_category_version_id
                          from platform_file.preservation_category_versions v
                          join platform_file.preservation_categories c on c.preservation_category_id = v.preservation_category_id
                         where c.category_code = :code
                         order by (v.confirmation_status = 'CONFIRMED' and v.effective_from_date <= :day) desc, v.version_number desc
                         limit 1
                        """)
                .param("code", category.value()).param("day", day)
                .query(UUID.class).optional();
    }

    @Override
    public void insertBinding(FileId file, DocumentReference document, Classification classification, FileSubject boundBy, Instant at) {
        jdbc.sql("""
                        insert into platform_file.file_bindings (file_binding_id, stored_file_id, document_type, document_id, access_level_code,
                            personal_information_mark_code, bound_by_kind, bound_by_user_id, bound_by_link_id, bound_by_module_code, bound_at,
                            created_at, updated_at)
                        values (:id, :file, :type, :document, :access, :mark, :kind, :userId, :linkId, :moduleCode, :at, :at, :at)
                        """)
                .param("id", ids.next()).param("file", file.value()).param("type", document.documentType().value())
                .param("document", document.documentId().value()).param("access", classification.accessLevel().name())
                .param("mark", classification.personalInformation().name()).param("kind", boundBy.kind().name())
                .param("userId", idOf(boundBy, FileSubjectKind.USER)).param("linkId", idOf(boundBy, FileSubjectKind.ONE_TIME_LINK))
                .param("moduleCode", boundBy.kind() == FileSubjectKind.PLATFORM_MODULE ? boundBy.id() : null).param("at", Timestamp.from(at))
                .update();
    }

    @Override
    public void insertFirstPreservation(FileId file, UUID categoryVersion, DocumentReference anchor, Instant at) {
        jdbc.sql("""
                        insert into platform_file.file_preservations (file_preservation_id, stored_file_id, preservation_sequence_number,
                            preservation_category_version_id, anchor_document_type, anchor_document_id, recorded_at, created_at, updated_at)
                        values (:id, :file, 1, :version, :type, :document, :at, :at, :at)
                        """)
                .param("id", ids.next()).param("file", file.value()).param("version", categoryVersion)
                .param("type", anchor.documentType().value()).param("document", anchor.documentId().value()).param("at", Timestamp.from(at))
                .update();
    }

    @Override
    public Optional<Reason> holdReason(FileId file, DocumentReference initiator) {
        return jdbc.sql("""
                        select hold_reason from platform_file.file_holds
                         where stored_file_id = :id and initiator_document_type = :type and initiator_document_id = :document
                        """)
                .param("id", file.value()).param("type", initiator.documentType().value()).param("document", initiator.documentId().value())
                .query((rs, n) -> new Reason(rs.getString("hold_reason")))
                .optional();
    }

    @Override
    public void insertHold(FileId file, DocumentReference initiator, FileSubject heldBy, Reason reason, Instant at) {
        jdbc.sql("""
                        insert into platform_file.file_holds (file_hold_id, stored_file_id, initiator_document_type, initiator_document_id, held_by_kind,
                            held_by_user_id, held_by_link_id, held_by_module_code, hold_reason, held_at, created_at, updated_at)
                        values (:id, :file, :type, :document, :kind, :userId, :linkId, :moduleCode, :reason, :at, :at, :at)
                        """)
                .param("id", ids.next()).param("file", file.value()).param("type", initiator.documentType().value())
                .param("document", initiator.documentId().value()).param("kind", heldBy.kind().name())
                .param("userId", idOf(heldBy, FileSubjectKind.USER)).param("linkId", idOf(heldBy, FileSubjectKind.ONE_TIME_LINK))
                .param("moduleCode", heldBy.kind() == FileSubjectKind.PLATFORM_MODULE ? heldBy.id() : null).param("reason", reason.value())
                .param("at", Timestamp.from(at))
                .update();
    }

    @Override
    public Optional<BoundContent> boundContent(FileId file, DocumentReference document) {
        return jdbc.sql("""
                        select f.file_status, f.object_key, f.content_hash, f.byte_count, f.media_type_code, n.original_name
                          from platform_file.file_bindings b
                          join platform_file.stored_files f on f.stored_file_id = b.stored_file_id
                          join platform_file.stored_file_names n on n.stored_file_id = f.stored_file_id
                         where b.stored_file_id = :id and b.document_type = :type and b.document_id = :document
                        """)
                .param("id", file.value()).param("type", document.documentType().value()).param("document", document.documentId().value())
                .query((rs, n) -> new BoundContent(FileStatus.valueOf(rs.getString("file_status")), rs.getString("object_key"),
                        new ContentHash(rs.getString("content_hash")), rs.getLong("byte_count"), rs.getString("media_type_code"), rs.getString("original_name")))
                .optional();
    }

    @Override
    public List<StoredFileSummary> listByDocument(DocumentReference document) {
        return jdbc.sql("""
                        select f.stored_file_id, f.content_hash, f.byte_count, f.media_type_code, n.original_name, f.file_status, f.has_macros,
                               f.uploader_kind, f.uploader_user_id, f.uploader_link_id, f.uploader_module_code, f.stored_at,
                               f.previous_file_id, f.derived_from_file_id, f.derivation_kind
                          from platform_file.file_bindings b
                          join platform_file.stored_files f on f.stored_file_id = b.stored_file_id
                          join platform_file.stored_file_names n on n.stored_file_id = f.stored_file_id
                         where b.document_type = :type and b.document_id = :document
                         order by b.bound_at, f.stored_file_id
                        """)
                .param("type", document.documentType().value()).param("document", document.documentId().value())
                .query((rs, n) -> new StoredFileSummary(new FileId(rs.getObject("stored_file_id", UUID.class)), new ContentHash(rs.getString("content_hash")),
                        rs.getLong("byte_count"), rs.getString("media_type_code"), rs.getString("original_name"), FileStatus.valueOf(rs.getString("file_status")),
                        rs.getBoolean("has_macros"), subject(rs, "uploader"), rs.getTimestamp("stored_at").toInstant(), lineage(rs)))
                .list();
    }

    private static Lineage lineage(ResultSet rs) throws SQLException {
        UUID previous = rs.getObject("previous_file_id", UUID.class);
        if (previous != null) {
            return new Lineage.NextVersion(new FileId(previous));
        }
        UUID source = rs.getObject("derived_from_file_id", UUID.class);
        if (source != null) {
            return new Lineage.DerivedFrom(new FileId(source), DerivationKind.valueOf(rs.getString("derivation_kind")));
        }
        return new Lineage.FirstVersion();
    }

    private static FileSubject subject(ResultSet rs, String prefix) throws SQLException {
        FileSubjectKind kind = FileSubjectKind.valueOf(rs.getString(prefix + "_kind"));
        String id = switch (kind) {
            case USER -> rs.getObject(prefix + "_user_id", UUID.class).toString();
            case ONE_TIME_LINK -> rs.getObject(prefix + "_link_id", UUID.class).toString();
            case PLATFORM_MODULE -> rs.getString(prefix + "_module_code");
        };
        return new FileSubject(kind, id);
    }

    private static DocumentReference document(ResultSet rs, String typeColumn, String idColumn) throws SQLException {
        return new DocumentReference(new DocumentTypeCode(rs.getString(typeColumn)), new DocumentId(rs.getObject(idColumn, UUID.class)));
    }

    private static UUID idOf(FileSubject subject, FileSubjectKind kind) {
        return subject.kind() == kind ? UUID.fromString(subject.id()) : null;
    }
}
