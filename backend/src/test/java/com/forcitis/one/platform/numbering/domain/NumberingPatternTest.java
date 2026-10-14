package com.forcitis.one.platform.numbering.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.organization.workforce.api.LegalEntityCode;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.DocumentType;
import com.forcitis.one.platform.numbering.api.ResetPolicy;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NumberingPatternTest {

    static final LocalDate DAY = LocalDate.of(2026, 10, 14);
    static final LegalEntityCode FST = new LegalEntityCode("FST");

    @Test
    @DisplayName("ADR-0010 decision 5: the default template renders {type code}-{LE}-{yyyyMM}-{seq:4}")
    void defaultTemplateRendersTheUnifiedFormat() {
        NumberingPattern pattern = new NumberingPattern(NumberingPattern.DEFAULT_TEMPLATE, ResetPolicy.MONTHLY);

        assertThat(pattern.usesTypeCode()).isTrue();
        assertThat(pattern.render("QT", FST, DAY, 1)).isEqualTo("QT-FST-202610-0001");
        assertThat(pattern.render("QT", FST, DAY, 9999)).isEqualTo("QT-FST-202610-9999");
        assertThat(pattern.capacity()).isEqualTo(9999);
    }

    @Test
    void sequenceOverflowIsRefusedInsteadOfTruncated() {
        NumberingPattern pattern = new NumberingPattern(NumberingPattern.DEFAULT_TEMPLATE, ResetPolicy.MONTHLY);

        assertThatThrownBy(() -> pattern.render("QT", FST, DAY, 10_000)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theDefaultTemplateOnlyResetsMonthlyOrFinerBecauseItNamesYearAndMonth() {
        assertThatThrownBy(() -> new NumberingPattern(NumberingPattern.DEFAULT_TEMPLATE, ResetPolicy.DAILY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new NumberingPattern(NumberingPattern.DEFAULT_TEMPLATE, ResetPolicy.YEARLY).value())
                .isEqualTo(NumberingPattern.DEFAULT_TEMPLATE);
    }

    @Test
    void renderingWithoutATypeCodeIsRefusedWhenTheTemplateNeedsOne() {
        NumberingPattern pattern = new NumberingPattern(NumberingPattern.DEFAULT_TEMPLATE, ResetPolicy.MONTHLY);

        assertThatThrownBy(() -> pattern.render(null, FST, DAY, 1)).isInstanceOf(IllegalStateException.class);
        assertThat(new NumberingPattern("JV-{LE}-{yyyyMM}-{seq:6}", ResetPolicy.MONTHLY).usesTypeCode()).isFalse();
    }

    @Test
    @DisplayName("a template with {LE} cannot be rendered without a legal entity code; one without {LE} needs none")
    void theLegalEntityCodeIsRequiredOnlyWhenTheTemplateUsesIt() {
        NumberingPattern withLegalEntity = new NumberingPattern("JV-{LE}-{yyyyMM}-{seq:6}", ResetPolicy.MONTHLY);
        NumberingPattern without = new NumberingPattern("{TYPE}-{yyyyMMdd}-{seq:4}", ResetPolicy.DAILY);

        assertThat(withLegalEntity.usesLegalEntityCode()).isTrue();
        assertThat(withLegalEntity.render(null, FST, DAY, 7)).isEqualTo("JV-FST-202610-000007");
        assertThatThrownBy(() -> withLegalEntity.render(null, null, DAY, 7)).isInstanceOf(IllegalStateException.class);
        assertThat(without.usesLegalEntityCode()).isFalse();
        assertThat(without.render("TS", null, DAY, 7)).isEqualTo("TS-20261014-0007");
    }

    @Test
    void aRuleWhoseTemplateUsesTheTypeCodeIsAcceptedForARegisteredCode() {
        NumberingPattern pattern = new NumberingPattern(NumberingPattern.DEFAULT_TEMPLATE, ResetPolicy.MONTHLY);

        Instant at = Instant.parse("2026-10-14T08:00:00Z");
        TemplateVersion confirmed = new TemplateVersion(1, pattern, 1, LocalDate.of(2026, 10, 15), null, at, VersionStatus.CONFIRMED, new UserId(2), at, null, null);
        TemplateVersion waiting = new TemplateVersion(1, pattern, 1, LocalDate.of(2026, 10, 15), null, at, VersionStatus.PENDING_CONFIRMATION, null, null, null, null);
        assertThat(NumberingRule.restore(DocumentType.TIMESHEET, List.of(confirmed), 0, 0).inForce()).isPresent();
        assertThat(NumberingRule.restore(DocumentType.QUOTE, List.of(waiting), 0, 0).inForce()).isEmpty();
    }

    @Test
    @DisplayName("ADR-0027 5.1 the placeholder set is exactly {TYPE} {LE} {yyyy} {yy} {MM} {dd} {yyyyMM} {yyyyMMdd} {yyMMdd} and {seq:N} with N from 1 to 12")
    void thePlaceholderSetIsExactlyTheDocumentedOne() {
        for (String token : new String[] {"TYPE", "LE", "yyyy", "yy", "MM", "dd", "yyyyMM", "yyyyMMdd", "yyMMdd"}) {
            String template = "{" + token + "}-{seq:4}";
            ResetPolicy reset = ResetPolicy.NEVER;
            assertThat(new NumberingPattern(template, reset).value()).as(token).isEqualTo(template);
        }
        for (int width = 1; width <= 12; width++) {
            assertThat(new NumberingPattern("{seq:" + width + "}", ResetPolicy.NEVER).capacity()).isEqualTo((long) Math.pow(10, width) - 1);
        }
        for (String token : new String[] {"seq:0", "seq:13", "seq", "yyyyM", "MMdd", "type", "le", "YYYY", "HH", ""}) {
            assertThatThrownBy(() -> new NumberingPattern("{" + token + "}-{seq:4}", ResetPolicy.NEVER)).as(token).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void stillRejectsUnknownTokensAndMissingSequences() {
        assertThatThrownBy(() -> new NumberingPattern("{TYPO}-{seq:4}", ResetPolicy.NEVER)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NumberingPattern("{TYPE}-{yyyyMM}", ResetPolicy.MONTHLY)).isInstanceOf(IllegalArgumentException.class);
    }
}
