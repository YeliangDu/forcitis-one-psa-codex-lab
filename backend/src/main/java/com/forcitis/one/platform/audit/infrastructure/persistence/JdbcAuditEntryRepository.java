package com.forcitis.one.platform.audit.infrastructure.persistence;

import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.domain.AuditEntryRepository;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAuditEntryRepository implements AuditEntryRepository {

    private final JdbcClient jdbc;

    JdbcAuditEntryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(AuditEntry entry, Instant createdAt) {
        jdbc.sql("""
                        insert into platform_audit.audit_entries (created_at, updated_at, action, actor_id, on_behalf_of_id, subject_key, detail)
                        values (:createdAt, :createdAt, :action, :actor, :onBehalfOf, :subject, :detail)
                        """)
                .param("createdAt", Timestamp.from(createdAt))
                .param("action", entry.action())
                .param("actor", entry.actorId() == null ? null : entry.actorId().value(), Types.BIGINT)
                .param("onBehalfOf", entry.onBehalfOfId() == null ? null : entry.onBehalfOfId().value(), Types.BIGINT)
                .param("subject", entry.subjectKey())
                .param("detail", entry.detail(), Types.VARCHAR)
                .update();
    }

    @Override
    public boolean saveForEvent(AuditEntry entry, Instant createdAt, UUID eventId, UUID correlationId) {
        return jdbc.sql("""
                        insert into platform_audit.audit_entries (created_at, updated_at, action, actor_id, on_behalf_of_id, subject_key, detail,
                                                                  event_id, correlation_id)
                        values (:createdAt, :createdAt, :action, :actor, :onBehalfOf, :subject, :detail, :eventId, :correlationId)
                        on conflict (event_id) do nothing
                        """)
                .param("createdAt", Timestamp.from(createdAt))
                .param("action", entry.action())
                .param("actor", entry.actorId() == null ? null : entry.actorId().value(), Types.BIGINT)
                .param("onBehalfOf", entry.onBehalfOfId() == null ? null : entry.onBehalfOfId().value(), Types.BIGINT)
                .param("subject", entry.subjectKey())
                .param("detail", entry.detail(), Types.VARCHAR)
                .param("eventId", eventId, Types.OTHER)
                .param("correlationId", correlationId, Types.OTHER)
                .update() > 0;
    }
}
