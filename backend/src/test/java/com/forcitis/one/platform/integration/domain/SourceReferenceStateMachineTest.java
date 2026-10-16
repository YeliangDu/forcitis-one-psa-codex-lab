package com.forcitis.one.platform.integration.domain;

import com.forcitis.one.fixture.doubles.TestAccounts;
import static com.forcitis.one.platform.integration.api.ConfirmationStatus.CONFIRMED;
import static com.forcitis.one.platform.integration.api.ConfirmationStatus.PENDING;
import static com.forcitis.one.platform.integration.api.ConfirmationStatus.REJECTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.TestIds;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.TransitionTable;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.AggregateReference;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.kernel.event.TestEnvelopes;
import com.forcitis.one.platform.integration.api.ConfirmationStatus;
import com.forcitis.one.platform.integration.api.ExternalSource;
import com.forcitis.one.platform.integration.api.IntegrationError;
import com.forcitis.one.platform.integration.api.OwnObjectId;
import com.forcitis.one.platform.integration.api.SourceObjectId;
import com.forcitis.one.platform.integration.api.SourceObjectKind;
import com.forcitis.one.platform.integration.events.SourceReferenceConfirmed;
import com.forcitis.one.platform.integration.events.SourceReferenceRegistered;
import com.forcitis.one.platform.integration.events.SourceReferenceRegistrationRefused;
import com.forcitis.one.platform.integration.events.SourceReferenceRejected;
import com.forcitis.one.platform.integration.events.SourceReferenceSuperseded;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SourceReferenceStateMachineTest {
    static final Instant NOW = Instant.parse("2026-11-02T02:00:00Z");
    static final LocalDate START = LocalDate.of(2026, 11, 2);
    static final UserId REGISTRAR = TestAccounts.account(11);
    static final UserId DECIDER = TestAccounts.account(12);
    static final OwnObjectId LEGAL_ENTITY = new OwnObjectId(TestIds.uuid(5));

    static SourceReference accountingCode(ConfirmationStatus status, LocalDate effectiveTo) {
        return SourceReference.restore(new SourceReferenceId(TestIds.uuid(1)), ExternalSource.ACCOUNTING_SYSTEM, SourceObjectKind.LEGAL_ENTITY,
                new SourceObjectId(1021), LEGAL_ENTITY, START, effectiveTo, REGISTRAR, status.name(), null, null, 3, 3);
    }

    static SourceReference at(ConfirmationStatus status) {
        return accountingCode(status, null);
    }

    static SourceReference registered() {
        return SourceReference.register(new SourceReferenceId(TestIds.uuid(2)), ExternalSource.ACCOUNTING_SYSTEM, SourceObjectKind.LEGAL_ENTITY,
                new SourceObjectId(1021), LEGAL_ENTITY, START, REGISTRAR);
    }

    @Test
    @DisplayName("confirm and reject are tried from every status: only a pending reference can be decided, the rest is a conflict")
    void exhaustiveTransitions() {
        new TransitionTable<ConfirmationStatus, SourceReference>(ConfirmationStatus.class, SourceReferenceStateMachineTest::at, SourceReference::status)
                .operation("confirm", reference -> reference.confirm(DECIDER, NOW), PENDING, CONFIRMED)
                .operation("reject", reference -> reference.reject(DECIDER, NOW), PENDING, REJECTED)
                .verify();
    }

    @Test
    @DisplayName("registering announces SourceReferenceRegistered; a decision announces SourceReferenceConfirmed or SourceReferenceRejected and records who decided when")
    void eachStepAnnouncesItself() {
        SourceReference confirmed = registered();
        assertThat(confirmed.status()).isEqualTo(PENDING);
        confirmed.confirm(DECIDER, NOW);
        assertThat(confirmed.drainEvents(TestEnvelopes.INSTANCE)).extracting(DomainEvent::getClass)
                .containsExactly(SourceReferenceRegistered.class, SourceReferenceConfirmed.class);
        assertThat(confirmed.decidedBy()).isEqualTo(DECIDER);
        assertThat(confirmed.decidedAt()).isEqualTo(NOW);

        SourceReference rejected = registered();
        rejected.reject(DECIDER, NOW);
        assertThat(rejected.drainEvents(TestEnvelopes.INSTANCE)).extracting(DomainEvent::getClass)
                .containsExactly(SourceReferenceRegistered.class, SourceReferenceRejected.class);
    }

    @Test
    @DisplayName("a confirmation that closes the reference in effect names it as a related aggregate; one that closes nothing names none")
    void aConfirmationNamesTheReferenceItSupersedes() {
        SourceReferenceId superseded = new SourceReferenceId(TestIds.uuid(8));
        SourceReference correction = registered();
        correction.confirm(DECIDER, NOW, superseded);
        SourceReference first = registered();
        first.confirm(DECIDER, NOW);

        assertThat(confirmedEnvelope(correction).relatedAggregates()).containsExactly(new AggregateReference("SourceReference", superseded.value()));
        assertThat(confirmedEnvelope(first).relatedAggregates()).isEmpty();
    }

    private static EventEnvelope confirmedEnvelope(SourceReference reference) {
        return reference.drainEvents(TestEnvelopes.INSTANCE).stream().filter(SourceReferenceConfirmed.class::isInstance).findFirst().orElseThrow().envelope();
    }

    @Test
    @DisplayName("four eyes: the person who registered a reference cannot confirm or reject it, whatever else they may do")
    void theRegistrantCannotDecide() {
        for (java.util.function.Consumer<SourceReference> decision : List.<java.util.function.Consumer<SourceReference>>of(
                reference -> reference.confirm(REGISTRAR, NOW), reference -> reference.reject(REGISTRAR, NOW))) {
            SourceReference pending = at(PENDING);
            assertThatThrownBy(() -> decision.accept(pending)).isInstanceOfSatisfying(DomainException.class, e -> {
                assertThat(e.errorCode()).isEqualTo(IntegrationError.PLATFORM_INTEGRATION_CONFIRMER_IS_AUTHOR);
                assertThat(e.kind()).isEqualTo(ErrorKind.FORBIDDEN);
            });
            assertThat(pending.status()).isEqualTo(PENDING);
            assertThat(pending.hasPendingEvents()).isFalse();
        }
    }

    @Test
    @DisplayName("only the accounting organization code of a legal entity is registered by hand; every other kind and system is a 422")
    void onlyTheAccountingCodeIsEditableByHand() {
        for (ExternalSource system : ExternalSource.values()) {
            for (SourceObjectKind kind : SourceObjectKind.values()) {
                boolean allowed = system == ExternalSource.ACCOUNTING_SYSTEM && kind == SourceObjectKind.LEGAL_ENTITY;
                if (allowed) {
                    SourceReference.requireRegistrationAllowed(system, kind);
                    continue;
                }
                assertThatThrownBy(() -> SourceReference.requireRegistrationAllowed(system, kind)).as("%s / %s", system, kind)
                        .isInstanceOfSatisfying(DomainException.class, e -> {
                            assertThat(e.errorCode()).isEqualTo(IntegrationError.PLATFORM_INTEGRATION_SOURCE_OBJECT_KIND_NOT_ALLOWED);
                            assertThat(e.kind()).isEqualTo(ErrorKind.UNPROCESSABLE);
                        });
            }
        }
    }

    @Test
    @DisplayName("a registered code is a positive number with the day it starts to apply; anything else is a 422 that records nothing")
    void registrationNeedsAPositiveCodeAndAStartDay() {
        for (long code : new long[] {0, -1, Long.MIN_VALUE}) {
            assertThatThrownBy(() -> SourceReference.register(new SourceReferenceId(TestIds.uuid(2)), ExternalSource.ACCOUNTING_SYSTEM,
                    SourceObjectKind.LEGAL_ENTITY, new SourceObjectId(code), LEGAL_ENTITY, START, REGISTRAR)).isInstanceOfSatisfying(DomainException.class,
                    e -> assertThat(e.errorCode()).isEqualTo(IntegrationError.PLATFORM_INTEGRATION_SOURCE_REFERENCE_INVALID));
        }
        assertThatThrownBy(() -> SourceReference.register(new SourceReferenceId(TestIds.uuid(2)), ExternalSource.ACCOUNTING_SYSTEM,
                SourceObjectKind.LEGAL_ENTITY, new SourceObjectId(1021), LEGAL_ENTITY, null, REGISTRAR)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.kind()).isEqualTo(ErrorKind.UNPROCESSABLE));
    }

    @Test
    @DisplayName("the confirmed reference in effect ends where its successor starts, once, and announces SourceReferenceSuperseded; a successor before its start, or a reference not in effect, is a conflict")
    void supersedingEndsTheReferenceInEffect() {
        SourceReference inEffect = at(CONFIRMED);
        inEffect.supersede(START.plusDays(14));

        assertThat(inEffect.effectiveTo()).isEqualTo(START.plusDays(14));
        assertThat(inEffect.drainEvents(TestEnvelopes.INSTANCE)).singleElement().isInstanceOf(SourceReferenceSuperseded.class);
        assertConflict(at(CONFIRMED), START.minusDays(1));
        assertConflict(at(PENDING), START.plusDays(1));
        assertConflict(at(REJECTED), START.plusDays(1));
        assertConflict(accountingCode(CONFIRMED, START.plusDays(5)), START.plusDays(10));
    }

    @Test
    @DisplayName("a successor from the same day voids the reference in effect: its period becomes empty and SourceReferenceSuperseded is announced")
    void aSuccessorFromTheSameDayEmptiesThePeriod() {
        SourceReference wrong = at(CONFIRMED);
        wrong.supersede(START);

        assertThat(wrong.effectiveFrom()).isEqualTo(START);
        assertThat(wrong.effectiveTo()).isEqualTo(START);
        assertThat(wrong.drainEvents(TestEnvelopes.INSTANCE)).singleElement().isInstanceOf(SourceReferenceSuperseded.class);
        assertConflict(wrong, START.plusDays(3));
    }

    private static void assertConflict(SourceReference reference, LocalDate successorFrom) {
        assertThatThrownBy(() -> reference.supersede(successorFrom)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.errorCode()).isEqualTo(IntegrationError.PLATFORM_INTEGRATION_SOURCE_REFERENCE_CONFLICT));
    }

    @Test
    @DisplayName("what the sync allocates is confirmed from the start, announces nothing and carries no period; a kind with a period cannot be allocated")
    void allocatedReferencesAreConfirmedAtOnce() {
        SourceReference employee = SourceReference.allocate(new SourceReferenceId(TestIds.uuid(3)), ExternalSource.ORG_SOURCE, SourceObjectKind.EMPLOYEE,
                new SourceObjectId(Long.MIN_VALUE), new OwnObjectId(TestIds.uuid(4)));

        assertThat(employee.status()).isEqualTo(CONFIRMED);
        assertThat(employee.effectiveFrom()).isNull();
        assertThat(employee.hasPendingEvents()).isFalse();
        assertThatThrownBy(() -> SourceReference.allocate(new SourceReferenceId(TestIds.uuid(3)), ExternalSource.ACCOUNTING_SYSTEM,
                SourceObjectKind.LEGAL_ENTITY, new SourceObjectId(1021), LEGAL_ENTITY)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a refused registration announces SourceReferenceRegistrationRefused and nothing else")
    void aRefusalIsAnnounced() {
        SourceReferenceRefusal refusal = SourceReferenceRefusal.of(new SourceReferenceId(TestIds.uuid(9)), ExternalSource.ORG_SOURCE,
                SourceObjectKind.EMPLOYEE);

        assertThat(refusal.drainEvents(TestEnvelopes.INSTANCE)).singleElement().isInstanceOf(SourceReferenceRegistrationRefused.class);
    }
}
