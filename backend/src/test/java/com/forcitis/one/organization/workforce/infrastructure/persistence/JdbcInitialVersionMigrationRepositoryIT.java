package com.forcitis.one.organization.workforce.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.organization.workforce.domain.InitialVersionPlan;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** #960 review: the lock of the initial migration, against real PostgreSQL without the application. */
class JdbcInitialVersionMigrationRepositoryIT {

    static final TestDatabase postgres = TestPostgres.database(JdbcInitialVersionMigrationRepositoryIT.class, TestPostgres.MIGRATED);

    private final DriverManagerDataSource source = new DriverManagerDataSource(postgres.url(), postgres.username(), postgres.password());
    private final TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
    private final JdbcInitialVersionMigrationRepository repository = new JdbcInitialVersionMigrationRepository(JdbcClient.create(source), UUID::randomUUID,
            new BusinessZone(ZoneId.of("Asia/Shanghai")));

    private void execute(String sql) throws SQLException {
        try (Connection c = DriverManager.getConnection(postgres.url(), postgres.username(), postgres.password()); Statement s = c.createStatement()) {
            s.execute(sql);
        }
    }

    private UUID employee() throws SQLException {
        UUID id = Uuids.timeOrdered(Instant.parse("2026-11-02T02:00:00Z"));
        execute("insert into organization_workforce.employees (id, employee_code, display_name, employment_status) values ('" + id + "', 'E-" + id
                + "', 'name', 'EMPLOYED')");
        return id;
    }

    @Test
    @DisplayName("#960 评审必改：别的事务持有员工行时，迁入在锁上等待，对方提交版本后迁入的名单不含此人")
    void aVersionCommittedWhileWaitingForTheLockLeavesThePersonOut() throws Exception {
        UUID person = employee();
        try (Connection holder = DriverManager.getConnection(postgres.url(), postgres.username(), postgres.password()); Statement s = holder.createStatement()) {
            holder.setAutoCommit(false);
            s.execute("select id from organization_workforce.employees where id = '" + person + "' for update");
            CompletableFuture<List<InitialVersionPlan.SnapshotPerson>> migration = CompletableFuture
                    .supplyAsync(() -> transaction.execute(status -> repository.lockEmployeesWithoutVersions()));
            await().atMost(Duration.ofSeconds(10)).until(() -> {
                try (Connection c = DriverManager.getConnection(postgres.url(), postgres.username(), postgres.password()); Statement q = c.createStatement()) {
                    return q.executeQuery("select 1 from pg_stat_activity where wait_event_type = 'Lock'").next();
                }
            });
            s.execute("insert into organization_workforce.legal_entities (id, code, name, status) values ('019a0000-0000-7000-8000-00000000e0e1', 'ZREMP', 'Synthetic employer', 'ACTIVE')"
                    + " on conflict (id) do nothing");
            UUID relationship = UUID.randomUUID();
            s.execute("insert into organization_workforce.employment_relationships (employment_relationship_id, employee_id, employer_legal_entity_id, employer_source_kind,"
                    + " created_at, updated_at) values ('" + relationship + "', '" + person + "', '019a0000-0000-7000-8000-00000000e0e1', 'DECLARED', now(), now())");
            s.execute("insert into organization_workforce.employment_relationship_versions (employment_relationship_version_id, employment_relationship_id, employee_id,"
                    + " effective_from_date, revision_number,"
                    + " employment_status, employment_phase_kind, is_on_unpaid_leave, is_fee_earner, employment_start_basis_kind, change_channel_kind,"
                    + " created_at, updated_at) values ('" + UUID.randomUUID() + "', '" + relationship + "', '" + person + "', date '2026-10-01', 0, 'EMPLOYED', 'REGULAR', false,"
                    + " false, 'UNKNOWN', 'MIGRATION', now(), now())");
            holder.commit();
            assertThat(migration.get()).extracting(InitialVersionPlan.SnapshotPerson::id).doesNotContain(person);
        }
    }
}
