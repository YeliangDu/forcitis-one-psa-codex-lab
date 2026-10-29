package com.forcitis.one.organization.attendance.infrastructure.persistence;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.attendance.api.AttendanceError;
import com.forcitis.one.organization.attendance.domain.CalendarDay;
import com.forcitis.one.organization.attendance.api.DayKind;
import com.forcitis.one.organization.attendance.domain.WorkCalendarKey;
import com.forcitis.one.organization.attendance.domain.WorkCalendarRepository;
import com.forcitis.one.organization.attendance.domain.WorkCalendarYear;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.LocalDate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcWorkCalendarRepository implements WorkCalendarRepository {
    private final JdbcClient jdbc;
    private final Clock clock;
    private final IdGenerator ids;

    JdbcWorkCalendarRepository(JdbcClient jdbc, Clock clock, IdGenerator ids) {
        this.ids = ids;
        this.clock = clock;
        this.jdbc = jdbc;
    }

    @Override
    public Optional<WorkCalendarYear> find(WorkCalendarKey key) {
        return jdbc.sql("""
                        select id, version, event_count from organization_attendance.work_calendar_years
                         where legal_entity_id is not distinct from :legalEntityId and year_number = :year
                        """)
                .param("legalEntityId", key.legalEntityId() == null ? null : key.legalEntityId().value(), java.sql.Types.OTHER)
                .param("year", key.calendarYear())
                .query((rs, n) -> new Stored(rs.getObject("id", UUID.class), rs.getLong("version"), rs.getLong("event_count"))).optional()
                .map(row -> WorkCalendarYear.restore(row.id(), key, days(row.id()), row.version(), row.eventCount()));
    }

    private record Stored(UUID id, long version, long eventCount) {
    }

    private List<CalendarDay> days(UUID yearId) {
        return jdbc.sql("""
                select calendar_date, day_kind from organization_attendance.work_calendar_days where work_calendar_year_id = :id order by calendar_date
                """)
                .param("id", yearId)
                .query((rs, n) -> new CalendarDay(rs.getObject("calendar_date", LocalDate.class), DayKind.valueOf(rs.getString("day_kind")))).list();
    }

    @Override
    public void save(WorkCalendarYear calendarYear) {
        WorkCalendarKey key = calendarYear.key();
        UUID id = calendarYear.id();
        if (calendarYear.version() == 0) {
            try {
                jdbc.sql("""
                                insert into organization_attendance.work_calendar_years (id, legal_entity_id, year_number, version, event_count)
                                values (:id, :legalEntityId, :year, :version, :eventSequence)
                                """)
                        .param("id", id).param("legalEntityId", key.legalEntityId() == null ? null : key.legalEntityId().value(), java.sql.Types.OTHER)
                        .param("year", key.calendarYear()).param("version", calendarYear.versionAfterSave())
                        .param("eventSequence", calendarYear.eventSequenceAfterSave())
                        .update();
            } catch (DuplicateKeyException saved) {
                throw conflict(key);
            }
        } else {
            int updated = jdbc.sql("""
                            update organization_attendance.work_calendar_years set version = :next, event_count = :eventSequence, updated_at = :updatedAt
                             where id = :id and version = :version
                            """)
                    .param("next", calendarYear.versionAfterSave()).param("eventSequence", calendarYear.eventSequenceAfterSave()).param("id", id).param("version", calendarYear.version()).param("updatedAt", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
                    .update();
            if (updated != 1) {
                throw conflict(key);
            }
            jdbc.sql("""
                    delete from organization_attendance.work_calendar_days where work_calendar_year_id = :id
                    """).param("id", id).update();
        }
        for (CalendarDay day : calendarYear.days()) {
            jdbc.sql("""
                            insert into organization_attendance.work_calendar_days (id, work_calendar_year_id, calendar_date, day_kind)
                            values (:dayId, :id, :date, :kind)
                            """)
                    .param("dayId", ids.next()).param("id", id).param("date", day.calendarDate()).param("kind", day.dayKind().name()).update();
        }
    }

    private static DomainException conflict(WorkCalendarKey key) {
        return new DomainException(AttendanceError.ORGANIZATION_ATTENDANCE_YEAR_CONFLICT, ErrorKind.CONFLICT,
                "the work calendar of " + key.calendarYear() + " was saved by someone else; reload and try again");
    }
}
