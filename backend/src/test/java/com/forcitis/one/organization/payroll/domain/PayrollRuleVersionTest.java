package com.forcitis.one.organization.payroll.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.TestIds;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorCode;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.TransitionTable;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.fixture.doubles.CatalogueBusinessDates;
import com.forcitis.one.kernel.SystemActors;
import com.forcitis.one.kernel.event.ActorType;
import com.forcitis.one.kernel.event.EventDraft;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.kernel.event.EventEnvelopes;
import com.forcitis.one.organization.payroll.api.JurisdictionKey;
import com.forcitis.one.organization.payroll.api.PayrollError;
import com.forcitis.one.organization.payroll.api.PayrollRuleKind;
import com.forcitis.one.organization.payroll.events.PayrollRuleVersionBecameEffective;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PayrollRuleVersionTest {
    static final EventEnvelopes ENVELOPES = unchecked -> {
        EventDraft draft = CatalogueBusinessDates.check(unchecked);
        return new EventEnvelope(UUID.randomUUID(), draft.eventType(), draft.schemaVersion(), Instant.EPOCH, draft.aggregateType(), draft.aggregateId(),
                draft.aggregateVersion(), UUID.randomUUID(), null, ActorType.SYSTEM, SystemActors.idOf("test.envelope"), draft.legalEntityId(),
                draft.businessDate(), null, draft.relatedAggregates());
    };
    static final LocalDate FROM = LocalDate.of(2026, 1, 1);
    static final Instant AT = Instant.parse("2026-11-05T02:00:00Z");
    static final UserId DRAFTER = new UserId(TestIds.uuid(11));
    static final UserId APPROVER = new UserId(TestIds.uuid(12));
    static final PayrollRuleRowBound ROUNDING_ROW = new PayrollRuleRowBound(PayrollRuleRowKind.ROUNDING_MODE, null, null);

    static PayrollRuleVersion version(PayrollRuleKind kind, PayrollRuleVersionStatus status) {
        boolean decided = status != PayrollRuleVersionStatus.DRAFT;
        return PayrollRuleVersion.restore(new PayrollRuleVersionRecord(TestIds.uuid(1), kind, new JurisdictionKey("CN"), FROM,
                status == PayrollRuleVersionStatus.SUPERSEDED ? FROM.plusYears(1) : null, status, DRAFTER, decided ? APPROVER : null, decided ? AT : null, 0));
    }

    static PayrollRuleRowBound band(String lower, String upper) {
        return new PayrollRuleRowBound(PayrollRuleRowKind.INCOME_TAX_BAND, new BigDecimal(lower), upper == null ? null : new BigDecimal(upper));
    }

    static PayrollRuleRowBound deduction() {
        return new PayrollRuleRowBound(PayrollRuleRowKind.INCOME_TAX_MONTHLY_BASIC_DEDUCTION, null, null);
    }

    static void assertRefused(Runnable action, ErrorCode code, ErrorKind kind) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(DomainException.class, e -> {
            assertThat(e.errorCode()).isEqualTo(code);
            assertThat(e.kind()).isEqualTo(kind);
        });
    }

    @Test
    @DisplayName("PRC-180 publish is tried from every status and leaves only DRAFT, to EFFECTIVE; supersede leaves only EFFECTIVE, to SUPERSEDED")
    void everyOperationInEveryStatus() {
        new TransitionTable<PayrollRuleVersionStatus, PayrollRuleVersion>(PayrollRuleVersionStatus.class,
                status -> version(PayrollRuleKind.ROUNDING, status), PayrollRuleVersion::status)
                .operation("publish", v -> v.publish(APPROVER, AT, List.of(ROUNDING_ROW)), PayrollRuleVersionStatus.DRAFT, PayrollRuleVersionStatus.EFFECTIVE)
                .operation("supersede", v -> v.supersede(FROM.plusDays(1)), PayrollRuleVersionStatus.EFFECTIVE, PayrollRuleVersionStatus.SUPERSEDED)
                .verify();
    }

    @Test
    @DisplayName("PRC-180 publish records one event with ids and the start date, which is also the business date; the approval is stored on the version")
    void publishRecordsOneEvent() {
        PayrollRuleVersion v = version(PayrollRuleKind.ROUNDING, PayrollRuleVersionStatus.DRAFT);

        v.publish(APPROVER, AT, List.of(ROUNDING_ROW));

        assertThat(v.approvedBy()).isEqualTo(APPROVER);
        assertThat(v.publishedAt()).isEqualTo(AT);
        List<DomainEvent> events = v.drainEvents(ENVELOPES);
        assertThat(events).singleElement().isInstanceOfSatisfying(PayrollRuleVersionBecameEffective.class, e -> {
            assertThat(e.payrollRuleVersionId().value()).isEqualTo(TestIds.uuid(1));
            assertThat(e.ruleKind()).isEqualTo(PayrollRuleKind.ROUNDING);
            assertThat(e.jurisdictionKey()).isEqualTo(new JurisdictionKey("CN"));
            assertThat(e.effectiveFromDate()).isEqualTo(FROM);
            assertThat(e.envelope().businessDate()).isEqualTo(FROM);
        });
    }

    @Test
    @DisplayName("INV-131-style four eyes: the one who drafted a version cannot approve it, and a refused publish changes nothing and records nothing")
    void drafterIsNotTheApprover() {
        PayrollRuleVersion v = version(PayrollRuleKind.ROUNDING, PayrollRuleVersionStatus.DRAFT);

        assertRefused(() -> v.publish(DRAFTER, AT, List.of(ROUNDING_ROW)), PayrollError.ORGANIZATION_PAYROLL_RULE_VERSION_SAME_PERSON, ErrorKind.UNPROCESSABLE);

        assertThat(v.status()).isEqualTo(PayrollRuleVersionStatus.DRAFT);
        assertThat(v.approvedBy()).isNull();
        assertThat(v.hasPendingEvents()).isFalse();
    }

    @Test
    @DisplayName("INV-139 a successor must start after the version it closes; the closing day is the successor's start")
    void successorStartsAfterThePredecessor() {
        PayrollRuleVersion v = version(PayrollRuleKind.ROUNDING, PayrollRuleVersionStatus.EFFECTIVE);

        assertRefused(() -> v.supersede(FROM), PayrollError.ORGANIZATION_PAYROLL_RULE_VERSION_OVERLAP, ErrorKind.CONFLICT);
        assertRefused(() -> v.supersede(FROM.minusDays(1)), PayrollError.ORGANIZATION_PAYROLL_RULE_VERSION_OVERLAP, ErrorKind.CONFLICT);
        assertThat(v.status()).isEqualTo(PayrollRuleVersionStatus.EFFECTIVE);

        v.supersede(FROM.plusDays(1));

        assertThat(v.effectiveToDate()).isEqualTo(FROM.plusDays(1));
    }

    @Test
    @DisplayName("publish check: rows of another category, a missing kind, or a single-row kind twice refuse the publish")
    void rowsBelongToTheCategoryAndAreComplete() {
        PayrollRuleVersion tax = version(PayrollRuleKind.INCOME_TAX, PayrollRuleVersionStatus.DRAFT);
        PayrollRuleRowBound open = band("0", null);

        assertRefused(() -> tax.publish(APPROVER, AT, List.of(open, deduction(), ROUNDING_ROW)), PayrollError.ORGANIZATION_PAYROLL_RULE_VERSION_INCOMPLETE, ErrorKind.UNPROCESSABLE);
        assertRefused(() -> tax.publish(APPROVER, AT, List.of(open)), PayrollError.ORGANIZATION_PAYROLL_RULE_VERSION_INCOMPLETE, ErrorKind.UNPROCESSABLE);
        assertRefused(() -> tax.publish(APPROVER, AT, List.of(deduction())), PayrollError.ORGANIZATION_PAYROLL_RULE_VERSION_INCOMPLETE, ErrorKind.UNPROCESSABLE);
        assertRefused(() -> tax.publish(APPROVER, AT, List.of(open, deduction(), deduction())), PayrollError.ORGANIZATION_PAYROLL_RULE_VERSION_INCOMPLETE,
                ErrorKind.UNPROCESSABLE);
        assertRefused(() -> version(PayrollRuleKind.ROUNDING, PayrollRuleVersionStatus.DRAFT).publish(APPROVER, AT, List.of()),
                PayrollError.ORGANIZATION_PAYROLL_RULE_VERSION_INCOMPLETE, ErrorKind.UNPROCESSABLE);
        assertThat(tax.status()).isEqualTo(PayrollRuleVersionStatus.DRAFT);

        tax.publish(APPROVER, AT, List.of(open, deduction()));

        assertThat(tax.status()).isEqualTo(PayrollRuleVersionStatus.EFFECTIVE);
    }

    @Test
    @DisplayName("publish check: social insurance and housing fund need both a rate and a base limit")
    void socialInsuranceAndHousingFundNeedRateAndBaseLimit() {
        for (PayrollRuleKind kind : List.of(PayrollRuleKind.SOCIAL_INSURANCE, PayrollRuleKind.HOUSING_FUND)) {
            PayrollRuleRowKind rate = PayrollRuleRowKind.valueOf(kind + "_RATE");
            PayrollRuleRowKind limit = PayrollRuleRowKind.valueOf(kind + "_BASE_LIMIT");
            PayrollRuleRowBound rateRow = new PayrollRuleRowBound(rate, null, null);
            PayrollRuleRowBound limitRow = new PayrollRuleRowBound(limit, new BigDecimal("1"), new BigDecimal("2"));

            assertRefused(() -> version(kind, PayrollRuleVersionStatus.DRAFT).publish(APPROVER, AT, List.of(rateRow)),
                    PayrollError.ORGANIZATION_PAYROLL_RULE_VERSION_INCOMPLETE, ErrorKind.UNPROCESSABLE);
            assertRefused(() -> version(kind, PayrollRuleVersionStatus.DRAFT).publish(APPROVER, AT, List.of(limitRow)),
                    PayrollError.ORGANIZATION_PAYROLL_RULE_VERSION_INCOMPLETE, ErrorKind.UNPROCESSABLE);
            PayrollRuleVersion ok = version(kind, PayrollRuleVersionStatus.DRAFT);
            ok.publish(APPROVER, AT, List.of(rateRow, rateRow, limitRow));
            assertThat(ok.status()).isEqualTo(PayrollRuleVersionStatus.EFFECTIVE);
        }
    }

    @Test
    @DisplayName("publish check: income tax bands start at zero, each starts where the one before ends, the top band has no end")
    void bandsFormAChainFromZeroToOpen() {
        List<List<PayrollRuleRowBound>> broken = List.of(
                List.of(band("1", null)),
                List.of(band("0", "100"), band("101", null)),
                List.of(band("0", "100"), band("90", null)),
                List.of(band("0", "100"), band("100", "200")),
                List.of(band("0", null), band("100", null)),
                List.of(band("0", "100")));
        for (List<PayrollRuleRowBound> bands : broken) {
            List<PayrollRuleRowBound> rows = new java.util.ArrayList<>(bands);
            rows.add(deduction());
            assertRefused(() -> version(PayrollRuleKind.INCOME_TAX, PayrollRuleVersionStatus.DRAFT).publish(APPROVER, AT, rows),
                    PayrollError.ORGANIZATION_PAYROLL_RULE_VERSION_INCOMPLETE, ErrorKind.UNPROCESSABLE);
        }

        PayrollRuleVersion chain = version(PayrollRuleKind.INCOME_TAX, PayrollRuleVersionStatus.DRAFT);
        chain.publish(APPROVER, AT, List.of(band("100.00", "200"), band("0.00", "100"), band("200", null), deduction()));

        assertThat(chain.status()).isEqualTo(PayrollRuleVersionStatus.EFFECTIVE);
    }
}
