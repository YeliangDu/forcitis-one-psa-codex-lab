package com.forcitis.one.organization.workforce.domain;

import static com.forcitis.one.organization.workforce.domain.Fixtures.ROOT;
import static com.forcitis.one.organization.workforce.domain.Fixtures.apply;
import static com.forcitis.one.organization.workforce.domain.Fixtures.first;
import static com.forcitis.one.organization.workforce.domain.Fixtures.member;
import static com.forcitis.one.organization.workforce.domain.Fixtures.post;
import static com.forcitis.one.organization.workforce.domain.Fixtures.source;
import static com.forcitis.one.organization.workforce.domain.Fixtures.unit;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The shrink guard of RULE-358 (org units) and RULE-366 (people), both with a 20 % maximum drop. */
class ShrinkGuardRulesTest {

    private static RunStats stats(int incomingUnits, int previousUnits, int incomingPeople, int previousPeople) {
        return new RunStats(incomingUnits, incomingPeople, incomingUnits, incomingPeople, previousUnits, previousPeople, 0, 0, 0, 0, 0, 0);
    }

    @Test
    @DisplayName("RULE-358 300 active units and A8 returns 230: the run is held; at 240 it proceeds")
    void orgUnitShrinkIsHeldBelowEightyPercent() {
        assertThat(ShrinkGuard.evaluate(stats(230, 300, 100, 100), 20).held()).isTrue();
        assertThat(ShrinkGuard.evaluate(stats(240, 300, 100, 100), 20).held()).isFalse();
        assertThat(ShrinkGuard.evaluate(stats(239, 300, 100, 100), 20).held()).isTrue();
    }

    @Test
    @DisplayName("RULE-366 people use the same guard: a drop of more than 20 % in employed people holds the run")
    void peopleShrinkIsHeldToo() {
        ShrinkGuard.Verdict verdict = ShrinkGuard.evaluate(stats(300, 300, 1000, 1300), 20);

        assertThat(verdict.held()).isTrue();
        assertThat(verdict.reason()).contains("employed people 1300 -> 1000");
        assertThat(ShrinkGuard.evaluate(stats(300, 300, 1040, 1300), 20).held()).isFalse();
    }

    @Test
    @DisplayName("RULE-358 the first run (empty snapshot) is never held; growth is never held")
    void firstRunAndGrowthAreNotHeld() {
        assertThat(ShrinkGuard.evaluate(stats(5, 0, 5, 0), 20).held()).isFalse();
        assertThat(ShrinkGuard.evaluate(stats(400, 300, 2000, 1300), 20).held()).isFalse();
    }

    @Test
    @DisplayName("RULE-366 the figures come from the reconciliation: mass absence is visible before anything is written")
    void reconciliationFeedsTheGuard() {
        List<SourceMember> everyone = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            everyone.add(member(i, 1, 100L, null));
        }
        SourceSnapshot full = source(List.of(unit(1, ROOT)), List.of(), List.of(post(100)), everyone);
        OrgSnapshot stored = apply(OrgSnapshot.empty(), first(full));

        Reconciliation shrunk = SnapshotReconciler.reconcile(stored,
                source(List.of(unit(1, ROOT)), List.of(), List.of(post(100)), everyone.subList(0, 7)));

        assertThat(shrunk.stats().previousEmployedCount()).isEqualTo(10);
        assertThat(shrunk.stats().incomingEmployedCount()).isEqualTo(7);
        assertThat(ShrinkGuard.evaluate(shrunk.stats(), 20).held()).isTrue();
        assertThat(shrunk.separatedEmployees()).hasSize(3);
    }
}
