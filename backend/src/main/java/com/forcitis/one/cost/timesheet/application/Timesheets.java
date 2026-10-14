package com.forcitis.one.cost.timesheet.application;

import com.forcitis.one.cost.timesheet.api.TimesheetId;
import com.forcitis.one.cost.timesheet.domain.StaleTimesheetException;
import com.forcitis.one.cost.timesheet.domain.Timesheet;
import com.forcitis.one.cost.timesheet.domain.TimesheetRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** Loads a timesheet (locked), saves it once per use case and publishes what it recorded in the same transaction. */
@Component
class Timesheets {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final TimesheetRepository repository;
    private final TimesheetEvents events;
    private final ObjectProvider<Clock> clock;

    Timesheets(TimesheetRepository repository, TimesheetEvents events, ObjectProvider<Clock> clock) {
        this.repository = repository;
        this.events = events;
        this.clock = clock;
    }

    Timesheet require(TimesheetId id) {
        return repository.find(id).orElseThrow(() -> new TimesheetOperationException(TimesheetError.COST_TIMESHEET_NOT_FOUND,
                "No timesheet " + id.value()));
    }

    void save(Timesheet timesheet) {
        try {
            repository.save(timesheet);
        } catch (StaleTimesheetException stale) {
            throw new TimesheetOperationException(TimesheetError.COST_TIMESHEET_CONCURRENT_CHANGE, stale.getMessage());
        }
        events.publish(timesheet.pullEvents());
    }

    LocalDate today() {
        return LocalDate.ofInstant(clock.getIfAvailable(Clock::systemUTC).instant(), ZONE);
    }

    Instant now() {
        return clock.getIfAvailable(Clock::systemUTC).instant();
    }
}
