package com.forcitis.one.organization.workforce.domain;

import static com.forcitis.one.organization.workforce.domain.LegalEntityStatus.ACTIVE;
import static com.forcitis.one.organization.workforce.domain.LegalEntityStatus.INACTIVE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.TestIds;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.TransitionTable;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.TestEnvelopes;
import com.forcitis.one.organization.workforce.api.LegalEntityCode;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.events.LegalEntityActivated;
import com.forcitis.one.organization.workforce.events.LegalEntityCodeCorrected;
import com.forcitis.one.organization.workforce.events.LegalEntityDeactivated;
import com.forcitis.one.organization.workforce.events.LegalEntityRegistered;
import com.forcitis.one.organization.workforce.events.LegalEntityRenamed;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LegalEntityStateMachineTest {
    static final LegalEntityId ID = new LegalEntityId(TestIds.uuid(4));

    static LegalEntity at(LegalEntityStatus status) {
        return LegalEntity.restore(ID, new LegalEntityCode("FST"), "Synthetic", status.name(), null, 7, 7);
    }

    @Test
    @DisplayName("activate and deactivate are tried from every status: only the opposite status is legal, the rest is refused")
    void exhaustiveTransitions() {
        new TransitionTable<LegalEntityStatus, LegalEntity>(LegalEntityStatus.class, LegalEntityStateMachineTest::at, LegalEntity::status)
                .operation("activate", LegalEntity::activate, INACTIVE, ACTIVE)
                .operation("deactivate", LegalEntity::deactivate, ACTIVE, INACTIVE)
                .verify();
    }

    @Test
    @DisplayName("declaring the status it already has changes nothing; a different one goes through the machine; an unknown one is a 422")
    void declareStatus() {
        LegalEntity active = at(ACTIVE);
        active.declareStatus("ACTIVE");
        assertThat(active.hasPendingEvents()).isFalse();

        active.declareStatus("INACTIVE");
        assertThat(active.status()).isEqualTo(INACTIVE);
        assertThat(active.drainEvents(TestEnvelopes.INSTANCE)).singleElement().isInstanceOf(LegalEntityDeactivated.class);

        for (String unknown : new String[] {"GONE", "active", null}) {
            assertThatThrownBy(() -> at(ACTIVE).declareStatus(unknown)).isInstanceOfSatisfying(DomainException.class, e -> {
                assertThat(e.errorCode()).isEqualTo(WorkforceError.ORGANIZATION_WORKFORCE_LEGAL_ENTITY_STATUS_UNKNOWN);
                assertThat(e.kind()).isEqualTo(ErrorKind.UNPROCESSABLE);
            });
        }
    }

    @Test
    @DisplayName("registering announces LegalEntityRegistered with the id the repository gave, the code and the legal entity in the envelope")
    void registering() {
        LegalEntity saved = LegalEntity.register(ID, new LegalEntityCode("FST"), "  Synthetic  ");

        assertThat(saved.name()).isEqualTo("Synthetic");
        assertThat(saved.status()).isEqualTo(ACTIVE);
        List<DomainEvent> events = saved.drainEvents(TestEnvelopes.INSTANCE);
        assertThat(events).singleElement().isInstanceOfSatisfying(LegalEntityRegistered.class, e -> {
            assertThat(e.code()).isEqualTo(new LegalEntityCode("FST"));
            assertThat(e.envelope().aggregateId()).isEqualTo(TestIds.uuid(4));
            assertThat(e.envelope().legalEntityId()).isEqualTo(ID);
            assertThat(e.envelope().aggregateVersion()).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("the code can be corrected while no number was issued, and the correction is announced with the old and the new code")
    void codeCorrectionBeforeNumbersWereIssued() {
        LegalEntity entity = at(ACTIVE);

        entity.correctCode(new LegalEntityCode("FSTX"), false);

        assertThat(entity.code()).isEqualTo(new LegalEntityCode("FSTX"));
        assertThat(entity.drainEvents(TestEnvelopes.INSTANCE)).singleElement().isInstanceOfSatisfying(LegalEntityCodeCorrected.class, e -> {
            assertThat(e.previousCode()).isEqualTo(new LegalEntityCode("FST"));
            assertThat(e.newCode()).isEqualTo(new LegalEntityCode("FSTX"));
        });
    }

    @Test
    @DisplayName("once a number carrying the code was issued the code is fixed: 409, the code stays, no event")
    void codeIsLockedAfterNumbersWereIssued() {
        LegalEntity entity = at(ACTIVE);

        assertThatThrownBy(() -> entity.correctCode(new LegalEntityCode("FSTX"), true)).isInstanceOfSatisfying(DomainException.class, e -> {
            assertThat(e.errorCode()).isEqualTo(WorkforceError.ORGANIZATION_WORKFORCE_LEGAL_ENTITY_CODE_LOCKED);
            assertThat(e.kind()).isEqualTo(ErrorKind.CONFLICT);
        });
        assertThat(entity.code()).isEqualTo(new LegalEntityCode("FST"));
        assertThat(entity.hasPendingEvents()).isFalse();
    }

    @Test
    @DisplayName("saying the same code again is not a correction, even after numbers were issued")
    void sameCodeIsNoCorrection() {
        LegalEntity entity = at(ACTIVE);
        entity.correctCode(new LegalEntityCode("FST"), true);
        assertThat(entity.hasPendingEvents()).isFalse();
    }

    @Test
    @DisplayName("a rename announces LegalEntityRenamed once, an unchanged name nothing; a blank or over-long name is refused")
    void renaming() {
        LegalEntity entity = at(ACTIVE);
        entity.rename("Synthetic");
        assertThat(entity.hasPendingEvents()).isFalse();
        entity.rename("Renamed");
        assertThat(entity.drainEvents(TestEnvelopes.INSTANCE)).singleElement().isInstanceOf(LegalEntityRenamed.class);
        for (String bad : new String[] {"", "  ", null, "x".repeat(101)}) {
            assertThatThrownBy(() -> at(ACTIVE).rename(bad)).isInstanceOfSatisfying(DomainException.class,
                    e -> assertThat(e.errorCode()).isEqualTo(WorkforceError.ORGANIZATION_WORKFORCE_LEGAL_ENTITY_NAME_INVALID));
        }
    }

    @Test
    @DisplayName("several changes in one unit are numbered contiguously after the loaded event sequence; the lock version moves once")
    void severalEventsInOneUnit() {
        LegalEntity entity = at(ACTIVE);
        entity.rename("Renamed");
        entity.deactivate();
        entity.activate();

        List<DomainEvent> events = entity.drainEvents(TestEnvelopes.INSTANCE);

        assertThat(events).extracting(e -> e.envelope().aggregateVersion()).containsExactly(8L, 9L, 10L);
        assertThat(events.get(2)).isInstanceOf(LegalEntityActivated.class);
        assertThat(entity.eventSequence()).isEqualTo(10);
        assertThat(entity.version()).isEqualTo(8);
    }
}
