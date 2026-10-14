package com.forcitis.one.organization.attendance.infrastructure.persistence;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.attendance.api.AttendanceError;
import com.forcitis.one.organization.attendance.domain.CalendarDay;
import com.forcitis.one.organization.attendance.api.DayKind;
import com.forcitis.one.organization.attendance.domain.WorkCalendarKey;
import com.forcitis.one.organization.attendance.domain.WorkCalendarRepository;
import com.forcitis.one.organization.attendance.domain.WorkCalendarYear;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcWorkCalendarRepository implements WorkCalendarRepository {
    private final JdbcClient jdbc;
    private final Clock clock;

    JdbcWorkCalendarRepository(JdbcClient jdbc, Clock clock) {
        this.clock = clock;
        this.jdbc = jdbc;
    }

    @Override
    public Optional<WorkCalendarYear> find(WorkCalendarKey key) {
        return jdbc.sql("""
                        select id, version, event_count from organization_attendance.work_calendar_years
                         where legal_entity_id is not distinct from :legalEntityId and year_number = :year
                        """)
                .param("legalEntityId", key.legalEntityId() == null ? null : key.legalEntityId().value(), java.sql.Types.BIGINT)
                .param("year", key.calendarYear())
                .query((rs, n) -> new long[] {rs.getLong("id"), rs.getLong("version"), rs.getLong("event_count")}).optional()
                .map(row -> WorkCalendarYear.restore(row[0], key, days(row[0]), row[1], row[2]));
    }

    private List<CalendarDay> days(long yearId) {
        return jdbc.sql("select calendar_date, day_kind from organization_attendance.work_calendar_days where work_calendar_year_id = :id order by calendar_date")
                .param("id", yearId)
                .query((rs, n) -> new CalendarDay(rs.getDate("calendar_date").toLocalDate(), DayKind.valueOf(rs.getString("day_kind")))).list();
    }

    @Override
    public void save(WorkCalendarYear calendarYear) {
        WorkCalendarKey key = calendarYear.key();
        long id;
        if (calendarYear.storedRow() == null) {
            try {
                id = jdbc.sql("""
                                insert into organization_attendance.work_calendar_years (legal_entity_id, year_number, version, event_count)
                                values (:legalEntityId, :year, :version, :eventSequence) returning id
                                """)
                        .param("legalEntityId", key.legalEntityId() == null ? null : key.legalEntityId().value(), java.sql.Types.BIGINT)
                        .param("year", key.calendarYear()).param("version", calendarYear.versionAfterSave())
                        .param("eventSequence", calendarYear.eventSequenceAfterSave())
                        .query(Long.class).single();
            } catch (DuplicateKeyException saved) {
                throw conflict(key);
            }
        } else {
            id = calendarYear.storedRow();
            int updated = jdbc.sql("""
                            update organization_attendance.work_calendar_years set version = :next, event_count = :eventSequence, updated_at = :updatedAt
                             where id = :id and version = :version
                            """)
                    .param("next", calendarYear.versionAfterSave()).param("eventSequence", calendarYear.eventSequenceAfterSave()).param("id", id).param("version", calendarYear.version()).param("updatedAt", Timestamp.from(clock.instant()))
                    .update();
            if (updated != 1) {
                throw conflict(key);
            }
            jdbc.sql("delete from organization_attendance.work_calendar_days where work_calendar_year_id = :id").param("id", id).update();
        }
        for (CalendarDay day : calendarYear.days()) {
            jdbc.sql("""
                            insert into organization_attendance.work_calendar_days (work_calendar_year_id, calendar_date, day_kind)
                            values (:id, :date, :kind)
                            """)
                    .param("id", id).param("date", Date.valueOf(day.calendarDate())).param("kind", day.dayKind().name()).update();
        }
    }

    private static DomainException conflict(WorkCalendarKey key) {
        return new DomainException(AttendanceError.ORGANIZATION_ATTENDANCE_YEAR_CONFLICT, ErrorKind.CONFLICT,
                "the work calendar of " + key.calendarYear() + " was saved by someone else; reload and try again");
    }
}
