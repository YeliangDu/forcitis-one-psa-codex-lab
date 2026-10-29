package com.forcitis.one.organization.attendance.infrastructure.persistence;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.attendance.domain.CalendarDay;
import com.forcitis.one.organization.attendance.api.DayKind;
import com.forcitis.one.organization.attendance.domain.WorkCalendarKey;
import com.forcitis.one.organization.attendance.domain.WorkCalendarQueries;
import com.forcitis.one.organization.attendance.domain.WorkCalendarYearRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcWorkCalendarQueries implements WorkCalendarQueries {
    private final JdbcClient jdbc;

    JdbcWorkCalendarQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<WorkCalendarYearRecord> find(WorkCalendarKey key) {
        return jdbc.sql("""
                        select id, version from organization_attendance.work_calendar_years
                         where legal_entity_id is not distinct from :legalEntityId and year_number = :year
                        """)
                .param("legalEntityId", key.legalEntityId() == null ? null : key.legalEntityId().value(), java.sql.Types.OTHER)
                .param("year", key.calendarYear())
                .query((rs, n) -> new Stored(rs.getObject("id", UUID.class), rs.getLong("version"))).optional()
                .map(row -> new WorkCalendarYearRecord(key, days(row.id()), row.version()));
    }

    private record Stored(UUID id, long version) {
    }

    private List<CalendarDay> days(UUID yearId) {
        return jdbc.sql("""
                select calendar_date, day_kind from organization_attendance.work_calendar_days where work_calendar_year_id = :id order by calendar_date
                """)
                .param("id", yearId)
                .query((rs, n) -> new CalendarDay(rs.getObject("calendar_date", LocalDate.class), DayKind.valueOf(rs.getString("day_kind")))).list();
    }

    @Override
    public List<Integer> findMaintainedYearsBefore(@Nullable LegalEntityId legalEntityId, @Nullable Integer beforeYear, int limit) {
        return jdbc.sql("""
                        select year_number from organization_attendance.work_calendar_years
                         where legal_entity_id is not distinct from :legalEntityId and (cast(:beforeYear as integer) is null or year_number < :beforeYear)
                         order by year_number desc limit :limit
                        """)
                .param("legalEntityId", legalEntityId == null ? null : legalEntityId.value(), java.sql.Types.OTHER)
                .param("beforeYear", beforeYear, java.sql.Types.INTEGER)
                .param("limit", limit)
                .query(Integer.class).list();
    }
}
