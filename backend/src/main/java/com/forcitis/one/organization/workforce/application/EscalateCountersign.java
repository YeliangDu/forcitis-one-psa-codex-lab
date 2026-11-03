package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.api.AttributionCountersignId;
import com.forcitis.one.organization.workforce.domain.AttributionCountersign;
import com.forcitis.one.organization.workforce.domain.AttributionCountersignRepository;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Raises one open countersign request to the highest escalation level due on the day and sends the one notice of that level (ADR-0073 item 3.6, one-design rulings on #896
 * items 2 and 3). "After" is strict: level 1 when the day is past the due date, level 2 past the 2nd working day after it, level 3 past the 4th. A request already at the due
 * level, or decided in the meantime, is left alone, so a repeated run changes and sends nothing. It never decides anything.
 */
@Service
class EscalateCountersign {
    private static final int LEVEL_TWO_WORKING_DAYS = 2;
    private static final int LEVEL_THREE_WORKING_DAYS = 4;

    private final AttributionCountersignRepository countersigns;
    private final CountersignDueDates dueDates;
    private final CountersignEscalationNotice notice;
    private final Clock clock;

    EscalateCountersign(AttributionCountersignRepository countersigns, CountersignDueDates dueDates, CountersignEscalationNotice notice, Clock clock) {
        this.countersigns = countersigns;
        this.dueDates = dueDates;
        this.notice = notice;
        this.clock = clock;
    }

    @Transactional
    void execute(AttributionCountersignId id, LocalDate today) {
        AttributionCountersign row = countersigns.find(id).orElseThrow(() -> new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_COUNTERSIGN_NOT_FOUND, ErrorKind.NOT_FOUND,
                "no countersign request " + id.value()));
        if (!row.isOpen()) {
            return;
        }
        int level = levelOn(row, today);
        if (level <= row.escalationLevel()) {
            return;
        }
        long loaded = row.version();
        row.escalateTo(level);
        countersigns.saveEscalation(row, clock.instant(), loaded);
        notice.send(row, level, today);
    }

    /** The calendar of the row's legal entity is read only as far as the day reaches, so a row not yet past its due date asks for nothing. */
    private int levelOn(AttributionCountersign row, LocalDate today) {
        if (!today.isAfter(row.dueOn())) {
            return 0;
        }
        if (!today.isAfter(dueDates.workingDaysAfter(row.dueOn(), LEVEL_TWO_WORKING_DAYS, row.legalEntityId()))) {
            return 1;
        }
        return today.isAfter(dueDates.workingDaysAfter(row.dueOn(), LEVEL_THREE_WORKING_DAYS, row.legalEntityId())) ? AttributionCountersign.LAST_ESCALATION_LEVEL : 2;
    }
}
