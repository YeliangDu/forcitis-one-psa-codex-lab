package com.forcitis.one.platform.numbering.domain;

import static com.forcitis.one.platform.numbering.api.ConfirmationStatus.CONFIRMED;
import static com.forcitis.one.platform.numbering.api.ConfirmationStatus.PENDING_CONFIRMATION;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.kernel.DocumentType;
import com.forcitis.one.kernel.TransitionTable;
import com.forcitis.one.kernel.event.TestEnvelopes;
import com.forcitis.one.platform.numbering.api.ConfirmationStatus;
import com.forcitis.one.platform.numbering.api.ResetPolicy;
import com.forcitis.one.platform.numbering.events.NumberingRuleConfirmed;
import com.forcitis.one.platform.numbering.events.NumberingRuleReconfigured;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NumberingRuleStateMachineTest {
    static final NumberingPattern TEMPLATE = new NumberingPattern(NumberingPattern.DEFAULT_TEMPLATE, ResetPolicy.MONTHLY);
    static final NumberingPattern OTHER = new NumberingPattern("{TYPE}-{yyyy}-{seq:5}", ResetPolicy.YEARLY);

    static NumberingRule at(ConfirmationStatus status) {
        return NumberingRule.restore(DocumentType.QUOTE, TEMPLATE, 1, status.name(), 4);
    }

    @Test
    @DisplayName("confirm and reconfigure are tried from every status: confirm only from PENDING_CONFIRMATION; reconfigure always, and it confirms")
    void exhaustiveTransitions() {
        new TransitionTable<ConfirmationStatus, NumberingRule>(ConfirmationStatus.class, NumberingRuleStateMachineTest::at, NumberingRule::confirmation)
                .operation("confirm", NumberingRule::confirm, PENDING_CONFIRMATION, CONFIRMED)
                .operation("reconfigure", rule -> rule.reconfigure(OTHER, 7), PENDING_CONFIRMATION, CONFIRMED, CONFIRMED, CONFIRMED)
                .verify();
    }

    @Test
    @DisplayName("reconfiguring a pending rule records Reconfigured then Confirmed; a confirmed rule only Reconfigured")
    void events() {
        NumberingRule pending = at(PENDING_CONFIRMATION);
        pending.reconfigure(OTHER, 7);
        assertThat(pending.drainEvents(TestEnvelopes.INSTANCE)).extracting(e -> e.getClass().getSimpleName()).containsExactly("NumberingRuleReconfigured",
                "NumberingRuleConfirmed");
        assertThat(pending.pattern()).isEqualTo(OTHER);
        assertThat(pending.startValue()).isEqualTo(7);
        assertThat(pending.version()).isEqualTo(6);

        NumberingRule confirmed = at(CONFIRMED);
        confirmed.reconfigure(OTHER, 7);
        assertThat(confirmed.drainEvents(TestEnvelopes.INSTANCE)).singleElement().isInstanceOfSatisfying(NumberingRuleReconfigured.class, e -> {
            assertThat(e.documentType()).isEqualTo(DocumentType.QUOTE);
            assertThat(e.envelope().aggregateId()).isEqualTo("QUOTE");
            assertThat(e.envelope().aggregateVersion()).isEqualTo(5);
        });
    }
}
