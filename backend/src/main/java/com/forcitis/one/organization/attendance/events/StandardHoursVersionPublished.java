package com.forcitis.one.organization.attendance.events;

import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.kernel.event.NotAudited;
import com.forcitis.one.organization.attendance.api.StandardHoursVersionId;
import java.time.LocalDate;

/** Analytics only: a standard-hours version of one legal entity (or the DEFAULT scope) was published, effective from the given date, which is also the envelope's businessDate. */
@NotAudited(reason = "the publication is audited by its own record in the same transaction (organization.attendance.standard-hours.published)")
public record StandardHoursVersionPublished(EventEnvelope envelope, StandardHoursVersionId standardHoursVersionId, LocalDate effectiveFromDate) implements DomainEvent {
}
