package com.forcitis.one.organization.workforce.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.organization.workforce.domain.CommandReadQueries;
import com.forcitis.one.organization.workforce.domain.CommandReadQueries.Position;
import com.forcitis.one.organization.workforce.domain.CommandReadQueries.Scope;
import com.forcitis.one.platform.integration.api.WecomGateway;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** #1172: rows created at the same instant keep one order across pages; the HTTP ITs cannot make ties, so the rows are inserted here, in reverse id order. */
@SpringBootTest(properties = {"flowable.async-executor-activate=false"})
class CommandReadPagingIT {
    @ServiceConnection
    static final PostgreSQLContainer postgres = TestPostgres.container().withInitScript("init-schemas.sql").withUrlParam("currentSchema", "platform_approval");

    static {
        postgres.start();
    }

    private static final Instant SAME = Instant.parse("2026-01-01T00:00:00Z");

    @MockitoBean
    WecomGateway wecomGateway;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    IdGenerator ids;

    @Autowired
    CommandReadQueries queries;

    @Test
    @DisplayName("#1172 personnel commands created together are paged by command id, one row per page")
    void personnelCommandsWithTheSameCreationInstant() {
        List<UUID> expected = descendingIds(3).reversed();
        for (UUID id : expected.reversed()) {
            personnelCommand(id, employee(), "REGULARIZE", "PENDING");
        }
        assertThat(pages(after -> queries.personnel(new Scope(true, Set.of()), null, null, null, after, 1), row -> row.personnelCommandId().value(), expected)).containsExactlyElementsOf(expected);
    }

    @Test
    @DisplayName("#1172 org-structure commands created together are paged by command id, one row per page")
    void orgStructureCommandsWithTheSameCreationInstant() {
        List<UUID> expected = descendingIds(3).reversed();
        for (UUID id : expected.reversed()) {
            UUID position = ids.next();
            jdbc.sql("insert into organization_workforce.positions (id, code, name, status) values (:id, :code, 'Example position', 'ACTIVE')")
                    .param("id", position).param("code", "PG-" + position).update();
            jdbc.sql("""
                    insert into organization_workforce.org_structure_commands (org_structure_command_id, command_kind, target_kind, target_position_id, effective_date, status,
                      created_at, created_by, updated_at)
                    values (:id, 'DISABLE', 'POSITION', :position, date '2026-12-01', 'PENDING', :at, :by, :at)
                    """).param("id", id).param("position", position).param("at", java.time.OffsetDateTime.ofInstant(SAME, java.time.ZoneOffset.UTC)).param("by", ids.next()).update();
        }
        assertThat(pages(after -> queries.structures(after, 1), row -> row.orgStructureCommandId().value(), expected)).containsExactlyElementsOf(expected);
    }

    @Test
    @DisplayName("#1172 held separations created together are paged by hold id, one row per page")
    void holdsWithTheSameCreationInstant() {
        List<UUID> expected = descendingIds(3).reversed();
        for (UUID id : expected.reversed()) {
            UUID employee = employee();
            UUID command = ids.next();
            personnelCommand(command, employee, "SEPARATE", "HELD");
            jdbc.sql("""
                    insert into organization_workforce.separation_holds (separation_hold_id, personnel_command_id, employee_id, status, created_at, updated_at)
                    values (:id, :command, :employee, 'HELD', :at, :at)
                    """).param("id", id).param("command", command).param("employee", employee).param("at", java.time.OffsetDateTime.ofInstant(SAME, java.time.ZoneOffset.UTC)).update();
        }
        assertThat(pages(after -> queries.holds(after, 1), row -> row.separationHoldId().value(), expected)).containsExactlyElementsOf(expected);
    }

    /** Ids that sort ascending in the returned list but whose descending order is the insertion order. */
    private List<UUID> descendingIds(int count) {
        List<UUID> found = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            found.add(ids.next());
        }
        found.sort(java.util.Comparator.reverseOrder());
        return found;
    }

    /** One row per page; only the ids this test inserted are kept, because the other cases of this class share the instant and the database. */
    private <T> List<UUID> pages(Function<Position, List<T>> page, Function<T, UUID> id, List<UUID> own) {
        List<UUID> seen = new ArrayList<>();
        Position after = null;
        for (int guard = 0; guard < 1000; guard++) {
            List<T> rows = page.apply(after);
            if (rows.isEmpty()) {
                return seen;
            }
            rows.stream().map(id).filter(own::contains).forEach(seen::add);
            T last = rows.getLast();
            after = new Position(SAME, id.apply(last));
        }
        throw new AssertionError("more than 1000 pages");
    }

    private UUID employee() {
        UUID employee = ids.next();
        jdbc.sql("insert into organization_workforce.employees (id, employee_code, display_name, employment_status) values (:id, :code, 'Example', 'EMPLOYED')")
                .param("id", employee).param("code", "PG-" + employee).update();
        return employee;
    }

    private void personnelCommand(UUID id, UUID employee, String kind, String status) {
        jdbc.sql("""
                insert into organization_workforce.personnel_commands (personnel_command_id, employee_id, command_kind, effective_date, status, created_at, created_by, updated_at)
                values (:id, :employee, :kind, date '2026-12-01', :status, :at, :by, :at)
                """).param("id", id).param("employee", employee).param("kind", kind).param("status", status)
                .param("at", java.time.OffsetDateTime.ofInstant(SAME, java.time.ZoneOffset.UTC)).param("by", ids.next()).update();
    }
}
