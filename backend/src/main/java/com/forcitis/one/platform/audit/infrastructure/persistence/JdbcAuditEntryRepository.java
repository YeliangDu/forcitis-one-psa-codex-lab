package com.forcitis.one.platform.audit.infrastructure.persistence;

import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.domain.AuditEntryRepository;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAuditEntryRepository implements AuditEntryRepository {
    private static final Logger LOG = LoggerFactory.getLogger(JdbcAuditEntryRepository.class);
    private static final int MAX_ADDRESS_LENGTH = 64;
    private static final String NO_PARTITION_STATE = "23514";
    private static final Pattern ADDRESS_SHAPE = Pattern.compile(
            "(?:[0-9]{1,3}(?:\\.[0-9]{1,3}){3}|(?=[^:]*:[^:]*:)(?!.*:::)[0-9A-Fa-f.:]+)(?:%[0-9A-Za-z._-]+)?");

    private final JdbcClient jdbc;
    private final IdGenerator ids;

    JdbcAuditEntryRepository(JdbcClient jdbc, IdGenerator ids) {
        this.jdbc = jdbc;
        this.ids = ids;
    }

    @Override
    public void save(AuditEntry entry, Instant createdAt) {
        String sourceAddress = storableAddress(entry.sourceAddress());
        try {
            insert(entry, createdAt, sourceAddress);
        } catch (DataAccessException failure) {
            alertWhenNoPartition(failure, "audit entry", null, createdAt);
            throw failure;
        }
    }

    private void insert(AuditEntry entry, Instant createdAt, String sourceAddress) {
        jdbc.sql("""
                        insert into platform_audit.audit_entries (id, created_at, updated_at, action, actor_id, on_behalf_of_id, subject_kind, subject_id, detail,
                                                                  source_address, outcome_kind, login_method_kind,
                                                                  change_reason, before_text, after_text)
                        values (:id, :createdAt, :createdAt, :action, :actor, :onBehalfOf, :subjectKind, :subjectId, :detail, :sourceAddress, :outcome, :loginMethod,
                                :reason, :before, :after)
                        """)
                .param("id", ids.next())
                .param("createdAt", Timestamp.from(createdAt))
                .param("action", entry.action())
                .param("actor", entry.actorId() == null ? null : entry.actorId().value(), Types.OTHER)
                .param("onBehalfOf", entry.onBehalfOfId() == null ? null : entry.onBehalfOfId().value(), Types.OTHER)
                .param("subjectKind", entry.subject().kind().name())
                .param("subjectId", entry.subject().id(), Types.OTHER)
                .param("detail", entry.detail(), Types.VARCHAR)
                .param("sourceAddress", sourceAddress, Types.VARCHAR)
                .param("outcome", entry.outcome() == null ? null : entry.outcome().name(), Types.VARCHAR)
                .param("loginMethod", entry.loginMethod(), Types.VARCHAR)
                .param("reason", entry.changeReason(), Types.VARCHAR)
                .param("before", entry.beforeText(), Types.VARCHAR)
                .param("after", entry.afterText(), Types.VARCHAR)
                .update();
    }

    @Override
    public boolean saveForEvent(AuditEntry entry, Instant createdAt, UUID eventId, UUID correlationId) {
        try {
            return insertForEvent(entry, createdAt, eventId, correlationId);
        } catch (DataAccessException failure) {
            alertWhenNoPartition(failure, "event", eventId, createdAt);
            throw failure;
        }
    }

    private boolean insertForEvent(AuditEntry entry, Instant createdAt, UUID eventId, UUID correlationId) {
        return jdbc.sql("""
                        insert into platform_audit.audit_entries (id, created_at, updated_at, action, actor_id, on_behalf_of_id, subject_kind, subject_id, detail,
                                                                  event_id, correlation_id, change_reason, before_text, after_text)
                        values (:id, :createdAt, :createdAt, :action, :actor, :onBehalfOf, :subjectKind, :subjectId, :detail, :eventId, :correlationId,
                                :reason, :before, :after)
                        on conflict (event_id, created_at) do nothing
                        """)
                .param("id", ids.next())
                .param("createdAt", Timestamp.from(createdAt))
                .param("action", entry.action())
                .param("actor", entry.actorId() == null ? null : entry.actorId().value(), Types.OTHER)
                .param("onBehalfOf", entry.onBehalfOfId() == null ? null : entry.onBehalfOfId().value(), Types.OTHER)
                .param("subjectKind", entry.subject().kind().name())
                .param("subjectId", entry.subject().id(), Types.OTHER)
                .param("detail", entry.detail(), Types.VARCHAR)
                .param("eventId", eventId, Types.OTHER)
                .param("correlationId", correlationId, Types.OTHER)
                .param("reason", entry.changeReason(), Types.VARCHAR)
                .param("before", entry.beforeText(), Types.VARCHAR)
                .param("after", entry.afterText(), Types.VARCHAR)
                .update() > 0;
    }

    /** A late event or a missing pre-created month fails the write (no catch-all partition); the alert names the month and the event id, never the content. */
    private static void alertWhenNoPartition(DataAccessException failure, String what, UUID eventId, Instant createdAt) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && NO_PARTITION_STATE.equals(sql.getSQLState()) && sql.getMessage() != null
                    && sql.getMessage().contains("no partition")) {
                LOG.error("ALERT platform.audit late {} {} for {}: no audit partition", what, eventId == null ? "" : eventId,
                        createdAt.atOffset(ZoneOffset.UTC).toLocalDate().withDayOfMonth(1));
                return;
            }
        }
    }

    /** An address the column constraint would refuse is left empty rather than cut or allowed to fail an audit write: the trail keeps the entry (same rule as the last login address, ID-12). */
    static String storableAddress(String address) {
        if (address == null) {
            return null;
        }
        if (address.length() <= MAX_ADDRESS_LENGTH && ADDRESS_SHAPE.matcher(address).matches()) {
            return address;
        }
        LOG.error("ALERT platform.audit source address not recorded: not an address (length {})", address.length());
        return null;
    }
}
