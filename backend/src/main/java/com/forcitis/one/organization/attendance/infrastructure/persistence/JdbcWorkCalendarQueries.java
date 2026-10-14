package com.forcitis.one.organization.attendance.infrastructure.persistence;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.attendance.domain.CalendarDay;
import com.forcitis.one.organization.attendance.domain.DayKind;
import com.forcitis.one.organization.attendance.domain.WorkCalendarKey;
import com.forcitis.one.organization.attendance.domain.WorkCalendarQueries;
import com.forcitis.one.organization.attendance.domain.WorkCalendarYearRecord;
import java.util.List;
import java.util.Optional;
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
                        select id from organization_attendance.work_calendar_years
                         where legal_entity_id is not distinct from :legalEntityId and year_number = :year
                        """)
                .param("legalEntityId", key.legalEntityId() == null ? null : key.legalEntityId().value(), java.sql.Types.BIGINT)
                .param("year", key.calendarYear())
                .query(Long.class).optional()
                .map(id -> new WorkCalendarYearRecord(key, days(id)));
    }

    private List<CalendarDay> days(long yearId) {
        return jdbc.sql("select calendar_date, day_kind from organization_attendance.work_calendar_days where work_calendar_year_id = :id order by calendar_date")
                .param("id", yearId)
                .query((rs, n) -> new CalendarDay(rs.getDate("calendar_date").toLocalDate(), DayKind.valueOf(rs.getString("day_kind")))).list();
    }

    @Override
    public List<Integer> findMaintainedYears(LegalEntityId legalEntityId) {
        return jdbc.sql("""
                        select year_number from organization_attendance.work_calendar_years
                         where legal_entity_id is not distinct from :legalEntityId order by year_number
                        """)
                .param("legalEntityId", legalEntityId == null ? null : legalEntityId.value(), java.sql.Types.BIGINT)
                .query(Integer.class).list();
    }
}
