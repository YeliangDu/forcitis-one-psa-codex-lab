package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.identity.api.RoleDirectory;
import com.forcitis.one.platform.integration.api.WecomGateway;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.servlet.HandlerMapping;

/** #730 G1: every GET handler of every HandlerMapping of the running application is in architecture/data-scope-read-paths.txt exactly once, and every line there is a mapped handler. */
@SpringBootTest(properties = {"flowable.async-executor-activate=false", "flowable.database-schema-update=true",
        "spring.datasource.hikari.connection-timeout=2000",
        "spring.flyway.locations=classpath:db/migration,classpath:db/testdata"})
class DataScopeReadPathsIT {
    @MockitoBean
    OrgDirectory orgDirectory;

    @MockitoBean
    RoleDirectory roleDirectory;

    @MockitoBean
    WecomGateway wecomGateway;

    @MockitoBean
    AuditLog auditLog;

    static final TestDatabase postgres = TestPostgres.database(DataScopeReadPathsIT.class, TestPostgres.MIGRATED_TESTDATA).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Autowired
    List<HandlerMapping> mappings;

    @Test
    @DisplayName("#730 G1: the GET handlers of the running application are exactly the lines of data-scope-read-paths.txt")
    void everyGetHandlerIsRegisteredExactlyOnce() throws Exception {
        List<String> problems = new ArrayList<>();
        List<String> handlers = DataScopeReadPaths.getHandlers(mappings, problems);
        problems.addAll(DataScopeReadPaths.problems(handlers, Files.readAllLines(DataScopeReadPathsGuardTest.REGISTER)));
        assertThat(problems).isEmpty();
    }
}
