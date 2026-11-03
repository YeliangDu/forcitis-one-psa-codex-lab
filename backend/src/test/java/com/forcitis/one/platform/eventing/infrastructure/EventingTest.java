package com.forcitis.one.platform.eventing.infrastructure;

import com.forcitis.one.fixture.doubles.TestAccounts;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.forcitis.one.TestIds;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.SystemActors;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.kernel.event.ActorType;
import com.forcitis.one.kernel.event.EventDraft;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.kernel.CurrentUser;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.scheduling.annotation.Scheduled;

class EventingTest {
    static final Instant NOW = Instant.parse("2026-10-14T02:00:00.123Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    static EventDraft draft() {
        return new EventDraft("platform.eventing.Probe", 1, "Probe", TestIds.uuid(7), 3, new LegalEntityId(TestIds.uuid(1)), LocalDate.of(2026, 10, 14), List.of());
    }

    static StaticListableBeanFactory beans(Object... beans) {
        StaticListableBeanFactory factory = new StaticListableBeanFactory();
        int i = 0;
        for (Object bean : beans) {
            factory.addBean("bean" + i++, bean);
        }
        return factory;
    }

    static ContextualEventEnvelopes envelopes(CurrentUser user) {
        StaticListableBeanFactory factory = user == null ? beans(CLOCK) : beans(CLOCK, user);
        return new ContextualEventEnvelopes(CLOCK, factory.getBeanProvider(CurrentUser.class));
    }

    static CurrentUser signedIn(long id) {
        CurrentUser user = mock(CurrentUser.class);
        when(user.userId()).thenReturn(TestAccounts.account(id));
        return user;
    }

    @Test
    @DisplayName("the envelope has a UUIDv7 event id, the Clock's time, and the draft's aggregate, legal entity, business date and source")
    void envelopeCarriesTheDraftAndTheClock() {
        UUID correlation = Uuids.timeOrdered(CLOCK);
        try (WorkContext.Scope scope = WorkContext.enter(correlation, null, null)) {
            EventEnvelope envelope = envelopes(signedIn(9001)).wrap(draft());

            assertThat(envelope.eventId().version()).isEqualTo(7);
            assertThat(Uuids.instantOf(envelope.eventId())).isEqualTo(NOW);
            assertThat(envelope.occurredAt()).isEqualTo(NOW);
            assertThat(envelope.eventType()).isEqualTo("platform.eventing.Probe");
            assertThat(envelope.aggregateId()).isEqualTo(TestIds.uuid(7));
            assertThat(envelope.aggregateVersion()).isEqualTo(3);
            assertThat(envelope.legalEntityId()).isEqualTo(new LegalEntityId(TestIds.uuid(1)));
            assertThat(envelope.businessDate()).isEqualTo(LocalDate.of(2026, 10, 14));
            assertThat(envelope.accountingPeriod()).as("only the posting pipeline sets it").isNull();
            assertThat(envelope.correlationId()).isEqualTo(correlation);
            assertThat(envelope.causationId()).isNull();
        }
    }

    @Test
    @DisplayName("with a signed-in user the actor is USER and his id; inside a job or a handled event it is SYSTEM and the fixed UUID of the job or handler, which names it again (C5)")
    void actorIsTheUserOrTheJob() {
        try (WorkContext.Scope scope = WorkContext.enter(UUID.randomUUID(), null, null)) {
            EventEnvelope byUser = envelopes(signedIn(9001)).wrap(draft());
            assertThat(byUser.actorType()).isEqualTo(ActorType.USER);
            assertThat(byUser.actorId()).isEqualTo(TestAccounts.account(9001).value());
        }
        UUID cause = UUID.randomUUID();
        try (WorkContext.Scope scope = WorkContext.enter(UUID.randomUUID(), cause, "organization.workforce.snapshot.reconcile")) {
            EventEnvelope byJob = envelopes(signedIn(9001)).wrap(draft());
            assertThat(byJob.actorType()).isEqualTo(ActorType.SYSTEM);
            assertThat(byJob.actorId()).isEqualTo(SystemActors.idOf("organization.workforce.snapshot.reconcile"));
            assertThat(SystemActors.nameOf(byJob.actorId())).contains("organization.workforce.snapshot.reconcile");
            assertThat(byJob.causationId()).as("the handled event is the cause").isEqualTo(cause);
        }
    }

    @Test
    @DisplayName("with neither a user nor a job there is no actor: publishing fails instead of inventing one")
    void noActorFails() {
        assertThatThrownBy(() -> envelopes(null).wrap(draft())).isInstanceOf(IllegalStateException.class).hasMessageContaining("actor");
        CurrentUser nobody = mock(CurrentUser.class);
        when(nobody.userId()).thenThrow(new IllegalStateException("No authenticated user"));
        assertThatThrownBy(() -> envelopes(nobody).wrap(draft())).isInstanceOf(IllegalStateException.class).hasMessageContaining("actor");
    }

    @Test
    @DisplayName("outside any request or job a correlation id is generated for the event, and nothing leaks into the thread afterwards")
    void correlationIdIsGeneratedWhenThereIsNone() {
        EventEnvelope envelope;
        try (WorkContext.Scope scope = WorkContext.enter(UUID.randomUUID(), null, "test.envelope")) {
            WorkContext.correlationId();
        }
        assertThat(WorkContext.correlationId()).isEmpty();
        try (WorkContext.Scope scope = WorkContext.enter(UUID.randomUUID(), null, "test.job")) {
            envelope = envelopes(null).wrap(draft());
        }
        assertThat(envelope.correlationId()).isNotNull();
        assertThat(WorkContext.correlationId()).isEmpty();
        assertThat(WorkContext.systemActor()).isEmpty();
        assertThat(WorkContext.causationId()).isEmpty();
    }

    @Test
    @DisplayName("scopes nest and restore: the outer correlation id is back after an inner one closes")
    void scopesNest() {
        UUID outer = UUID.randomUUID();
        UUID inner = UUID.randomUUID();
        try (WorkContext.Scope a = WorkContext.enter(outer, null, "test.outer")) {
            try (WorkContext.Scope b = WorkContext.enter(inner, UUID.randomUUID(), "test.inner")) {
                assertThat(WorkContext.correlationId()).contains(inner);
                assertThat(WorkContext.systemActor()).contains("test.inner");
            }
            assertThat(WorkContext.correlationId()).contains(outer);
            assertThat(WorkContext.systemActor()).contains("test.outer");
            assertThat(WorkContext.causationId()).isEmpty();
        }
    }

    public static class Job {
        public String seenActor;
        public UUID seenCorrelation;

        @Scheduled(fixedDelay = 1000)
        @SchedulerLock(name = "test.eventing.probe.job")
        public void run() {
            seenActor = WorkContext.systemActor().orElse(null);
            seenCorrelation = WorkContext.correlationId().orElse(null);
        }
    }

    @Test
    @DisplayName("a scheduled job runs as SYSTEM under its lock name with a correlation id of its own")
    void scheduledJobsRunAsSystemUnderTheirLockName() {
        Job target = new Job();
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(new UnitOfWorkAspect(CLOCK));
        Job proxy = factory.getProxy();

        proxy.run();

        assertThat(target.seenActor).isEqualTo("test.eventing.probe.job");
        assertThat(target.seenCorrelation).isNotNull();
        assertThat(WorkContext.correlationId()).isEmpty();
    }
}
