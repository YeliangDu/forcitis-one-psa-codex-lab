package com.forcitis.one.organization.workforce.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.TestIds;
import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.fixture.doubles.TestOrgIds;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.TestEnvelopes;
import com.forcitis.one.organization.workforce.api.AttributionSubject;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.events.LegalEntityAttributed;
import com.forcitis.one.organization.workforce.events.LegalEntityAttributionEnded;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.forcitis.one.organization.workforce.api.AttributionId;

class LegalEntityAttributionTest {
    static final AttributionId ATTRIBUTION_ID = new AttributionId(TestIds.uuid(5));
    static final LegalEntityId LE1 = new LegalEntityId(TestIds.uuid(1));
    static final LegalEntityId LE2 = new LegalEntityId(TestIds.uuid(2));
    static final LocalDate JAN = LocalDate.of(2026, 1, 1);
    static final LocalDate JUL = LocalDate.of(2026, 7, 1);

    static LegalEntityAttribution unit() {
        return LegalEntityAttribution.empty(ATTRIBUTION_ID, AttributionSubject.of(TestOrgIds.orgUnit(3)));
    }

    @Test
    @DisplayName("ADR-0026 a new attribution closes the open period on its first day, so the periods never overlap")
    void attributingClosesTheOpenPeriod() {
        LegalEntityAttribution attribution = unit();
        attribution.attribute(LE1, JAN);
        attribution.attribute(LE2, JUL);

        assertThat(attribution.periods()).containsExactly(new AttributionPeriod(LE1, JAN, JUL), new AttributionPeriod(LE2, JUL, null));
        assertThat(attribution.on(LocalDate.of(2026, 6, 30)).orElseThrow().legalEntityId()).isEqualTo(LE1);
        assertThat(attribution.on(JUL).orElseThrow().legalEntityId()).isEqualTo(LE2);
        assertThat(attribution.on(LocalDate.of(2025, 12, 31))).isEmpty();
    }

    @Test
    @DisplayName("each attribution and each ending announces its own event, numbered after the stored count, with the legal entity and the day")
    void events() {
        LegalEntityAttribution attribution = LegalEntityAttribution.restore(ATTRIBUTION_ID, AttributionSubject.of(TestAccounts.employee(42)),
                List.of(), 2, 2);
        attribution.attribute(LE1, JAN);
        attribution.end(JUL);

        List<DomainEvent> events = attribution.drainEvents(TestEnvelopes.INSTANCE);

        assertThat(events).extracting(e -> e.envelope().aggregateVersion()).containsExactly(3L, 4L);
        assertThat(events.get(0)).isInstanceOfSatisfying(LegalEntityAttributed.class, e -> {
            assertThat(e.effectiveFrom()).isEqualTo(JAN);
            assertThat(e.envelope().legalEntityId()).isEqualTo(LE1);
            assertThat(e.envelope().businessDate()).isEqualTo(JAN);
            assertThat(e.envelope().aggregateId()).isEqualTo(ATTRIBUTION_ID.value());
        });
        assertThat(events.get(1)).isInstanceOfSatisfying(LegalEntityAttributionEnded.class, e -> {
            assertThat(e.effectiveTo()).isEqualTo(JUL);
            assertThat(e.envelope().businessDate()).isEqualTo(JUL);
        });
    }

    // RULE-503
    @Test
    @DisplayName("asking for the legal entity it already has changes nothing and records nothing")
    void sameEntityIsANoOp() {
        LegalEntityAttribution attribution = unit();
        attribution.attribute(LE1, JAN);
        attribution.drainEvents(TestEnvelopes.INSTANCE);

        attribution.attribute(LE1, JUL);

        assertThat(attribution.hasPendingEvents()).isFalse();
        assertThat(attribution.periods()).hasSize(1);
    }

