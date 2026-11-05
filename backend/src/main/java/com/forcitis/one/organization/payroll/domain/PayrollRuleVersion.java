package com.forcitis.one.organization.payroll.domain;

import com.forcitis.one.kernel.AggregateRoot;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.EventDraft;
import com.forcitis.one.organization.payroll.api.JurisdictionKey;
import com.forcitis.one.organization.payroll.api.PayrollError;
import com.forcitis.one.organization.payroll.api.PayrollRuleKind;
import com.forcitis.one.organization.payroll.api.PayrollRuleVersionId;
import com.forcitis.one.organization.payroll.events.PayrollRuleVersionBecameEffective;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * One version of the rules of a category in a jurisdiction, with the rows it holds. DRAFT is edited elsewhere; publishing makes it EFFECTIVE and
 * freezes it; the next version closes it (SUPERSEDED). The database repeats the legal transitions as a trigger and the no-overlap rule (INV-139) as an exclusion.
 */
public final class PayrollRuleVersion extends AggregateRoot {
    private static final Set<PayrollRuleRowKind> REQUIRED_ROW_KINDS_OF_INCOME_TAX = EnumSet.of(PayrollRuleRowKind.INCOME_TAX_BAND,
            PayrollRuleRowKind.INCOME_TAX_MONTHLY_BASIC_DEDUCTION);

    private final UUID id;
    private final PayrollRuleKind ruleKind;
    private final JurisdictionKey jurisdictionKey;
    private final LocalDate effectiveFromDate;
    private @Nullable LocalDate effectiveToDate;
    private PayrollRuleVersionStatus status;
    private final UserId draftedBy;
    private @Nullable UserId approvedBy;
    private @Nullable Instant publishedAt;

    private PayrollRuleVersion(PayrollRuleVersionRecord stored) {
        super(stored.version(), 0);
        this.id = Objects.requireNonNull(stored.id(), "id");
        this.ruleKind = stored.ruleKind();
        this.jurisdictionKey = stored.jurisdictionKey();
        this.effectiveFromDate = stored.effectiveFromDate();
        this.effectiveToDate = stored.effectiveToDate();
        this.status = stored.status();
        this.draftedBy = stored.draftedBy();
        this.approvedBy = stored.approvedBy();
        this.publishedAt = stored.publishedAt();
    }

    public static PayrollRuleVersion restore(PayrollRuleVersionRecord stored) {
        return new PayrollRuleVersion(stored);
    }

    /**
     * DRAFT to EFFECTIVE. The approver is not the drafter; the rows are all of this category, hold every kind the category needs, and an income tax
     * table is a chain of bands from zero with an open top band.
     */
    public void publish(UserId approver, Instant at, List<PayrollRuleRowBound> rows) {
        if (status != PayrollRuleVersionStatus.DRAFT) {
            throw conflict("only a draft can be published, this version is " + status);
        }
        if (approver.equals(draftedBy)) {
            throw new DomainException(PayrollError.ORGANIZATION_PAYROLL_RULE_VERSION_SAME_PERSON, ErrorKind.UNPROCESSABLE,
                    "the approver of a rule version is not the one who drafted it");
        }
        requireCompleteRows(rows);
        this.status = PayrollRuleVersionStatus.EFFECTIVE;
        this.approvedBy = approver;
        this.publishedAt = at;
        record((envelopes, sequence) -> new PayrollRuleVersionBecameEffective(envelopes.wrap(new EventDraft("organization.payroll.PayrollRuleVersionBecameEffective", 1,
                "PayrollRuleVersion", id, sequence, null, effectiveFromDate, List.of())), new PayrollRuleVersionId(id), ruleKind, jurisdictionKey,
                effectiveFromDate));
    }

    /** EFFECTIVE to SUPERSEDED: the successor's first day is the day this version ends, so the two never share a day. */
    public void supersede(LocalDate successorFrom) {
        if (status != PayrollRuleVersionStatus.EFFECTIVE || effectiveToDate != null) {
            throw conflict("only the open effective version can be superseded, this version is " + status);
        }
        if (!successorFrom.isAfter(effectiveFromDate)) {
            throw new DomainException(PayrollError.ORGANIZATION_PAYROLL_RULE_VERSION_OVERLAP, ErrorKind.CONFLICT,
                    "a successor starts after " + effectiveFromDate + ", the start of the version in force");
        }
        this.status = PayrollRuleVersionStatus.SUPERSEDED;
        this.effectiveToDate = successorFrom;
    }

