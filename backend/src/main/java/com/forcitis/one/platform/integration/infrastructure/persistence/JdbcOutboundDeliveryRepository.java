package com.forcitis.one.platform.integration.infrastructure.persistence;

import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.platform.integration.domain.DeliveryKind;
import com.forcitis.one.platform.integration.domain.OutboundDelivery;
import com.forcitis.one.platform.integration.domain.OutboundDeliveryId;
import com.forcitis.one.platform.integration.domain.OutboundDeliveryRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcOutboundDeliveryRepository implements OutboundDeliveryRepository {

    private static final String COLUMNS = "id, deduplication_key, recipient_codes, title, description, target_url, status, attempt_count, "
            + "response_code, handled_note, handled_result, kind, target_key, correlation_id, version, event_count";

    private final JdbcClient jdbc;
    private final Clock clock;
    private final IdGenerator ids;

    JdbcOutboundDeliveryRepository(JdbcClient jdbc, Clock clock, IdGenerator ids) {
        this.ids = ids;
        this.clock = clock;
        this.jdbc = jdbc;
    }

    @Override
    public boolean insert(OutboundDelivery unsaved, Instant now) {
        return jdbc.sql("""
                        insert into platform_integration.outbound_deliveries
                               (id, deduplication_key, recipient_codes, skipped_recipient_codes, title, description, target_url, status,
                                next_attempt_at, last_error, kind, target_key, correlation_id, created_at, updated_at, version, event_count)
                        values (:id, :key, :recipients, :skipped, :title, :description, :url, :status, :next, :reason, :kind, :target,
                                :correlation, :now, :now, 0, 0)
                        on conflict (deduplication_key) do nothing
                        """)
                .param("id", ids.next())
                .param("key", unsaved.deduplicationKey())
                .param("recipients", codes(unsaved.recipients()))
                .param("skipped", unsaved.skippedRecipients().isEmpty() ? null : codes(unsaved.skippedRecipients()), Types.ARRAY)
                .param("title", unsaved.title())
                .param("description", unsaved.description(), Types.VARCHAR)
                .param("url", unsaved.targetUrl())
                .param("status", unsaved.status().name())
                .param("next", unsaved.nextAttemptAt() == null ? null : Timestamp.from(unsaved.nextAttemptAt()), Types.TIMESTAMP)
                .param("reason", unsaved.lastError(), Types.VARCHAR)
                .param("kind", unsaved.kind().name())
                .param("target", unsaved.targetKey(), Types.VARCHAR)
                .param("correlation", unsaved.correlation(), Types.OTHER)
                .param("now", Timestamp.from(now))
                .update() > 0;
    }

    @Override
    public Optional<OutboundDelivery> findByKey(String deduplicationKey) {
        return jdbc.sql("select " + COLUMNS + " from platform_integration.outbound_deliveries where deduplication_key = :key")
                .param("key", deduplicationKey).query((rs, n) -> row(rs)).optional();
    }

    @Override
    public List<OutboundDelivery> claimDue(Instant now, Duration lease, int limit) {
        return jdbc.sql("""
                        update platform_integration.outbound_deliveries
                           set attempt_count = attempt_count + 1, next_attempt_at = :leaseEnd, version = version + 1, updated_at = :now
                         where id in (select id from platform_integration.outbound_deliveries
                                       where status = 'PENDING' and next_attempt_at <= :now
                                       order by next_attempt_at, id
                                       limit :limit
                                         for update skip locked)
                        returning """ + " " + COLUMNS)
                .param("leaseEnd", Timestamp.from(now.plus(lease)))
                .param("now", Timestamp.from(now))
                .param("limit", limit)
                .query((rs, n) -> row(rs)).list();
    }

    @Override
    public Optional<OutboundDelivery> lockByKey(String deduplicationKey) {
        return jdbc.sql("select " + COLUMNS + " from platform_integration.outbound_deliveries where deduplication_key = :key for update")
                .param("key", deduplicationKey).query((rs, n) -> row(rs)).optional();
    }

    @Override
    public boolean save(OutboundDelivery claimed) {
        return jdbc.sql("""
                        update platform_integration.outbound_deliveries
                           set status = :status, response_code = :code, sent_at = :sentAt, next_attempt_at = :next,
                               last_error = :error, version = version + 1, updated_at = :updatedAt
                         where id = :id and status = :loadedStatus and attempt_count = :claimedAttempt
                        """)
                .param("status", claimed.status().name())
                .param("code", claimed.responseCode(), Types.VARCHAR)
                .param("sentAt", claimed.sentAt() == null ? null : Timestamp.from(claimed.sentAt()), Types.TIMESTAMP)
                .param("next", claimed.nextAttemptAt() == null ? null : Timestamp.from(claimed.nextAttemptAt()), Types.TIMESTAMP)
                .param("error", claimed.lastError(), Types.VARCHAR)
                .param("id", claimed.deliveryId().value()).param("loadedStatus", claimed.loadedStatus().name())
                .param("claimedAttempt", claimed.attemptCount()).param("updatedAt", Timestamp.from(clock.instant()))
                .update() > 0;
    }

    @Override
    public boolean saveHandled(OutboundDelivery delivery) {
        return jdbc.sql("""
                        update platform_integration.outbound_deliveries
                           set handled_note = :note, handled_result = :result, version = version + 1, updated_at = :updatedAt
                         where id = :id and status = :loadedStatus and attempt_count = :attempt
                        """)
                .param("note", delivery.handledNote(), Types.VARCHAR).param("result", delivery.handledResult(), Types.VARCHAR)
                .param("id", delivery.deliveryId().value()).param("loadedStatus", delivery.loadedStatus().name())
                .param("attempt", delivery.attemptCount()).param("updatedAt", Timestamp.from(clock.instant()))
                .update() > 0;
    }

    @Override
    public long countPendingCreatedBefore(Instant cutoff) {
        return jdbc.sql("select count(*) from platform_integration.outbound_deliveries where status = 'PENDING' and created_at < :cutoff")
                .param("cutoff", Timestamp.from(cutoff)).query(Long.class).single();
    }

    @Override
    public long countDeadLetters() {
        return jdbc.sql("select count(*) from platform_integration.outbound_deliveries where status = 'DEAD_LETTER'")
                .query(Long.class).single();
    }

    private static OutboundDelivery row(ResultSet rs) throws SQLException {
        return OutboundDelivery.restore(new OutboundDeliveryId(rs.getObject("id", UUID.class)), rs.getString("deduplication_key"),
                recipients(rs), rs.getString("title"), rs.getString("description"), rs.getString("target_url"),
                rs.getString("status"), rs.getInt("attempt_count"), rs.getString("response_code"), rs.getString("handled_note"), rs.getString("handled_result"),
                DeliveryKind.valueOf(rs.getString("kind")), rs.getString("target_key"), rs.getObject("correlation_id", UUID.class),
                rs.getLong("version"), rs.getLong("event_count"));
    }

    private static String[] codes(List<WecomUserId> ids) {
        return ids.stream().map(WecomUserId::value).toArray(String[]::new);
    }

    private static List<WecomUserId> recipients(ResultSet rs) throws SQLException {
        return Arrays.stream((String[]) rs.getArray("recipient_codes").getArray()).map(WecomUserId::new).toList();
    }
}