    @Test
    @DisplayName("history is not rewritten: a new attribution must start after the latest period began; ending needs an open period and a later day")
    void historyIsNotRewritten() {
        LegalEntityAttribution attribution = unit();
        attribution.attribute(LE1, JUL);

        assertThatThrownBy(() -> attribution.attribute(LE2, JAN)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.errorCode()).isEqualTo(WorkforceError.ORGANIZATION_WORKFORCE_ATTRIBUTION_DATE_INVALID));
        assertThatThrownBy(() -> attribution.attribute(LE2, JUL)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> attribution.end(JUL)).isInstanceOf(DomainException.class);
        attribution.end(LocalDate.of(2026, 8, 1));
        assertThatThrownBy(() -> attribution.end(LocalDate.of(2026, 9, 1))).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.errorCode()).isEqualTo(WorkforceError.ORGANIZATION_WORKFORCE_ATTRIBUTION_NOT_OPEN));
    }

    static final class Sources implements LegalEntityResolutionSources {
        final Map<EmployeeId, LegalEntityId> employees = new HashMap<>();
        final Map<OrgUnitId, LegalEntityId> units = new HashMap<>();
        final Map<OrgUnitId, OrgUnitId> parents = new HashMap<>();
        final Map<EmployeeId, OrgUnitId> assigned = new HashMap<>();

        @Override
        public Optional<LegalEntityId> employeeAttribution(EmployeeId employee, LocalDate date) {
            return Optional.ofNullable(employees.get(employee));
        }

        @Override
        public Optional<OrgUnitId> assignedUnit(EmployeeId employee, LocalDate date) {
            return Optional.ofNullable(assigned.get(employee));
        }

        @Override
        public Optional<LegalEntityId> orgUnitAttribution(OrgUnitId orgUnit, LocalDate date) {
            return Optional.ofNullable(units.get(orgUnit));
        }

        @Override
        public Optional<OrgUnitId> parentUnit(OrgUnitId orgUnit, LocalDate date) {
            return Optional.ofNullable(parents.get(orgUnit));
        }
    }

    // RULE-504
    @Test
    @DisplayName("ADR-0026 resolution: the employee's own attribution wins; else the nearest department up the chain; a cycle or nothing resolves to empty")
    void resolution() {
        Sources sources = new Sources();
        EmployeeId employee = TestAccounts.employee(7);
        OrgUnitId leaf = TestOrgIds.orgUnit(3);
        OrgUnitId middle = TestOrgIds.orgUnit(2);
        OrgUnitId root = TestOrgIds.orgUnit(1);
        sources.assigned.put(employee, leaf);
        sources.parents.put(leaf, middle);
        sources.parents.put(middle, root);

        assertThat(LegalEntityResolution.resolve(sources, employee, JAN)).as("nothing attributed anywhere").isEmpty();

        sources.units.put(root, LE1);
        assertThat(LegalEntityResolution.resolve(sources, employee, JAN)).contains(LE1);

        sources.units.put(middle, LE2);
        assertThat(LegalEntityResolution.resolve(sources, employee, JAN)).as("a subtree overrides its ancestor").contains(LE2);

        sources.employees.put(employee, LE1);
        assertThat(LegalEntityResolution.resolve(sources, employee, JAN)).as("the employee's own attribution wins").contains(LE1);

        Sources cyclic = new Sources();
        cyclic.assigned.put(employee, leaf);
        cyclic.parents.put(leaf, middle);
        cyclic.parents.put(middle, leaf);
        assertThat(LegalEntityResolution.resolve(cyclic, employee, JAN)).as("a broken tree does not loop").isEmpty();

        assertThat(LegalEntityResolution.resolve(new Sources(), TestAccounts.employee(8), JAN)).as("an unknown employee").isEmpty();
    }

    @Test
    @DisplayName("RULE-385 correcting a closed period moves its end, records one pending correction and one event; the same end again changes nothing")
    void correctingAClosedPeriod() {
        LegalEntityAttribution attribution = unit();
        attribution.attribute(LE1, JAN);
        attribution.attribute(LE2, JUL);
        attribution.drainEvents(TestEnvelopes.INSTANCE);
        LocalDate earlier = LocalDate.of(2026, 5, 1);

        attribution.correct(JAN, earlier, new com.forcitis.one.kernel.Reason("typo"), TestAccounts.account(7));
        attribution.correct(JAN, earlier, new com.forcitis.one.kernel.Reason("typo"), TestAccounts.account(7));

        assertThat(attribution.periods()).containsExactly(new AttributionPeriod(LE1, JAN, earlier), new AttributionPeriod(LE2, JUL, null));
        assertThat(attribution.pendingCorrections()).singleElement().satisfies(c -> {
            assertThat(c.previousEffectiveTo()).isEqualTo(JUL);
            assertThat(c.newEffectiveTo()).isEqualTo(earlier);
            assertThat(c.correctedBy()).isEqualTo(TestAccounts.account(7));
        });
        assertThat(attribution.drainEvents(TestEnvelopes.INSTANCE)).singleElement()
                .isInstanceOfSatisfying(com.forcitis.one.organization.workforce.events.LegalEntityAttributionCorrected.class, e -> {
                    assertThat(e.previousEffectiveTo()).isEqualTo(JUL);
                    assertThat(e.newEffectiveTo()).isEqualTo(earlier);
                    assertThat(e.reason().value()).isEqualTo("typo");
                });
    }

    @Test
    @DisplayName("RULE-385 an open period, an unknown period, an end that overlaps the next period and an end before the start are refused")
    void correctionRefusals() {
        LegalEntityAttribution attribution = unit();
        attribution.attribute(LE1, JAN);
        attribution.attribute(LE2, JUL);
        var reason = new com.forcitis.one.kernel.Reason("typo");

        assertThatThrownBy(() -> attribution.correct(JUL, LocalDate.of(2026, 9, 1), reason, TestAccounts.account(7)))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(WorkforceError.ORGANIZATION_WORKFORCE_ATTRIBUTION_PERIOD_NOT_CLOSED));
        assertThatThrownBy(() -> attribution.correct(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1), reason, TestAccounts.account(7)))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(WorkforceError.ORGANIZATION_WORKFORCE_ATTRIBUTION_PERIOD_NOT_FOUND));
        assertThatThrownBy(() -> attribution.correct(JAN, LocalDate.of(2026, 8, 1), reason, TestAccounts.account(7)))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(WorkforceError.ORGANIZATION_WORKFORCE_ATTRIBUTION_CORRECTION_OVERLAP));
        assertThatThrownBy(() -> attribution.correct(JAN, JAN, reason, TestAccounts.account(7)))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(WorkforceError.ORGANIZATION_WORKFORCE_ATTRIBUTION_DATE_INVALID));
        assertThat(attribution.pendingCorrections()).isEmpty();
    }
}