    private void requireCompleteRows(List<PayrollRuleRowBound> rows) {
        List<PayrollRuleRowKind> kinds = rows.stream().map(PayrollRuleRowBound::kind).toList();
        if (kinds.stream().anyMatch(kind -> kind.ruleKind() != ruleKind)) {
            throw incomplete("a " + ruleKind + " version holds only rows of its own category");
        }
        for (PayrollRuleRowKind required : requiredKinds()) {
            long count = kinds.stream().filter(required::equals).count();
            if (count == 0 || count > 1 && required.single()) {
                throw incomplete("a " + ruleKind + " version needs " + (required.single() ? "exactly one " : "at least one ") + required + " row");
            }
        }
        if (ruleKind == PayrollRuleKind.INCOME_TAX) {
            requireBandChain(rows.stream().filter(r -> r.kind() == PayrollRuleRowKind.INCOME_TAX_BAND).toList());
        }
    }

    private Set<PayrollRuleRowKind> requiredKinds() {
        return switch (ruleKind) {
            case INCOME_TAX -> REQUIRED_ROW_KINDS_OF_INCOME_TAX;
            case SOCIAL_INSURANCE -> EnumSet.of(PayrollRuleRowKind.SOCIAL_INSURANCE_RATE, PayrollRuleRowKind.SOCIAL_INSURANCE_BASE_LIMIT);
            case HOUSING_FUND -> EnumSet.of(PayrollRuleRowKind.HOUSING_FUND_RATE, PayrollRuleRowKind.HOUSING_FUND_BASE_LIMIT);
            case ROUNDING -> EnumSet.of(PayrollRuleRowKind.ROUNDING_MODE);
        };
    }

    /** The bands cover every income exactly once: the first starts at zero, each starts where the one before ends, the last has no end. */
    private static void requireBandChain(List<PayrollRuleRowBound> bands) {
        List<PayrollRuleRowBound> ordered = new ArrayList<>(bands);
        ordered.sort(Comparator.comparing(b -> Objects.requireNonNull(b.lowerBound())));
        if (Objects.requireNonNull(ordered.get(0).lowerBound()).signum() != 0) {
            throw incomplete("the first income tax band starts at zero");
        }
        for (int i = 0; i < ordered.size(); i++) {
            var upper = ordered.get(i).upperBound();
            boolean last = i == ordered.size() - 1;
            if (last ? upper != null : upper == null || upper.compareTo(Objects.requireNonNull(ordered.get(i + 1).lowerBound())) != 0) {
                throw incomplete(last ? "the top income tax band is open" : "each income tax band ends where the next one starts");
            }
        }
    }

    private static DomainException incomplete(String message) {
        return new DomainException(PayrollError.ORGANIZATION_PAYROLL_RULE_VERSION_INCOMPLETE, ErrorKind.UNPROCESSABLE, message);
    }

    private static DomainException conflict(String message) {
        return new DomainException(PayrollError.ORGANIZATION_PAYROLL_RULE_VERSION_STATE_CONFLICT, ErrorKind.CONFLICT, message);
    }

    public UUID id() {
        return id;
    }

    public PayrollRuleKind ruleKind() {
        return ruleKind;
    }

    public JurisdictionKey jurisdictionKey() {
        return jurisdictionKey;
    }

    public LocalDate effectiveFromDate() {
        return effectiveFromDate;
    }

    public @Nullable LocalDate effectiveToDate() {
        return effectiveToDate;
    }

    public PayrollRuleVersionStatus status() {
        return status;
    }

    public UserId draftedBy() {
        return draftedBy;
    }

    public @Nullable UserId approvedBy() {
        return approvedBy;
    }

    public @Nullable Instant publishedAt() {
        return publishedAt;
    }
}
