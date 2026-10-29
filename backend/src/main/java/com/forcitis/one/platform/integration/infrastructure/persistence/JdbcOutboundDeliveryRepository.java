package com.forcitis.one.platform.integration.infrastructure.persistence;

import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.platform.integration.api.DeliveryPurpose;
import com.forcitis.one.platform.integration.api.DeliverySubject;
import com.forcitis.one.platform.integration.api.DeliverySubjectKind;
import com.forcitis.one.platform.integration.domain.DeliveryKind;
import com.forcitis.one.platform.integration.domain.OutboundDelivery;
import com.forcitis.one.platform.integration.domain.OutboundDeliveryId;
import com.forcitis.one.platform.integration.domain.OutboundDeliveryRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcOutboundDeliveryRepository implements OutboundDeliveryRepository {

    private static final String COLUMNS = """
            id, subject_kind, subject_id, purpose_kind, trigger_event_id, recipient_codes, title, description, target_url, status,
            attempt_count, response_code, handled_note, handled_result, kind, target_delivery_id, correlation_id, version, event_count
            """;
    private static final String SELECT = """
            select
            """ + COLUMNS + """
              from platform_integration.outbound_deliveries
            """;
    private static final String CARD_OF_SUBJECT = """
             where kind = 'CARD' and subject_kind = :kind and subject_id = :subject and purpose_kind = :purpose
               and trigger_event_id is not distinct from cast(:trigger as uuid)
            """;

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
                               (id, subject_kind, subject_id, purpose_kind, trigger_event_id, recipient_codes, skipped_recipient_codes, title,
                                description, target_url, status, next_attempt_at, last_error, kind, target_delivery_id, correlation_id,
                                created_at, updated_at, version, event_count)
                        values (:id, :subjectKind, :subjectId, :purpose, :trigger, :recipients, :skipped, :title, :description, :url, :status,
                                :next, :reason, :kind, :target, :correlation, :now, :now, 0, 0)
                        on conflict do nothing
                        """)
                .param("id", ids.next())
                .param("subjectKind", unsaved.subject().kind().name())
                .param("subjectId", unsaved.subject().id())
                .param("purpose", unsaved.subject().purpose().name())
                .param("trigger", unsaved.subject().triggerEvent().orElse(null), Types.OTHER)
                .param("recipients", codes(unsaved.recipients()))
                .param("skipped", unsaved.skippedRecipients().isEmpty() ? null : codes(unsaved.skippedRecipients()), Types.ARRAY)
                .param("title", unsaved.title())
                .param("description", unsaved.description(), Types.VARCHAR)
                .param("url", unsaved.targetUrl())
                .param("status", unsaved.status().name())
                .param("next", unsaved.nextAttemptAt() == null ? null : OffsetDateTime.ofInstant(unsaved.nextAttemptAt(), ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE)
                .param("reason", unsaved.lastError(), Types.VARCHAR)
                .param("kind", unsaved.kind().name())
                .param("target", unsaved.target() == null ? null : unsaved.target().value(), Types.OTHER)
                .param("correlation", unsaved.correlation(), Types.OTHER)
                .param("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
                .update() > 0;
    }

    @Override
    public Optional<OutboundDelivery> findById(UUID deliveryId) {
        return jdbc.sql(SELECT + """
                 where id = :id
                """)
                .param("id", deliveryId).query((rs, n) -> row(rs)).optional();
    }

    @Override
    public Optional<OutboundDelivery> findCard(DeliverySubject subject) {
        return cardOf(subject, "");
    }

    @Override
    public Optional<OutboundDelivery> lockCard(DeliverySubject subject) {
        return cardOf(subject, "for update");
    }

    private Optional<OutboundDelivery> cardOf(DeliverySubject subject, String lock) {
        return jdbc.sql(SELECT + CARD_OF_SUBJECT + lock)
                .param("kind", subject.kind().name()).param("subject", subject.id()).param("purpose", subject.purpose().name())
                .param("trigger", subject.triggerEvent().orElse(null), Types.OTHER).query((rs, n) -> row(rs)).optional();
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
                        returning
                        """ + COLUMNS)
                .param("leaseEnd", OffsetDateTime.ofInstant(now.plus(lease), ZoneOffset.UTC))
                .param("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
                .param("limit", limit)
                .query((rs, n) -> row(rs)).list();
    }

    @Override
    public Optional<OutboundDelivery> lockById(UUID deliveryId) {
        return jdbc.sql(SELECT + """
                 where id = :id
                   for update
                """)
                .param("id", deliveryId).query((rs, n) -> row(rs)).optional();
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
                .param("sentAt", claimed.sentAt() == null ? null : OffsetDateTime.ofInstant(claimed.sentAt(), ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE)
                .param("next", claimed.nextAttemptAt() == null ? null : OffsetDateTime.ofInstant(claimed.nextAttemptAt(), ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE)
                .param("error", claimed.lastError(), Types.VARCHAR)
                .param("id", claimed.deliveryId().value()).param("loadedStatus", claimed.loadedStatus().name())
                .param("claimedAttempt", claimed.attemptCount()).param("updatedAt", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
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
                .param("attempt", delivery.attemptCount()).param("updatedAt", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
                .update() > 0;
    }

    @Override
    public long countPendingCreatedBefore(Instant cutoff) {
        return jdbc.sql("""
                select count(*) from platform_integration.outbound_deliveries where status = 'PENDING' and created_at < :cutoff
                """)
                .param("cutoff", OffsetDateTime.ofInstant(cutoff, ZoneOffset.UTC)).query(Long.class).single();
    }

    @Override
    public long countDeadLetters() {
        return jdbc.sql("""
                select count(*) from platform_integration.outbound_deliveries where status = 'DEAD_LETTER'
                """)
                .query(Long.class).single();
    }

    private static OutboundDelivery row(ResultSet rs) throws SQLException {
        DeliverySubjectKind kind = DeliverySubjectKind.valueOf(rs.getString("subject_kind"));
        DeliverySubject subject = new DeliverySubject(kind, rs.getObject("subject_id", UUID.class), DeliveryPurpose.valueOf(rs.getString("purpose_kind")),
                Optional.ofNullable(rs.getObject("trigger_event_id", UUID.class)));
        return OutboundDelivery.restore(new OutboundDeliveryId(rs.getObject("id", UUID.class)), subject,
                recipients(rs), rs.getString("title"), rs.getString("description"), rs.getString("target_url"),
                rs.getString("status"), rs.getInt("attempt_count"), rs.getString("response_code"), rs.getString("handled_note"), rs.getString("handled_result"),
                DeliveryKind.valueOf(rs.getString("kind")), rs.getObject("target_delivery_id", UUID.class), rs.getObject("correlation_id", UUID.class),
                rs.getLong("version"), rs.getLong("event_count"));
    }

    private static String[] codes(List<WecomUserId> ids) {
        return ids.stream().map(WecomUserId::value).toArray(String[]::new);
    }

    private static List<WecomUserId> recipients(ResultSet rs) throws SQLException {
        return Arrays.stream((String[]) rs.getArray("recipient_codes").getArray()).map(WecomUserId::new).toList();
    }
}
