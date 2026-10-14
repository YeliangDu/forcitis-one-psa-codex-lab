package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.platform.audit.api.AuditLog;
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

/**
 * Whole-application context on PostgreSQL 18 with in-memory the source / old-system stand-ins and a mocked audit trail
 * (platform.audit is a separate module). One container for all workforce integration tests: they share one
 * Spring context, and every test starts from empty snapshot tables.
 */
@SpringBootTest(properties = {"flowable.async-executor-activate=false"})
@AutoConfigureMockMvc
@Import(WorkforceTestSources.class)
@RecordApplicationEvents
public abstract class WorkforceIntegrationSupport {

    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18")
            .withInitScript("init-schemas.sql")
            .withUrlParam("currentSchema", "platform_approval");

    static {
        postgres.start();
    }

    @MockitoBean
    protected AuditLog audit;

    // platform.approval (same application context) sends WeCom cards; integration is not wired in this test.
    @MockitoBean
    protected WecomGateway wecomGateway;

    @Autowired
    protected JdbcClient jdbc;

    @Autowired
    protected FakeOrgSource orgSource;

    @Autowired
    protected FakeShortNameSource oldSystem;

    @BeforeEach
    protected void emptySnapshot() {
        jdbc.sql("""
                truncate table organization_workforce.sync_diffs, organization_workforce.sync_runs,
                       organization_workforce.assignments, organization_workforce.employees,
                       organization_workforce.org_units, organization_workforce.positions restart identity cascade
                """).update();
        orgSource.unavailable = false;
        orgSource.failure = null;
        orgSource.singleLookupResult = java.util.List.of();
        orgSource.singleLookups.set(0);
        oldSystem.unavailable = false;
        oldSystem.names = java.util.Map.of();
    }

    protected int count(String table) {
        return jdbc.sql("select count(*) from organization_workforce." + table).query(Integer.class).single();
    }
}
