package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.ActorType;
import com.forcitis.one.organization.workforce.api.AttributionCountersignId;
import com.forcitis.one.organization.workforce.domain.AttributionCountersign;
import com.forcitis.one.organization.workforce.domain.AttributionCountersignRepository;
import com.forcitis.one.organization.workforce.domain.CountersignRequestKind;
import com.forcitis.one.organization.workforce.domain.OrgStructureCommand;
import com.forcitis.one.organization.workforce.domain.PersonnelCommand;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditEntryId;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Writing the countersign requests of a command and what each write leaves behind: the state transition and the audit entry (note of #878 section 7). The required
 * set is the legal entities of the basis (a personnel command: its two; an org command: every entity before or after for any employee of the basis), one request each, decided separately even when one person heads both (user answer Q1).
 */
@Component
class CountersignRequests {
    static final String AGGREGATE = "AttributionCountersign";
    private static final String OPEN = "OPEN";

    private final AttributionCountersignRepository countersigns;
    private final CountersignDueDates dueDates;
    private final AuditLog audit;
    private final IdGenerator ids;

    CountersignRequests(AttributionCountersignRepository countersigns, CountersignDueDates dueDates, AuditLog audit, IdGenerator ids) {
        this.countersigns = countersigns;
        this.dueDates = dueDates;
        this.audit = audit;
        this.ids = ids;
    }

    /** What a request is about: the command that needs the countersign and the audit subject of what it changes. */
    record Parent(UUID id, AuditSubject subject) {
        static Parent of(PersonnelCommand command) {
            return new Parent(command.id().value(), AuditSubject.of(AuditSubjectKind.EMPLOYEE, command.employeeId().value()));
        }

        static Parent of(OrgStructureCommand command) {
            return new Parent(command.id().value(), AuditSubject.of(AuditSubjectKind.ORG_UNIT, java.util.Objects.requireNonNull(command.targetOrgUnitId()).value()));
        }
    }

    /** The legal entities whose finance heads must decide: the entity before and the entity after. */
    static Set<LegalEntityId> required(PersonnelCommand command) {
        Set<LegalEntityId> entities = new LinkedHashSet<>();
        entities.add(java.util.Objects.requireNonNull(command.countersignPreviousLegalEntityId(), "previous legal entity"));
        entities.add(java.util.Objects.requireNonNull(command.countersignNewLegalEntityId(), "new legal entity"));
        return entities;
    }

    /** A request per required entity, asked by {@code by}; the next sequence number of the kind (a rejected withdrawal may be asked again). */
    void open(PersonnelCommand command, CountersignRequestKind kind, @Nullable String requestReason, List<AttributionCountersign> existing, UserId by, Instant now,
              LocalDate today, UUID correlation) {
        int sequence = nextSequence(existing, kind);
        for (LegalEntityId entity : required(command)) {
            insert(AttributionCountersign.open(new AttributionCountersignId(ids.next()), command, kind, sequence, entity, by, now, dueDates.after(today, entity), requestReason),
                    Parent.of(command), by, now, today, correlation);
        }
    }

    /** As {@link #open} for an org-structure command: one request per legal entity of the set. */
    void open(OrgStructureCommand command, Set<LegalEntityId> entities, CountersignRequestKind kind, @Nullable String requestReason, List<AttributionCountersign> existing, UserId by,
              Instant now, LocalDate today, UUID correlation) {
        int sequence = nextSequence(existing, kind);
        for (LegalEntityId entity : entities) {
            insert(AttributionCountersign.openForOrg(new AttributionCountersignId(ids.next()), command, kind, sequence, entity, by, now, dueDates.after(today, entity),
                    requestReason), Parent.of(command), by, now, today, correlation);
        }
    }

    private static int nextSequence(List<AttributionCountersign> existing, CountersignRequestKind kind) {
        return existing.stream().filter(r -> r.requestKind() == kind).mapToInt(AttributionCountersign::requestSequenceNumber).max().orElse(0) + 1;
    }

    private void insert(AttributionCountersign request, Parent parent, UserId by, Instant now, LocalDate today, UUID correlation) {
        countersigns.insert(request);
        countersigns.insertTransition(request.id().value(), request.version(), null, request.status().name(), ActorType.USER, by.value(), today, now, parent.id(), correlation);
        audit.record(new AuditEntry("organization.workforce.attribution-countersign.requested", by, null, parent.subject(), detail(parent, request)));
    }

    /** The finance head approved the request (the aggregate already says so); writes the decision with its audit entry and transition. */
    AuditEntryId approved(AttributionCountersign request, PersonnelCommand command, long loadedVersion, UserId by, Instant now, LocalDate today, UUID correlation) {
        return approved(request, Parent.of(command), loadedVersion, by, now, today, correlation);
    }

    AuditEntryId approved(AttributionCountersign request, Parent parent, long loadedVersion, UserId by, Instant now, LocalDate today, UUID correlation) {
        AuditEntryId entry = audit.record(new AuditEntry("organization.workforce.attribution-countersign.approved", by, null, parent.subject(), detail(parent, request)));
        return save(request, parent, loadedVersion, entry, ActorType.USER, by.value(), now, today, correlation);
    }

    AuditEntryId rejected(AttributionCountersign request, PersonnelCommand command, long loadedVersion, UserId by, Instant now, LocalDate today, UUID correlation) {
        return rejected(request, Parent.of(command), loadedVersion, by, now, today, correlation);
    }

    AuditEntryId rejected(AttributionCountersign request, Parent parent, long loadedVersion, UserId by, Instant now, LocalDate today, UUID correlation) {
        AuditEntryId entry = audit.record(new AuditEntry("organization.workforce.attribution-countersign.rejected", by, null, parent.subject(), detail(parent, request)));
        return save(request, parent, loadedVersion, entry, ActorType.USER, by.value(), now, today, correlation);
    }

    private AuditEntryId save(AttributionCountersign request, Parent parent, long loadedVersion, AuditEntryId entry, ActorType actorType, UUID actor, Instant now,
                              LocalDate today, UUID correlation) {
        countersigns.saveDecision(request, entry, now, loadedVersion);
        countersigns.insertTransition(request.id().value(), loadedVersion + 1, OPEN, request.status().name(), actorType, actor, today, now, parent.id(), correlation);
        return entry;
    }

    /** The open requests of the given ones close without a decision because the command left the state that needed them. */
    void lapse(PersonnelCommand command, List<AttributionCountersign> requests, ActorType actorType, UUID actor, Instant now, LocalDate today, UUID correlation) {
        lapse(Parent.of(command), requests, actorType, actor, now, today, correlation);
    }

    void lapse(Parent parent, List<AttributionCountersign> requests, ActorType actorType, UUID actor, Instant now, LocalDate today, UUID correlation) {
        for (AttributionCountersign request : requests) {
            if (request.isOpen()) {
                long loaded = request.version();
                request.close(now);
                AuditEntryId entry = audit.record(new AuditEntry("organization.workforce.attribution-countersign.lapsed", new UserId(actor), null, parent.subject(),
                        detail(parent, request)));
                save(request, parent, loaded, entry, actorType, actor, now, today, correlation);
            }
        }
    }

    private static String detail(Parent parent, AttributionCountersign request) {
        String text = request.isOpen() ? request.requestReason() : request.decisionReason();
        String reason = text == null ? "" : "; reason: " + text.replaceAll("\\p{Cntrl}", " ");
        return "command " + parent.id() + " " + request.requestKind() + " #" + request.requestSequenceNumber() + " legal entity " + request.legalEntityId().value()
                + " " + request.status() + reason;
    }
}
