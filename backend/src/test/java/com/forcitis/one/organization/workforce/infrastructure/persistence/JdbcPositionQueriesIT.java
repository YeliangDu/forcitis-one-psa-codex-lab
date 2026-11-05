package com.forcitis.one.organization.workforce.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.InvalidCursorException;
import com.forcitis.one.organization.workforce.api.PositionId;
import com.forcitis.one.organization.workforce.domain.PositionQueries.Row;
import com.forcitis.one.platform.integration.api.WecomGateway;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {"flowable.async-executor-activate=false"})
class JdbcPositionQueriesIT {
    private static final LocalDate TODAY = com.forcitis.one.BusinessDay.today();

    @ServiceConnection
    static final PostgreSQLContainer postgres = TestPostgres.container().withInitScript("init-schemas.sql").withUrlParam("currentSchema", "platform_approval");

    static {
        postgres.start();
    }

    @MockitoBean
    WecomGateway wecomGateway;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    BusinessZone zone;

    @Autowired
    IdGenerator ids;

    @Test
    @DisplayName("#1171 a superseded position version is ignored and a cursor row that left the filtered set is an invalid cursor")
    void supersededVersionsAreIgnoredAndALostCursorIsRejected() {
        PositionId one = position("PQ-1", "ACTIVE");
        PositionId two = position("PQ-2", "ACTIVE");
        position("PQ-3", "ACTIVE");
        UUID command = ids.next();
        jdbc.sql("""
                insert into organization_workforce.org_structure_commands (org_structure_command_id, command_kind, target_kind, target_position_id, effective_date, status,
                    applied_at, created_at, created_by, updated_at)
                values (:id, 'DISABLE', 'POSITION', :position, date '2026-01-01', 'EFFECTIVE', now(), now(), :actor, now())""")
                .param("id", command).param("position", one.value()).param("actor", ids.next()).update();
        jdbc.sql("""
                insert into organization_workforce.position_versions (position_version_id, position_id, effective_from_date, revision_number, code, name, status,
                    change_channel_kind, superseded_at, superseding_org_structure_command_id)
                values (:id, :position, date '2025-06-01', 1, 'PQ-1', 'withdrawn', 'DISABLED', 'MIGRATION', now(), :command)""")
                .param("id", ids.next()).param("position", one.value()).param("command", command).update();
        JdbcPositionQueries queries = new JdbcPositionQueries(jdbc, Clock.fixed(TODAY.atTime(4, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC), zone);

        assertThat(queries.list("DISABLED", null, 10)).as("the superseded DISABLED version does not count").isEmpty();
        assertThat(queries.find(one).orElseThrow().name()).isEqualTo("post PQ-1");
        assertThat(codes(queries.list(null, one, 10))).containsExactly("PQ-2", "PQ-3");
        assertThat(queries.list(null, new PositionId(last()), 10)).as("the last row as cursor is the true end").isEmpty();
        assertThatThrownBy(() -> queries.list("DISABLED", two, 10)).isInstanceOf(InvalidCursorException.class);
    }

    private UUID last() {
        return jdbc.sql("select position_id from organization_workforce.position_versions where code = 'PQ-3'").query(UUID.class).single();
    }

    private static List<String> codes(List<Row> rows) {
        return rows.stream().map(Row::code).toList();
    }

    private PositionId position(String code, String status) {
        UUID id = ids.next();
        jdbc.sql("insert into organization_workforce.positions (id, code, name, sort_order, status) values (:id, :code, :name, 1, :status)")
                .param("id", id).param("code", code).param("name", "post " + code).param("status", status).update();
        jdbc.sql("""
                insert into organization_workforce.position_versions (position_version_id, position_id, effective_from_date, revision_number, code, name, status, change_channel_kind)
                values (:version, :id, date '2025-01-01', 0, :code, :name, :status, 'MIGRATION')""")
                .param("version", ids.next()).param("id", id).param("code", code).param("name", "post " + code).param("status", status).update();
        return new PositionId(id);
    }
}
