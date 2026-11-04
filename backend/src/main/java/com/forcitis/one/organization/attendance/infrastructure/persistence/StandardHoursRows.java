package com.forcitis.one.organization.attendance.infrastructure.persistence;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.attendance.domain.DateOverride;
import com.forcitis.one.organization.attendance.domain.StandardHoursStatus;
import com.forcitis.one.organization.attendance.domain.StandardHoursVersionRecord;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Column list and row mapping shared by the repository and the queries. */
final class StandardHoursRows {
    static final String COLUMNS = """
            id, legal_entity_id, version_number, status, effective_from_date, effective_to_date, workday_minutes_count,
            adjusted_workday_minutes_count, basis_ref_text, confirmed_by, confirmed_at
            """;

    private StandardHoursRows() {
    }

    static StandardHoursVersionRecord map(ResultSet rs, List<DateOverride> overrides) throws SQLException {
        UUID legalEntity = rs.getObject("legal_entity_id", UUID.class);
        return new StandardHoursVersionRecord(rs.getObject("id", UUID.class), legalEntity == null ? null : new LegalEntityId(legalEntity),
                rs.getInt("version_number"), StandardHoursStatus.valueOf(rs.getString("status")),
                rs.getObject("effective_from_date", LocalDate.class), rs.getObject("effective_to_date", LocalDate.class),
                rs.getInt("workday_minutes_count"), rs.getInt("adjusted_workday_minutes_count"), rs.getString("basis_ref_text"),
                new UserId(rs.getObject("confirmed_by", UUID.class)), rs.getObject("confirmed_at", OffsetDateTime.class).toInstant(), overrides);
    }

    static List<DateOverride> overrides(JdbcClient jdbc, UUID versionId) {
        return jdbc.sql("""
                select work_date, minutes_count, reason_text from organization_attendance.standard_hours_date_overrides
                 where standard_hours_version_id = :id order by work_date
                """)
                .param("id", versionId)
                .query((rs, n) -> new DateOverride(rs.getObject("work_date", LocalDate.class), rs.getInt("minutes_count"), rs.getString("reason_text")))
                .list();
    }
}
