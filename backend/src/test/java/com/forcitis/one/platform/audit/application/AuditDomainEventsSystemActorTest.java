package com.forcitis.one.platform.audit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.forcitis.one.TestIds;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.event.ActorType;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.kernel.event.EventSource;
import com.forcitis.one.kernel.event.SourceSystem;
import com.forcitis.one.organization.workforce.api.LegalEntityCode;
import com.forcitis.one.organization.workforce.events.LegalEntityRegistered;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.domain.AuditEntryRepository;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

/** The audit trail names the job behind a SYSTEM actor from the declared catalogue, whatever ran on this instance before (P2-H1). */
class AuditDomainEventsSystemActorTest {
    static final UUID NAMESPACE = UUID.fromString("0d4f6c1e-7b2c-4c39-9e5d-3f1b7c2e6d90");

    final AuditEntryRepository entries = mock(AuditEntryRepository.class);
    final AuditDomainEvents audit = new AuditDomainEvents(entries);
    final ListAppender<ILoggingEvent> logged = new ListAppender<>();

    @BeforeEach
    void captureLogs() {
        logged.start();
        ((Logger) LoggerFactory.getLogger(AuditDomainEvents.class)).addAppender(logged);
        when(entries.saveForEvent(any(), any(), any(), any())).thenReturn(true);
    }

    @AfterEach
    void releaseLogs() {
        ((Logger) LoggerFactory.getLogger(AuditDomainEvents.class)).detachAppender(logged);
    }

    /** RFC 9562 version 5, computed here so the test does not ask SystemActors to make (and so register) the id. */
    static UUID version5(String name) throws Exception {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        sha1.update(ByteBuffer.allocate(16).putLong(NAMESPACE.getMostSignificantBits()).putLong(NAMESPACE.getLeastSignificantBits()).array());
        ByteBuffer hash = ByteBuffer.wrap(sha1.digest(name.getBytes(StandardCharsets.UTF_8)));
        long high = (hash.getLong() & ~0xF000L) | 0x5000L;
        long low = (hash.getLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new UUID(high, low);
    }

    private AuditEntry auditedBy(String actorId) {
        EventEnvelope envelope = new EventEnvelope(UUID.randomUUID(), "organization.workforce.LegalEntityRegistered", 1, Instant.parse("2026-10-15T02:00:00Z"),
                "LegalEntity", TestIds.uuid(9).toString(), 1, UUID.randomUUID(), null, ActorType.SYSTEM, actorId, new LegalEntityId(TestIds.uuid(9)), null,
                new EventSource(SourceSystem.ONE, null), null, List.of());
        audit.on(new LegalEntityRegistered(envelope, new LegalEntityId(TestIds.uuid(9)), new LegalEntityCode("SYS")));
        ArgumentCaptor<AuditEntry> saved = ArgumentCaptor.forClass(AuditEntry.class);
        verify(entries).saveForEvent(saved.capture(), any(), any(), any());
        return saved.getValue();
    }

    private List<String> alerts() {
        return logged.list.stream().map(ILoggingEvent::getFormattedMessage).filter(message -> message.startsWith("ALERT ")).toList();
    }

    @Test
    @DisplayName("P2-H1 重启后重投的 SYSTEM 事件仍写出作业名：本实例尚未运行该作业，名字从声明的目录反查，不告警")
    void P2_H1_aRedeliveredSystemEventNamesItsJob() throws Exception {
        AuditEntry entry = auditedBy(version5("platform.approval.deadline.enforce").toString());

        assertThat(entry.actorId()).isNull();
        assertThat(entry.detail()).isEqualTo("by platform.approval.deadline.enforce");
        assertThat(alerts()).isEmpty();
    }

    @Test
    @DisplayName("P2-H1 目录里没有的 SYSTEM actorId 写成 by <uuid> 并告警（兜底保留）")
    void P2_H1_anUndeclaredSystemActorIsAuditedByItsIdAndAlerts() throws Exception {
        UUID unknown = version5("no.such.job.declared");

        AuditEntry entry = auditedBy(unknown.toString());

        assertThat(entry.detail()).isEqualTo("by " + unknown);
        assertThat(alerts()).hasSize(1);
    }
}
