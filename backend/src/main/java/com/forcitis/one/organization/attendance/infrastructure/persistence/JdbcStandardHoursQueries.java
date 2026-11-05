package com.forcitis.one.organization.attendance.infrastructure.persistence;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.attendance.domain.StandardHoursQueries;
import com.forcitis.one.organization.attendance.domain.StandardHoursVersionRecord;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcStandardHoursQueries implements StandardHoursQueries {
    private final JdbcClient jdbc;

    JdbcStandardHoursQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<StandardHoursVersionRecord> list(@Nullable LegalEntityId legalEntityId, @Nullable Integer beforeVersionNumber, int limit) {
        return jdbc.sql("select " + StandardHoursRows.COLUMNS + """
                  from organization_attendance.standard_hours_versions
                 where legal_entity_id is not distinct from :legalEntityId
                   and (cast(:beforeVersionNumber as integer) is null or version_number < :beforeVersionNumber)
                 order by version_number desc
                 limit :limit
                """)
                .param("legalEntityId", scope(legalEntityId), Types.OTHER)
                .param("beforeVersionNumber", beforeVersionNumber, Types.INTEGER)
                .param("limit", limit)
                .query((rs, n) -> StandardHoursRows.map(rs, StandardHoursRows.overrides(jdbc, rs.getObject("id", UUID.class)))).list();
    }

    @Override
    public boolean hasVersionInEffect(@Nullable LegalEntityId legalEntityId) {
        return jdbc.sql("""
                select exists (select 1 from organization_attendance.standard_hours_versions
                                where legal_entity_id is not distinct from :legalEntityId and status = 'IN_EFFECT')
                """)
                .param("legalEntityId", scope(legalEntityId), Types.OTHER)
                .query(Boolean.class).single();
    }

    @Override
    public Optional<StandardHoursVersionRecord> inEffectOn(@Nullable LegalEntityId legalEntityId, LocalDate date) {
        return jdbc.sql("select " + StandardHoursRows.COLUMNS + """
                  from organization_attendance.standard_hours_versions
                 where legal_entity_id is not distinct from :legalEntityId and status = 'IN_EFFECT'
                   and effective_from_date <= :date and (effective_to_date is null or :date < effective_to_date)
                """)
                .param("legalEntityId", scope(legalEntityId), Types.OTHER)
                .param("date", date)
                .query((rs, n) -> StandardHoursRows.map(rs, StandardHoursRows.overrides(jdbc, rs.getObject("id", UUID.class))))
                .optional();
    }

    private static @Nullable UUID scope(@Nullable LegalEntityId legalEntityId) {
        return legalEntityId == null ? null : legalEntityId.value();
    }
}
