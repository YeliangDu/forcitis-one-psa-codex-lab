package com.forcitis.one.platform.numbering.domain;

import static com.forcitis.one.platform.numbering.domain.VersionStatus.CONFIRMED;
import static com.forcitis.one.platform.numbering.domain.VersionStatus.PENDING_CONFIRMATION;
import static com.forcitis.one.platform.numbering.domain.VersionStatus.SUPERSEDED;

import com.forcitis.one.TestIds;
import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.kernel.TransitionTable;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.numbering.api.NumberingRuleId;
import com.forcitis.one.platform.numbering.api.NumberingRuleVersionId;
import com.forcitis.one.platform.numbering.api.ResetPolicy;
import com.forcitis.one.platform.numbering.api.RuleVersion;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The machine moves the versions inside a rule: the rule here holds one version, the one whose status is watched. */
class NumberingRuleTransitionTableTest {
    static final NumberingPattern TEMPLATE = new NumberingPattern(NumberingPattern.DEFAULT_TEMPLATE, ResetPolicy.MONTHLY);
    static final LocalDate DAY = LocalDate.parse("2026-10-15");
    static final Instant AT = Instant.parse("2026-10-14T08:00:00Z");
    static final UserId AUTHOR = TestAccounts.account(11);
    static final UserId CONFIRMER = TestAccounts.account(12);

    static NumberingRule at(VersionStatus status) {
        boolean confirmed = status == CONFIRMED;
        TemplateVersion only = new TemplateVersion(new NumberingRuleVersionId(TestIds.uuid(41)), 1, TEMPLATE, 1, DAY, AUTHOR, AT, status,
                confirmed ? CONFIRMER : null, confirmed ? AT : null, null, null);
        return NumberingRule.restore(new NumberingRuleId(TestIds.uuid(4)), SeededDocumentTypes.of("QUOTE"), List.of(only), 4, 4);
    }

    @Test
    @DisplayName("confirm and propose are tried from every version status: confirm leaves only PENDING_CONFIRMATION, propose supersedes a pending version and leaves the others")
    void exhaustiveTransitions() {
        new TransitionTable<VersionStatus, NumberingRule>(VersionStatus.class, NumberingRuleTransitionTableTest::at, r -> r.versions().getFirst().status())
                .operation("confirm", r -> r.confirm(CONFIRMER, AT, DAY, new RuleVersion(1)), PENDING_CONFIRMATION, CONFIRMED)
                .operation("propose", r -> r.propose(new NumberingRuleVersionId(TestIds.uuid(42)), TEMPLATE, 1, DAY, AUTHOR, AT),
                        PENDING_CONFIRMATION, SUPERSEDED, CONFIRMED, CONFIRMED, SUPERSEDED, SUPERSEDED)
                .verify();
    }
}
