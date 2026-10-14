package com.forcitis.one.platform.numbering.domain;

import static com.forcitis.one.platform.numbering.api.ConfirmationStatus.CONFIRMED;
import static com.forcitis.one.platform.numbering.api.ConfirmationStatus.PENDING_CONFIRMATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.kernel.DocumentType;
import com.forcitis.one.kernel.TransitionTable;
import com.forcitis.one.kernel.event.TestEnvelopes;
import com.forcitis.one.platform.numbering.api.ConfirmationStatus;
import com.forcitis.one.platform.numbering.api.ResetPolicy;
import com.forcitis.one.platform.numbering.events.NumberingRuleConfirmed;
import com.forcitis.one.platform.numbering.events.NumberingRuleReconfigured;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NumberingRuleStateMachineTest {
    static final NumberingPattern TEMPLATE = new NumberingPattern(NumberingPattern.DEFAULT_TEMPLATE, ResetPolicy.MONTHLY);
    static final NumberingPattern OTHER = new NumberingPattern("{TYPE}-{yyyy}-{seq:5}", ResetPolicy.YEARLY);

    static final LocalDate ENFORCED_SINCE = LocalDate.of(2026, 9, 1);
    static final LocalDate DAY = LocalDate.of(2026, 10, 20);

    static NumberingRule at(ConfirmationStatus status) {
        return NumberingRule.restore(DocumentType.QUOTE, TEMPLATE, 1, status.name(), 1, status == CONFIRMED ? ENFORCED_SINCE : null, 4, 4);
    }

    @Test
    @DisplayName("confirm and reconfigure are tried from every status: confirm only from PENDING_CONFIRMATION; reconfigure always, and it confirms")
    void exhaustiveTransitions() {
        new TransitionTable<ConfirmationStatus, NumberingRule>(ConfirmationStatus.class, NumberingRuleStateMachineTest::at, NumberingRule::confirmation)
                .operation("confirm", rule -> rule.confirm(DAY), PENDING_CONFIRMATION, CONFIRMED)
                .operation("reconfigure", rule -> rule.reconfigure(OTHER, 7, DAY), PENDING_CONFIRMATION, CONFIRMED, CONFIRMED, CONFIRMED)
                .verify();
    }

    @Test
    @DisplayName("reconfiguring a pending rule records Reconfigured then Confirmed; a confirmed rule only Reconfigured")
    void events() {
        NumberingRule pending = at(PENDING_CONFIRMATION);
        pending.reconfigure(OTHER, 7, DAY);
        assertThat(pending.drainEvents(TestEnvelopes.INSTANCE)).extracting(e -> e.getClass().getSimpleName()).containsExactly("NumberingRuleReconfigured",
                "NumberingRuleConfirmed");
        assertThat(pending.pattern()).isEqualTo(OTHER);
        assertThat(pending.startValue()).isEqualTo(7);
        assertThat(pending.eventSequence()).isEqualTo(6);
        assertThat(pending.version()).isEqualTo(5);

        NumberingRule confirmed = at(CONFIRMED);
        confirmed.reconfigure(OTHER, 7, DAY);
        assertThat(confirmed.drainEvents(TestEnvelopes.INSTANCE)).singleElement().isInstanceOfSatisfying(NumberingRuleReconfigured.class, e -> {
            assertThat(e.documentType()).isEqualTo(DocumentType.QUOTE);
            assertThat(e.envelope().aggregateId()).isEqualTo("QUOTE");
            assertThat(e.envelope().aggregateVersion()).isEqualTo(5);
        });
    }

    @Test
    @DisplayName("the events carry the template version and the day it takes effect, and the effective day is their businessDate")
    void eventsCarryRuleVersionAndEffectiveDay() {
        NumberingRule pending = at(PENDING_CONFIRMATION);
        pending.reconfigure(OTHER, 7, DAY);
        assertThat(pending.drainEvents(TestEnvelopes.INSTANCE)).satisfiesExactly(
                reconfigured -> assertThat(reconfigured).isInstanceOfSatisfying(NumberingRuleReconfigured.class, e -> {
                    assertThat(e.ruleVersion().value()).as("a template that was never in force is not a new version").isEqualTo(1);
                    assertThat(e.effectiveFrom()).isEqualTo(DAY);
                    assertThat(e.envelope().businessDate()).isEqualTo(DAY);
                }),
                confirmed -> assertThat(confirmed).isInstanceOfSatisfying(NumberingRuleConfirmed.class, e -> {
                    assertThat(e.ruleVersion().value()).isEqualTo(1);
                    assertThat(e.effectiveFrom()).isEqualTo(DAY);
                    assertThat(e.envelope().businessDate()).isEqualTo(DAY);
                }));
        assertThat(pending.ruleVersion().value()).isEqualTo(1);
        assertThat(pending.effectiveFrom()).isEqualTo(DAY);

        NumberingRule confirmed = at(CONFIRMED);
        confirmed.reconfigure(OTHER, 7, DAY);
        assertThat(confirmed.drainEvents(TestEnvelopes.INSTANCE)).singleElement().isInstanceOfSatisfying(NumberingRuleReconfigured.class, e -> {
            assertThat(e.ruleVersion().value()).as("replacing a template in force starts the next version").isEqualTo(2);
            assertThat(e.effectiveFrom()).isEqualTo(DAY);
            assertThat(e.envelope().businessDate()).isEqualTo(DAY);
        });
        assertThat(confirmed.ruleVersion().value()).isEqualTo(2);
    }

    @Test
    @DisplayName("an event is sealed with the version of its own time even when a later change follows before it is drained")
    void sealedVersionsDoNotMoveWithLaterChanges() {
        NumberingRule rule = at(CONFIRMED);
        rule.reconfigure(OTHER, 7, DAY);
        rule.reconfigure(TEMPLATE, 8, DAY.plusDays(1));

        assertThat(rule.drainEvents(TestEnvelopes.INSTANCE)).extracting(e -> ((NumberingRuleReconfigured) e).ruleVersion().value()).containsExactly(2L, 3L);
    }

    @Test
    @DisplayName("without an effective day nothing is confirmed or reconfigured")
    void effectiveDayIsRequired() {
        NumberingRule pending = at(PENDING_CONFIRMATION);
        assertThatThrownBy(() -> pending.confirm(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> pending.reconfigure(OTHER, 7, null)).isInstanceOf(NullPointerException.class);
        assertThat(pending.confirmation()).isEqualTo(PENDING_CONFIRMATION);
        assertThat(pending.hasPendingEvents()).isFalse();
        assertThatThrownBy(() -> NumberingRule.restore(DocumentType.QUOTE, TEMPLATE, 1, "CONFIRMED", 1, null, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
