package com.forcitis.one.organization.workforce.domain;

import java.util.Comparator;
import com.forcitis.one.kernel.AggregateRoot;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.Reason;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.EventDraft;
import com.forcitis.one.organization.workforce.api.AttributionId;
import com.forcitis.one.organization.workforce.api.AttributionSubject;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.events.LegalEntityAttributed;
import com.forcitis.one.organization.workforce.events.LegalEntityAttributionCorrected;
import com.forcitis.one.organization.workforce.events.LegalEntityAttributionEnded;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import com.forcitis.one.organization.workforce.api.AttributionSubjectId;
import org.jspecify.annotations.Nullable;

/**
 * Which legal entity a department or an employee belongs to, period by period. Periods never overlap; history is only closed,
 * never rewritten: a new attribution starts after the start of the latest period.
 */
public final class LegalEntityAttribution extends AggregateRoot {
    private final AttributionId id;
    private final AttributionSubject subject;
    private final int storedPeriodCount;
    private List<AttributionPeriod> periods;
    private final List<AttributionCorrection> pendingCorrections = new ArrayList<>();

    private LegalEntityAttribution(AttributionId id, AttributionSubject subject, List<AttributionPeriod> periods, long version,
                                   long eventCount) {
        super(version, eventCount);
        this.id = Objects.requireNonNull(id, "id");
        this.subject = Objects.requireNonNull(subject, "subject");
        this.periods = List.copyOf(periods);
        this.storedPeriodCount = this.periods.size();
    }

    public static LegalEntityAttribution empty(AttributionId id, AttributionSubject subject) {
        return new LegalEntityAttribution(id, subject, List.of(), 0, 0);
    }

    public static LegalEntityAttribution restore(AttributionId id, AttributionSubject subject, List<AttributionPeriod> periods, long version,
                                                 long eventCount) {
        return new LegalEntityAttribution(id, subject, periods.stream().sorted(Comparator.comparing(AttributionPeriod::effectiveFrom)).toList(),
                version, eventCount);
    }

    public AttributionId id() {
        return id;
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

    public List<AttributionCorrection> pendingCorrections() {
        return List.copyOf(pendingCorrections);
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
        Optional<AttributionPeriod> latest = periods.stream().max(Comparator.comparing(AttributionPeriod::effectiveFrom));
        if (latest.isPresent() && !from.isAfter(latest.get().effectiveFrom())) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_ATTRIBUTION_DATE_INVALID, ErrorKind.UNPROCESSABLE,
                    "a new attribution starts after " + latest.get().effectiveFrom());
        }
        if (latest.isPresent() && latest.get().isOpen() && latest.get().legalEntityId().equals(legalEntityId)) {
            return;
        }
        if (latest.isPresent() && !latest.get().isOpen() && from.isBefore(latest.get().effectiveTo())) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_ATTRIBUTION_DATE_INVALID, ErrorKind.UNPROCESSABLE,
                    "the latest attribution ended on " + latest.get().effectiveTo() + "; a closed period is not shortened, so a new one starts on or after that day");
        }
        List<AttributionPeriod> next = new ArrayList<>(periods);
        if (latest.isPresent() && latest.get().isOpen()) {
            next.set(next.indexOf(latest.get()), latest.get().closedOn(from));
        }
        next.add(new AttributionPeriod(legalEntityId, from, null));
        this.periods = List.copyOf(next);
        record((envelopes, sequence) -> new LegalEntityAttributed(envelopes.wrap(draft("LegalEntityAttributed", sequence, legalEntityId, from)),
                subject.kind(), new AttributionSubjectId(subject.subjectId()), legalEntityId, from));
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
                open.legalEntityId(), to)), subject.kind(), new AttributionSubjectId(subject.subjectId()), open.legalEntityId(), to));
    }

    /**
     * A closed period is never rewritten: the correction is appended and the period reads as ending on the new day from then on. The
     * corrected period must not run into another period of the subject. Correcting to the day it already ends on changes nothing.
     */
    public void correct(LocalDate periodFrom, LocalDate newEffectiveTo, Reason reason, UserId correctedBy) {
        Objects.requireNonNull(periodFrom, "periodFrom");
        Objects.requireNonNull(newEffectiveTo, "newEffectiveTo");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(correctedBy, "correctedBy");
        AttributionPeriod period = periods.stream().filter(p -> p.effectiveFrom().equals(periodFrom)).findFirst().orElseThrow(
                () -> new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_ATTRIBUTION_PERIOD_NOT_FOUND, ErrorKind.NOT_FOUND,
                        "no attribution period starts on " + periodFrom));
        if (period.isOpen()) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_ATTRIBUTION_PERIOD_NOT_CLOSED, ErrorKind.UNPROCESSABLE,
                    "the period that starts on " + periodFrom + " is still open: end it instead of correcting it");
        }
        if (!newEffectiveTo.isAfter(periodFrom)) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_ATTRIBUTION_DATE_INVALID, ErrorKind.UNPROCESSABLE,
                    "a period ends after " + periodFrom);
        }
        if (newEffectiveTo.equals(period.effectiveTo())) {
            return;
        }
        for (AttributionPeriod other : periods) {
            if (other.effectiveFrom().isAfter(periodFrom) && other.effectiveFrom().isBefore(newEffectiveTo)) {
                throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_ATTRIBUTION_CORRECTION_OVERLAP, ErrorKind.UNPROCESSABLE,
                        "ending on " + newEffectiveTo + " would run into the period that starts on " + other.effectiveFrom());
            }
        }
        @Nullable LocalDate previous = period.effectiveTo();
        List<AttributionPeriod> next = new ArrayList<>(periods);
        next.set(next.indexOf(period), period.closedOn(newEffectiveTo));
        this.periods = List.copyOf(next);
        pendingCorrections.add(new AttributionCorrection(periodFrom, previous, newEffectiveTo, reason, correctedBy));
        record((envelopes, sequence) -> new LegalEntityAttributionCorrected(envelopes.wrap(draft("LegalEntityAttributionCorrected", sequence,
                period.legalEntityId(), newEffectiveTo)), subject.kind(), new AttributionSubjectId(subject.subjectId()), period.legalEntityId(), periodFrom, previous, newEffectiveTo, reason));
    }

    private EventDraft draft(String eventName, long sequence, LegalEntityId legalEntityId, @Nullable LocalDate businessDate) {
        return new EventDraft("organization.workforce." + eventName, 1, "LegalEntityAttribution",
                id.value(), sequence, legalEntityId, businessDate, List.of());
    }
}
