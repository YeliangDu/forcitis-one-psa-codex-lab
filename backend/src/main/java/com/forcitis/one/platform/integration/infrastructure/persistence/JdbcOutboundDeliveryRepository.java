package com.forcitis.one.platform.integration.infrastructure.persistence;

import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.platform.integration.domain.DeliveryKind;
import com.forcitis.one.platform.integration.domain.DeliveryStatus;
import com.forcitis.one.platform.integration.domain.NewDelivery;
import com.forcitis.one.platform.integration.domain.OutboundDelivery;
import com.forcitis.one.platform.integration.domain.OutboundDeliveryId;
import com.forcitis.one.platform.integration.domain.OutboundDeliveryRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcOutboundDeliveryRepository implements OutboundDeliveryRepository {

    private static final String COLUMNS = "id, deduplication_key, recipients, title, description, target_url, status, attempt_count, "
            + "response_code, handled_note, kind, target_key";

    private static final String HANDLED_SUFFIX = "#handled";

    private final JdbcClient jdbc;

    JdbcOutboundDeliveryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean insert(NewDelivery delivery, Instant now) {
        boolean pending = delivery.status() == DeliveryStatus.PENDING;
        return jdbc.sql("""
                        insert into platform_integration.outbound_deliveries
                               (deduplication_key, recipients, skipped_recipients, title, description, target_url, status,
                                next_attempt_at, last_error, created_at, updated_at)
                        values (:key, :recipients, :skipped, :title, :description, :url, :status, :next, :reason, :now, :now)
                        on conflict (deduplication_key) do nothing
                        """)
                .param("key", delivery.deduplicationKey())
                .param("recipients", join(delivery.recipients()))
                .param("skipped", delivery.skippedRecipients().isEmpty() ? null : join(delivery.skippedRecipients()), Types.VARCHAR)
                .param("title", delivery.title())
                .param("description", delivery.description(), Types.VARCHAR)
                .param("url", delivery.targetUrl())
                .param("status", delivery.status().name())
                .param("next", pending ? Timestamp.from(now) : null, Types.TIMESTAMP)
                .param("reason", delivery.reason(), Types.VARCHAR)
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
                           set attempt_count = attempt_count + 1, next_attempt_at = :leaseEnd, updated_at = :now
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
    public boolean markSent(OutboundDelivery claimed, String responseCode, Instant now) {
        return jdbc.sql("""
                        update platform_integration.outbound_deliveries
                           set status = 'SENT', response_code = :code, sent_at = :now, next_attempt_at = null, last_error = null,
                               updated_at = :now
                         where id = :id and status = 'PENDING' and attempt_count = :claimedAttempt
                        """)
                .param("code", responseCode, Types.VARCHAR).param("now", Timestamp.from(now)).param("id", claimed.deliveryId().value())
                .param("claimedAttempt", claimed.attemptCount()).update() > 0;
    }

    @Override
    public boolean markRetry(OutboundDelivery claimed, Instant nextAttemptAt, String error) {
        return jdbc.sql("""
                        update platform_integration.outbound_deliveries
                           set next_attempt_at = :next, last_error = :error, updated_at = now()
                         where id = :id and status = 'PENDING' and attempt_count = :claimedAttempt
                        """)
                .param("next", Timestamp.from(nextAttemptAt)).param("error", error).param("id", claimed.deliveryId().value())
                .param("claimedAttempt", claimed.attemptCount()).update() > 0;
    }

    @Override
    public boolean markDeadLetter(OutboundDelivery claimed, String error) {
        return finish(claimed, DeliveryStatus.DEAD_LETTER, error);
    }

    @Override
    public boolean markSkipped(OutboundDelivery claimed, String reason) {
        return finish(claimed, DeliveryStatus.SKIPPED, reason);
    }

    @Override
    public void recordHandledNote(String deduplicationKey, String note) {
        jdbc.sql("""
                        update platform_integration.outbound_deliveries
                           set handled_note = :note, updated_at = now()
                         where deduplication_key = :key
                        """)
                .param("note", note).param("key", deduplicationKey).update();
    }

    @Override
    public void recordHandledResult(String deduplicationKey, String result) {
        jdbc.sql("""
                        update platform_integration.outbound_deliveries
                           set handled_result = :result, updated_at = now()
                         where deduplication_key = :key
                        """)
                .param("result", result).param("key", deduplicationKey).update();
    }

    @Override
    public boolean queueHandledNote(OutboundDelivery sentCard, String note, Instant now) {
        return jdbc.sql("""
                        insert into platform_integration.outbound_deliveries
                               (deduplication_key, recipients, title, description, target_url, status, next_attempt_at, kind, target_key,
                                created_at, updated_at)
                        values (:key, :recipients, :title, :note, :url, 'PENDING', :now, 'HANDLED_NOTE', :target, :now, :now)
                        on conflict (deduplication_key) do nothing
                        """)
                .param("key", sentCard.deduplicationKey() + HANDLED_SUFFIX)
                .param("recipients", join(sentCard.recipients()))
                .param("title", sentCard.title())
                .param("note", note)
                .param("url", sentCard.targetUrl())
                .param("target", sentCard.deduplicationKey())
                .param("now", Timestamp.from(now))
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

    private boolean finish(OutboundDelivery claimed, DeliveryStatus status, String reason) {
        return jdbc.sql("""
                        update platform_integration.outbound_deliveries
                           set status = :status, next_attempt_at = null, last_error = :reason, updated_at = now()
                         where id = :id and status = 'PENDING' and attempt_count = :claimedAttempt
                        """)
                .param("status", status.name()).param("reason", reason).param("id", claimed.deliveryId().value())
                .param("claimedAttempt", claimed.attemptCount()).update() > 0;
    }

    private static OutboundDelivery row(ResultSet rs) throws SQLException {
        return new OutboundDelivery(new OutboundDeliveryId(rs.getLong("id")), rs.getString("deduplication_key"),
                split(rs.getString("recipients")), rs.getString("title"), rs.getString("description"), rs.getString("target_url"),
                DeliveryStatus.valueOf(rs.getString("status")), rs.getInt("attempt_count"), rs.getString("response_code"),
                rs.getString("handled_note"), DeliveryKind.valueOf(rs.getString("kind")), rs.getString("target_key"));
    }

    private static String join(List<WecomUserId> ids) {
        return ids.stream().map(WecomUserId::value).collect(Collectors.joining("|"));
    }

    private static List<WecomUserId> split(String joined) {
        return Arrays.stream(joined.split("\\|")).filter(id -> !id.isEmpty()).map(WecomUserId::new).toList();
    }
}
