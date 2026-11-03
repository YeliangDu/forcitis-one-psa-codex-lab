package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.platform.integration.api.SourceOrgSnapshot;
import com.forcitis.one.platform.integration.api.SourcePerson;

import static com.forcitis.one.organization.workforce.domain.Fixtures.apply;
import static com.forcitis.one.organization.workforce.domain.Fixtures.first;
import static com.forcitis.one.organization.workforce.domain.Fixtures.person;
import static com.forcitis.one.organization.workforce.domain.Fixtures.post;
import static com.forcitis.one.organization.workforce.domain.Fixtures.source;
import static com.forcitis.one.organization.workforce.domain.Fixtures.unit;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReconciliationCountsTest {
    @Test
    @DisplayName("RULE-366 the reconciliation counts the people before and after: mass absence is visible before anything is written")
    void aShrinkIsCountedAndSeparatesTheAbsent() {
        List<SourcePerson> everyone = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            everyone.add(person(i, 1, 100L, null));
        }
        SourceOrgSnapshot full = source(List.of(unit(1, null)), List.of(), List.of(post(100)), everyone);
        OrgSnapshot stored = apply(OrgSnapshot.empty(), first(full));

        Reconciliation shrunk = SnapshotReconciler.reconcile(stored,
                source(List.of(unit(1, null)), List.of(), List.of(post(100)), everyone.subList(0, 7)));

        assertThat(shrunk.stats().previousEmployedCount()).isEqualTo(10);
        assertThat(shrunk.stats().incomingEmployedCount()).isEqualTo(7);
        assertThat(shrunk.separatedEmployees()).hasSize(3);
    }
}
