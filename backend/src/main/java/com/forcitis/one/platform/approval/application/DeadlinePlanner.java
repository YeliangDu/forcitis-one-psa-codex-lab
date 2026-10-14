package com.forcitis.one.platform.approval.application;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.attendance.api.WorkCalendar;
import com.forcitis.one.platform.approval.domain.DeadlinePlan;
import com.forcitis.one.platform.approval.domain.NodeDeadline;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import org.springframework.stereotype.Component;

/**
 * D-1a-110..113: T is the workday the task is created in (the next workday when it is created on a day off); the deadline is 23:59 of
 * the deadlineWorkdays-th workday after T; the reminder is 09:00 on the next workday after the deadline; the escalation is 09:00 on
 * the escalationWorkdays-th workday after the reminder. A calendar that cannot answer fails the start of the approval, not the submission of the document (RULE-383).
 */
@Component
class DeadlinePlanner {

    static final LocalTime DEADLINE_TIME = LocalTime.of(23, 59);
    static final LocalTime REMINDER_TIME = LocalTime.of(9, 0);

    private final WorkCalendar calendar;
    private final BusinessTime time;

    DeadlinePlanner(WorkCalendar calendar, BusinessTime time) {
        this.calendar = calendar;
        this.time = time;
    }

    DeadlinePlan plan(NodeDeadline node, LegalEntityId legalEntityId) {
        LocalDate created = time.today();
        LocalDate start = calendar.isWorkday(created, legalEntityId) ? created : workdayAfter(created, 1, legalEntityId);
        LocalDate dueDay = workdayAfter(start, node.deadlineWorkdays(), legalEntityId);
        LocalDate remindDay = workdayAfter(dueDay, 1, legalEntityId);
        LocalDate escalateDay = workdayAfter(remindDay, node.escalationWorkdays(), legalEntityId);
        return new DeadlinePlan(at(dueDay, DEADLINE_TIME), at(remindDay, REMINDER_TIME), at(escalateDay, REMINDER_TIME));
    }

    private LocalDate workdayAfter(LocalDate day, int workdays, LegalEntityId legalEntityId) {
        Instant endOfWorkday = calendar.endOfWorkdayAfter(day, workdays, legalEntityId);
        return time.zone().dateOf(endOfWorkday.minusSeconds(1));
    }

    private Instant at(LocalDate day, LocalTime clockTime) {
        return day.atTime(clockTime).atZone(time.zone().zone()).toInstant();
    }
}
