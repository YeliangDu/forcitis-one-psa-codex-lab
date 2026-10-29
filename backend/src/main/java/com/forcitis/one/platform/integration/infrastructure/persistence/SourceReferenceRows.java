package com.forcitis.one.platform.integration.infrastructure.persistence;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.integration.api.ExternalSource;
import com.forcitis.one.platform.integration.api.OwnObjectId;
import com.forcitis.one.platform.integration.api.SourceObjectId;
import com.forcitis.one.platform.integration.api.SourceObjectKind;
import com.forcitis.one.platform.integration.domain.SourceReference;
import com.forcitis.one.platform.integration.domain.SourceReferenceId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;
import java.time.OffsetDateTime;

final class SourceReferenceRows {

    static final String SELECT = """
            select source_reference_id, created_at, source_system, source_object_kind, source_id, own_id, confirmation_status,
                   effective_from_date, effective_to_date, created_by, decided_by, decided_at, version, event_count
              from platform_integration.source_references
            """;

    private SourceReferenceRows() {
    }

    static SourceReference map(ResultSet rs) throws SQLException {
        UUID registeredBy = rs.getObject("created_by", UUID.class);
        UUID decidedBy = rs.getObject("decided_by", UUID.class);
        OffsetDateTime decidedAt = rs.getObject("decided_at", OffsetDateTime.class);
        return SourceReference.restore(new SourceReferenceId(rs.getObject("source_reference_id", UUID.class)),
                ExternalSource.valueOf(rs.getString("source_system")), SourceObjectKind.valueOf(rs.getString("source_object_kind")),
                new SourceObjectId(rs.getLong("source_id")), new OwnObjectId(rs.getObject("own_id", UUID.class)), rs.getObject("effective_from_date", LocalDate.class),
                rs.getObject("effective_to_date", LocalDate.class), registeredBy == null ? null : new UserId(registeredBy),
                rs.getString("confirmation_status"), decidedBy == null ? null : new UserId(decidedBy), decidedAt == null ? null : decidedAt.toInstant(),
                rs.getLong("version"), rs.getLong("event_count"));
    }
}
