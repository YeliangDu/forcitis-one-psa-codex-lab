package com.forcitis.one.platform.audit.infrastructure.persistence;

import com.forcitis.one.kernel.IdGenerator;
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
    private final IdGenerator ids;

    JdbcAuditEntryRepository(JdbcClient jdbc, IdGenerator ids) {
        this.jdbc = jdbc;
        this.ids = ids;
    }

    @Override
    public void save(AuditEntry entry, Instant createdAt) {
        jdbc.sql("""
                        insert into platform_audit.audit_entries (id, created_at, updated_at, action, actor_id, on_behalf_of_id, subject_key, detail,
                                                                  source_address, outcome_kind, login_method_kind,
                                                                  change_reason, before_text, after_text)
                        values (:id, :createdAt, :createdAt, :action, :actor, :onBehalfOf, :subject, :detail, :sourceAddress, :outcome, :loginMethod,
                                :reason, :before, :after)
                        """)
                .param("id", ids.next())
                .param("createdAt", Timestamp.from(createdAt))
                .param("action", entry.action())
                .param("actor", entry.actorId() == null ? null : entry.actorId().value(), Types.BIGINT)
                .param("onBehalfOf", entry.onBehalfOfId() == null ? null : entry.onBehalfOfId().value(), Types.BIGINT)
                .param("subject", entry.subjectKey())
                .param("detail", entry.detail(), Types.VARCHAR)
                .param("sourceAddress", entry.sourceAddress(), Types.VARCHAR)
                .param("outcome", entry.outcome() == null ? null : entry.outcome().name(), Types.VARCHAR)
                .param("loginMethod", entry.loginMethod(), Types.VARCHAR)
                .param("reason", entry.changeReason(), Types.VARCHAR)
                .param("before", entry.beforeText(), Types.VARCHAR)
                .param("after", entry.afterText(), Types.VARCHAR)
                .update();
    }

    @Override
    public boolean saveForEvent(AuditEntry entry, Instant createdAt, UUID eventId, UUID correlationId) {
        return jdbc.sql("""
                        insert into platform_audit.audit_entries (id, created_at, updated_at, action, actor_id, on_behalf_of_id, subject_key, detail,
                                                                  event_id, correlation_id, change_reason, before_text, after_text)
                        values (:id, :createdAt, :createdAt, :action, :actor, :onBehalfOf, :subject, :detail, :eventId, :correlationId,
                                :reason, :before, :after)
                        on conflict (event_id) do nothing
                        """)
                .param("id", ids.next())
                .param("createdAt", Timestamp.from(createdAt))
                .param("action", entry.action())
                .param("actor", entry.actorId() == null ? null : entry.actorId().value(), Types.BIGINT)
                .param("onBehalfOf", entry.onBehalfOfId() == null ? null : entry.onBehalfOfId().value(), Types.BIGINT)
                .param("subject", entry.subjectKey())
                .param("detail", entry.detail(), Types.VARCHAR)
                .param("eventId", eventId, Types.OTHER)
                .param("correlationId", correlationId, Types.OTHER)
                .param("reason", entry.changeReason(), Types.VARCHAR)
                .param("before", entry.beforeText(), Types.VARCHAR)
                .param("after", entry.afterText(), Types.VARCHAR)
                .update() > 0;
    }
}
