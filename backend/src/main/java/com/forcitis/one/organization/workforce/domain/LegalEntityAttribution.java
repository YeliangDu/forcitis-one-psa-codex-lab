package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.AggregateRoot;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.event.EventDraft;
import com.forcitis.one.kernel.event.EventSource;
import com.forcitis.one.kernel.event.SourceSystem;
import com.forcitis.one.organization.workforce.api.AttributionSubject;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.events.LegalEntityAttributed;
import com.forcitis.one.organization.workforce.events.LegalEntityAttributionEnded;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Which legal entity a department or an employee belongs to, period by period. Periods never overlap; history is only closed,
 * never rewritten: a new attribution starts after the start of the latest period.
 */
public final class LegalEntityAttribution extends AggregateRoot {
    private final Long storedRow;
    private final AttributionSubject subject;
    private final int storedPeriodCount;
    private List<AttributionPeriod> periods;

    private LegalEntityAttribution(Long storedRow, AttributionSubject subject, List<AttributionPeriod> periods, long version,
                                   long eventCount) {
        super(version, eventCount);
        this.storedRow = storedRow;
        this.subject = Objects.requireNonNull(subject, "subject");
        this.periods = List.copyOf(periods);
        this.storedPeriodCount = this.periods.size();
    }

    public static LegalEntityAttribution empty(AttributionSubject subject) {
        return new LegalEntityAttribution(null, subject, List.of(), 0, 0);
    }

    public static LegalEntityAttribution restore(long storedRow, AttributionSubject subject, List<AttributionPeriod> periods, long version,
                                                 long eventCount) {
        return new LegalEntityAttribution(storedRow, subject, periods.stream().sorted(java.util.Comparator.comparing(AttributionPeriod::effectiveFrom)).toList(),
                version, eventCount);
    }

    public Long storedRow() {
        return storedRow;
    }

    public AttributionSubject subject() {
        return subject;
    }

    public List<AttributionPeriod> periods() {
        return periods;
    }

    public int storedPeriodCount() {
        return storedPeriodCount;
    }

    public Optional<AttributionPeriod> on(LocalDate date) {
        return periods.stream().filter(p -> p.covers(date)).findFirst();
    }

    public Optional<AttributionPeriod> open() {
        return periods.stream().filter(AttributionPeriod::isOpen).findFirst();
    }

    public void attribute(LegalEntityId legalEntityId, LocalDate from) {
        Objects.requireNonNull(legalEntityId, "legalEntityId");
        Objects.requireNonNull(from, "from");
        Optional<AttributionPeriod> latest = periods.stream().max(java.util.Comparator.comparing(AttributionPeriod::effectiveFrom));
        if (latest.isPresent() && !from.isAfter(latest.get().effectiveFrom())) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_ATTRIBUTION_DATE_INVALID, ErrorKind.UNPROCESSABLE,
                    "a new attribution starts after " + latest.get().effectiveFrom());
        }
        if (latest.isPresent() && latest.get().isOpen() && latest.get().legalEntityId().equals(legalEntityId)) {
            return;
        }
        List<AttributionPeriod> next = new ArrayList<>(periods);
        if (latest.isPresent() && (latest.get().isOpen() || latest.get().effectiveTo().isAfter(from))) {
            next.set(next.indexOf(latest.get()), latest.get().closedOn(from));
        }
        next.add(new AttributionPeriod(legalEntityId, from, null));
        this.periods = List.copyOf(next);
        record((envelopes, sequence) -> new LegalEntityAttributed(envelopes.wrap(draft("LegalEntityAttributed", sequence, legalEntityId, from)),
                subject, legalEntityId, from));
    }

    public void end(LocalDate to) {
        Objects.requireNonNull(to, "to");
        AttributionPeriod open = open().orElseThrow(() -> new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_ATTRIBUTION_NOT_OPEN,
                ErrorKind.CONFLICT, "there is no open attribution to end"));
        if (!to.isAfter(open.effectiveFrom())) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_ATTRIBUTION_DATE_INVALID, ErrorKind.UNPROCESSABLE,
                    "an attribution ends after " + open.effectiveFrom());
        }
        List<AttributionPeriod> next = new ArrayList<>(periods);
        next.set(next.indexOf(open), open.closedOn(to));
        this.periods = List.copyOf(next);
        record((envelopes, sequence) -> new LegalEntityAttributionEnded(envelopes.wrap(draft("LegalEntityAttributionEnded", sequence,
                open.legalEntityId(), to)), subject, open.legalEntityId(), to));
    }

    private EventDraft draft(String eventName, long sequence, LegalEntityId legalEntityId, LocalDate businessDate) {
        return new EventDraft("organization.workforce." + eventName, 1, "LegalEntityAttribution",
                subject.kind() + ":" + subject.subjectKey(), sequence, legalEntityId, businessDate,
                new EventSource(SourceSystem.ONE, null), List.of());
    }
}
