package com.forcitis.one.organization.workforce.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.platform.audit.api.AuditLog;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import com.forcitis.one.platform.integration.api.WecomGateway;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** One container and Spring context for all workforce integration tests; every test starts from empty snapshot tables. */
@SpringBootTest(properties = {"flowable.async-executor-activate=false"})
@AutoConfigureMockMvc
@Import(WorkforceTestSources.class)
@RecordApplicationEvents
public abstract class WorkforceIntegrationSupport {
    @ServiceConnection
    static final PostgreSQLContainer postgres = TestPostgres.container()
            .withInitScript("init-schemas.sql")
            .withUrlParam("currentSchema", "platform_approval");

    static {
        postgres.start();
    }

    @MockitoBean
    protected AuditLog audit;

    // platform.approval sends WeCom cards; integration is not wired in this test.
    @MockitoBean
    protected WecomGateway wecomGateway;

    @Autowired
    protected JdbcClient jdbc;

    @Autowired
    protected FakeOrgSource orgSource;

    @Autowired
    protected AdjustableClock clock;

    @Autowired
    protected FakeShortNameSource oldSystem;

    private WorkContext.Scope acting;

    @BeforeEach
    protected void startCountingAggregateWrites() {
        com.forcitis.one.fixture.doubles.AggregateWriteGuard.forget();
    }

    @AfterEach
    protected void oneTransactionChangesOneAggregateUnlessRegistered() {
        List<String> violations = com.forcitis.one.fixture.doubles.AggregateWriteGuard.violations();
        com.forcitis.one.fixture.doubles.AggregateWriteGuard.forget();
        assertThat(violations).as("DDD-1 one transaction changes one aggregate").isEmpty();
    }

    @BeforeEach
    protected void actAsTheTestJob() {
        acting = WorkContext.enter(UUID.randomUUID(), null, "test.organization.workforce");
    }

    @AfterEach
    protected void leaveTheTestJob() {
        acting.close();
    }

    @BeforeEach
    protected void emptySnapshot() {
        awaitEventListeners();
        VersionTables.truncate(jdbc, """
                truncate table organization_workforce.sync_diffs, organization_workforce.sync_runs,
                       organization_workforce.org_unit_legal_entities, organization_workforce.employee_legal_entities, organization_workforce.legal_entity_attributions,
                       organization_workforce.org_unit_versions, organization_workforce.assignment_versions,
                       organization_workforce.assignments, organization_workforce.employees,
                       organization_workforce.org_units, organization_workforce.positions restart identity cascade
                """);
        clock.reset();
        orgSource.unavailable = false;
        orgSource.failure = null;
        orgSource.afterRead = () -> { };
        orgSource.singleLookupResult = java.util.List.of();
        orgSource.singleLookups.set(0);
        oldSystem.unavailable = false;
        oldSystem.names = java.util.Map.of();
    }

    // TRUNCATE takes an exclusive lock: listeners of the previous test's events (identity, audit) must have finished first,
    // otherwise the two wait on each other (PessimisticLockingFailureException). Only the events of this module are waited for,
    // and a timeout names the listeners that did not finish.
    protected void awaitEventListeners() {
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(20)).pollInterval(java.time.Duration.ofMillis(20))
                .untilAsserted(() -> org.assertj.core.api.Assertions.assertThat(unfinishedListeners()).as("event listeners still running after 20 s").isEmpty());
    }

    private java.util.List<String> unfinishedListeners() {
        return jdbc.sql("""
                select listener_id || ' <- ' || event_type from platform_integration.event_publication
                 where completion_date is null and event_type like 'com.forcitis.one.organization.workforce.%'
                 order by publication_date
                """).query(String.class).list();
    }

    protected int count(String table) {
        return jdbc.sql("select count(*) from organization_workforce." + table).query(Integer.class).single();
    }
}
