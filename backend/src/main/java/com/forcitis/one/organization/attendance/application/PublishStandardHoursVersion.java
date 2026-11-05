package com.forcitis.one.organization.attendance.application;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.KnownLegalEntities;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.organization.attendance.api.AttendanceError;
import com.forcitis.one.organization.attendance.domain.DateOverride;
import com.forcitis.one.organization.attendance.domain.StandardHours;
import com.forcitis.one.organization.attendance.domain.StandardHoursRepository;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditEntryId;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Publishes the next version of a scope; the database closes the previous one. The audit entry is written first because the version cites it. */
@Service
public class PublishStandardHoursVersion {
    private final StandardHoursRepository versions;
    private final KnownLegalEntities legalEntities;
    private final AuditLog audit;
    private final DomainEvents events;
    private final CurrentUser currentUser;
    private final IdGenerator ids;
    private final Clock clock;
    private final BusinessZone zone;

    PublishStandardHoursVersion(StandardHoursRepository versions, KnownLegalEntities legalEntities, AuditLog audit, DomainEvents events,
            CurrentUser currentUser, IdGenerator ids, Clock clock, BusinessZone zone) {
        this.versions = versions;
        this.legalEntities = legalEntities;
        this.audit = audit;
        this.events = events;
        this.currentUser = currentUser;
        this.ids = ids;
        this.clock = clock;
        this.zone = zone;
    }

    @Transactional
    public PublishedStandardHours execute(PublishStandardHoursCommand command) {
        if (command.legalEntityId() != null && !legalEntities.isKnown(command.legalEntityId())) {
            throw new DomainException(AttendanceError.ORGANIZATION_ATTENDANCE_STANDARD_HOURS_INVALID, ErrorKind.INVALID,
                    "unknown legal entity " + command.legalEntityId());
        }
        UUID id = ids.next();
        UserId by = currentUser.userId();
        AuditEntryId decision = audit.record(new AuditEntry("organization.attendance.standard-hours.published", by, null,
                AuditSubject.without(AuditSubjectKind.OPERATION), "standardHoursVersionId=" + id + " scope="
                + (command.legalEntityId() == null ? "DEFAULT" : command.legalEntityId()) + " effectiveFromDate=" + command.effectiveFromDate()
                + " workdayMinutes=" + command.workdayMinutes() + " adjustedWorkdayMinutes=" + command.adjustedWorkdayMinutes()
                + " dateOverrides=" + command.dateOverrides().size()));
        StandardHours version = StandardHours.publish(id, command.legalEntityId(), versions.tail(command.legalEntityId()), zone.today(clock),
                command.effectiveFromDate(), command.workdayMinutes(), command.adjustedWorkdayMinutes(), command.dateOverrides().stream().map(o -> new DateOverride(o.workDate(), o.minutes(), o.reason())).toList(),
                command.basisRef(), by, clock.instant(), decision);
        versions.insert(version);
        events.publish(version);
        return new PublishedStandardHours(id, version.versionNumber());
    }
}
