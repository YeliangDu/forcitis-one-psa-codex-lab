package com.forcitis.one.organization.workforce.infrastructure.persistence;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.InvalidCursorException;
import com.forcitis.one.organization.workforce.api.PositionId;
import com.forcitis.one.organization.workforce.domain.PositionQueries;
import com.forcitis.one.organization.workforce.domain.PositionStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcPositionQueries implements PositionQueries {
    private static final String CURRENT = """
            with current_positions as (
                select position_id, code, name, status, effective_from_date, effective_to_date
                  from organization_workforce.position_versions
                 where superseded_at is null and effective_from_date <= :day
                   and (effective_to_date is null or :day < effective_to_date)
            )
            """;
    private static final String SELECT = "select position_id, code, name, status, effective_from_date, effective_to_date from current_positions";

    private final JdbcClient jdbc;
    private final Clock clock;
    private final BusinessZone zone;

    JdbcPositionQueries(JdbcClient jdbc, Clock clock, BusinessZone zone) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.zone = zone;
    }

    @Override
    public List<Row> list(@Nullable String status, @Nullable PositionId after, int limit) {
        var rows = jdbc.sql(CURRENT + """
                , filtered_positions as (
                    select position_id, code, name, status, effective_from_date, effective_to_date from current_positions
                     where cast(:status as text) is null or status = :status
                )
                select position_id, code, name, status, effective_from_date, effective_to_date from filtered_positions
                 where cast(:after as uuid) is null or (coalesce(code, ''), position_id) >
                       (select coalesce(code, ''), position_id from filtered_positions where position_id = :after)
                 order by coalesce(code, ''), position_id limit :limit
                """).param("day", zone.today(clock)).param("status", status, Types.VARCHAR)
                .param("after", after == null ? null : after.value(), Types.OTHER).param("limit", limit).query((rs, n) -> row(rs)).list();
        if (rows.isEmpty() && after != null && jdbc.sql(CURRENT + SELECT + " where position_id = :id and (cast(:status as text) is null or status = :status)")
                .param("day", zone.today(clock)).param("status", status, Types.VARCHAR).param("id", after.value()).query((rs, n) -> 1).list().isEmpty()) {
            // A cursor row that left the filtered set between two pages must not look like the end of the list.
            throw new InvalidCursorException("the cursor row is no longer in the result");
        }
        return rows;
    }

    @Override
    public Optional<Row> find(PositionId position) {
        return jdbc.sql(CURRENT + SELECT + " where position_id = :id").param("day", zone.today(clock)).param("id", position.value())
                .query((rs, n) -> row(rs)).optional();
    }

    @Override
    public boolean isActive(PositionId position) {
        return jdbc.sql(CURRENT + SELECT + " where position_id = :id and status = 'ACTIVE' and exists (select 1 from organization_workforce.positions p where p.id = :id and p.status = 'ACTIVE')")
                .param("day", zone.today(clock)).param("id", position.value()).query((rs, n) -> 1).list().size() == 1;
    }

    private static Row row(ResultSet rs) throws SQLException {
        return new Row(new PositionId(rs.getObject("position_id", UUID.class)), rs.getString("code"), rs.getString("name"),
                PositionStatus.valueOf(rs.getString("status")), rs.getObject("effective_from_date", LocalDate.class), rs.getObject("effective_to_date", LocalDate.class));
    }
}
