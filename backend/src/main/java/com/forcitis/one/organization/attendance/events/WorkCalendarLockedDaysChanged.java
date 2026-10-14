package com.forcitis.one.organization.attendance.events;

import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.organization.attendance.api.LockedDayChange;
import com.forcitis.one.platform.audit.api.AuditDetail;
import java.time.Year;
import java.util.List;
import java.util.stream.Collectors;

public record WorkCalendarLockedDaysChanged(EventEnvelope envelope, Year calendarYear, List<LockedDayChange> changes) implements DomainEvent, AuditDetail {
    @Override
    public String auditDetail() {
        return changes.stream().map(c -> c.calendarDate() + " " + c.before() + " -> " + c.after()).collect(Collectors.joining("; "));
    }
}
