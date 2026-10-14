package com.forcitis.one.platform.numbering.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.kernel.DocumentType;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.TestEnvelopes;
import com.forcitis.one.platform.numbering.api.ConfirmationKind;
import com.forcitis.one.platform.numbering.api.NumberingError;
import com.forcitis.one.platform.numbering.api.ResetPolicy;
import com.forcitis.one.platform.numbering.api.RuleVersion;
import com.forcitis.one.platform.numbering.events.NumberingRuleConfirmationGiven;
import com.forcitis.one.platform.numbering.events.NumberingRuleConfirmed;
import com.forcitis.one.platform.numbering.events.NumberingRuleReconfigured;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NumberingRuleStateMachineTest {
    static final NumberingPattern TEMPLATE = new NumberingPattern(NumberingPattern.DEFAULT_TEMPLATE, ResetPolicy.MONTHLY);
    static final NumberingPattern OTHER = new NumberingPattern("{TYPE}-{yyyy}-{seq:5}", ResetPolicy.YEARLY);
    static final Instant AT = Instant.parse("2026-10-14T08:00:00Z");
    static final UserId AUTHOR = new UserId(11);
    static final UserId CONFIRMER = new UserId(12);
    static final UserId FINANCE = new UserId(13);

    static TemplateVersion pending(int number, UserId author) {
        return new TemplateVersion(number, TEMPLATE, 1, author, AT, VersionStatus.PENDING_CONFIRMATION, null, null, null, null);
    }

    static TemplateVersion confirmed(int number) {
        return new TemplateVersion(number, TEMPLATE, 1, AUTHOR, AT, VersionStatus.CONFIRMED, CONFIRMER, AT, null, null);
    }

    static NumberingRule rule(DocumentType type, TemplateVersion... versions) {
        return NumberingRule.restore(type, List.of(versions), 4, 4);
    }

    static DomainException refusal(Runnable action) {
        return (DomainException) catchThrowable(action);
    }

    private static Throwable catchThrowable(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException failure) {
            return failure;
        }
        throw new AssertionError("expected a refusal");
    }

    @Test
    @DisplayName("ADR-0027 2.4 the author of a version cannot confirm it; someone else can, and the version then issues")
    void theAuthorCannotConfirmTheirOwnVersion() {
        NumberingRule rule = rule(DocumentType.QUOTE, pending(1, AUTHOR));

        DomainException refused = refusal(() -> rule.confirm(AUTHOR, AT));

        assertThat(refused.errorCode()).isEqualTo(NumberingError.PLATFORM_NUMBERING_CONFIRMER_IS_AUTHOR);
        assertThat(refused.kind()).isEqualTo(ErrorKind.FORBIDDEN);
        assertThat(rule.inForce()).isEmpty();
        assertThat(rule.hasPendingEvents()).isFalse();

        rule.confirm(CONFIRMER, AT);

        assertThat(rule.inForce()).hasValueSatisfying(v -> {
            assertThat(v.number()).isEqualTo(1);
            assertThat(v.confirmedBy()).isEqualTo(CONFIRMER);
        });
        assertThat(rule.drainEvents(TestEnvelopes.INSTANCE)).satisfiesExactly(
                e -> assertThat(e).isInstanceOfSatisfying(NumberingRuleConfirmationGiven.class, given -> {
                    assertThat(given.kind()).isEqualTo(ConfirmationKind.BUSINESS);
                    assertThat(given.ruleVersion()).isEqualTo(new RuleVersion(1));
                }),
                e -> assertThat(e).isInstanceOf(NumberingRuleConfirmed.class));
    }

    @Test
    @DisplayName("ADR-0027 2.4 a seeded default template has no author, so anyone entitled to approve may confirm it")
    void aSeededDefaultCanBeConfirmedByAnyone() {
        NumberingRule rule = rule(DocumentType.QUOTE, pending(1, null));

        rule.confirm(CONFIRMER, AT);

        assertThat(rule.inForce()).isPresent();
    }

    @Test
    @DisplayName("ADR-0027 2.4 a change saves a new version; the confirmed one keeps issuing until the new one is confirmed")
    void aChangeLeavesTheConfirmedVersionInForce() {
        NumberingRule rule = rule(DocumentType.QUOTE, confirmed(1));

        rule.propose(OTHER, 7, AUTHOR, AT);

        assertThat(rule.inForce()).hasValueSatisfying(v -> assertThat(v.number()).isEqualTo(1));
        assertThat(rule.pending()).hasValueSatisfying(v -> {
            assertThat(v.number()).isEqualTo(2);
            assertThat(v.pattern()).isEqualTo(OTHER);
            assertThat(v.startValue()).isEqualTo(7);
            assertThat(v.author()).isEqualTo(AUTHOR);
        });
        assertThat(rule.drainEvents(TestEnvelopes.INSTANCE)).singleElement().isInstanceOfSatisfying(NumberingRuleReconfigured.class,
                e -> assertThat(e.ruleVersion()).isEqualTo(new RuleVersion(2)));

        rule.confirm(CONFIRMER, AT);

        assertThat(rule.inForce()).hasValueSatisfying(v -> assertThat(v.number()).isEqualTo(2));
        assertThat(rule.pending()).isEmpty();
    }

    @Test
    @DisplayName("ADR-0027 2.4 a second change before confirmation replaces the waiting version, which is never confirmed")
    void aSecondChangeSupersedesTheWaitingVersion() {
        NumberingRule rule = rule(DocumentType.QUOTE, pending(1, AUTHOR));

        rule.propose(OTHER, 7, AUTHOR, AT);

        assertThat(rule.versions()).extracting(TemplateVersion::status).containsExactly(VersionStatus.SUPERSEDED, VersionStatus.PENDING_CONFIRMATION);
        assertThat(rule.changedVersions()).hasSize(2);
        assertThat(refusal(() -> rule.confirm(AUTHOR, AT)).errorCode()).isEqualTo(NumberingError.PLATFORM_NUMBERING_CONFIRMER_IS_AUTHOR);
    }

    @Test
    @DisplayName("ADR-0027 2.4 a version is confirmed once; with nothing waiting there is nothing to confirm")
    void nothingToConfirmAndNoSecondConfirmation() {
        NumberingRule settled = rule(DocumentType.QUOTE, confirmed(1));
        assertThat(refusal(() -> settled.confirm(CONFIRMER, AT)).errorCode()).isEqualTo(NumberingError.PLATFORM_NUMBERING_NOTHING_TO_CONFIRM);

        NumberingRule voucher = rule(DocumentType.JOURNAL_VOUCHER, pending(1, AUTHOR));
        voucher.confirm(CONFIRMER, AT);
        assertThat(refusal(() -> voucher.confirm(new UserId(99), AT)).errorCode())
                .isEqualTo(NumberingError.PLATFORM_NUMBERING_RULE_TRANSITION_ILLEGAL);
    }

    @Test
    @DisplayName("D-1a-157 a voucher template also needs the finance confirmation, from a third person")
    void aVoucherTemplateNeedsTheFinanceConfirmationToo() {
        NumberingRule rule = rule(DocumentType.JOURNAL_VOUCHER, pending(1, AUTHOR));
        assertThat(rule.requiresFinanceConfirmation()).isTrue();

        rule.confirm(CONFIRMER, AT);

        assertThat(rule.inForce()).as("the business confirmation alone is not enough").isEmpty();
        assertThat(rule.drainEvents(TestEnvelopes.INSTANCE)).singleElement().isInstanceOf(NumberingRuleConfirmationGiven.class);
        assertThat(refusal(() -> rule.confirmByFinance(AUTHOR, AT)).errorCode()).isEqualTo(NumberingError.PLATFORM_NUMBERING_CONFIRMER_IS_AUTHOR);
        assertThat(refusal(() -> rule.confirmByFinance(CONFIRMER, AT)).errorCode()).isEqualTo(NumberingError.PLATFORM_NUMBERING_CONFIRMATION_REPEATED);

        rule.confirmByFinance(FINANCE, AT);

        assertThat(rule.inForce()).hasValueSatisfying(v -> {
            assertThat(v.confirmedBy()).isEqualTo(CONFIRMER);
            assertThat(v.financeConfirmedBy()).isEqualTo(FINANCE);
        });
        assertThat(rule.drainEvents(TestEnvelopes.INSTANCE)).extracting(e -> e.getClass().getSimpleName())
                .containsExactly("NumberingRuleConfirmationGiven", "NumberingRuleConfirmed");
    }

    @Test
    @DisplayName("D-1a-157 the finance confirmation may come first; the version is in force once both are there")
    void theFinanceConfirmationMayComeFirst() {
        NumberingRule rule = rule(DocumentType.JOURNAL_VOUCHER, pending(1, AUTHOR));

        rule.confirmByFinance(FINANCE, AT);
        assertThat(rule.inForce()).isEmpty();
        assertThat(refusal(() -> rule.confirm(FINANCE, AT)).errorCode()).isEqualTo(NumberingError.PLATFORM_NUMBERING_CONFIRMATION_REPEATED);
        rule.confirm(CONFIRMER, AT);

        assertThat(rule.inForce()).isPresent();
    }

    @Test
    @DisplayName("D-1a-157 only voucher templates take a finance confirmation")
    void otherTemplatesTakeNoFinanceConfirmation() {
        NumberingRule rule = rule(DocumentType.QUOTE, pending(1, AUTHOR));

        assertThat(rule.requiresFinanceConfirmation()).isFalse();
        assertThat(refusal(() -> rule.confirmByFinance(FINANCE, AT)).errorCode())
                .isEqualTo(NumberingError.PLATFORM_NUMBERING_FINANCE_CONFIRMATION_NOT_REQUIRED);
    }

    @Test
    @DisplayName("D-1a-147 a rule with no confirmed version has nothing in force: it issues no number")
    void nothingInForceUntilConfirmed() {
        NumberingRule rule = rule(DocumentType.QUOTE, pending(1, null));

        assertThat(rule.inForce()).isEmpty();
        assertThat(rule.current().number()).isEqualTo(1);
    }

    @Test
    @DisplayName("a template that cannot be saved is refused without touching the rule")
    void anInvalidProposalChangesNothing() {
        NumberingRule rule = rule(DocumentType.QUOTE, pending(1, null));

        assertThatThrownBy(() -> rule.propose(OTHER, 0, AUTHOR, AT)).isInstanceOf(IllegalArgumentException.class);
        assertThat(rule.versions()).hasSize(1);
        assertThat(rule.hasPendingEvents()).isFalse();
    }
}
