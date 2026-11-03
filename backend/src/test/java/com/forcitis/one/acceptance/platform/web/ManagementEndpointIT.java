package com.forcitis.one.acceptance.platform.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.OrgUnitAccess;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.identity.api.RoleDirectory;
import com.forcitis.one.platform.integration.api.WecomGateway;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * D1-3b (opus review of #134, H1): the metrics scraper reaches the management port without a session. On that port the security chain
 * of the application lets health and prometheus through and refuses every other actuator endpoint; on the application port the
 * prometheus endpoint stays closed to anonymous callers. Real anonymous HTTP requests against both ports of a running server.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"flowable.async-executor-activate=false",
        "flowable.database-schema-update=true", "forcitis.platform.approval.assignment.process-administrator-user-id=9001",
        "spring.datasource.hikari.connection-timeout=2000", "management.server.port=0", "management.server.address=127.0.0.1",
        "management.endpoints.web.exposure.include=health,prometheus,env,beans,heapdump"})
class ManagementEndpointIT {

    private static final String APPLICATION_METRIC = "forcitis_organization_attendance_calendar_unmaintained_total";

    @MockitoBean
    OrgUnitAccess orgUnitAccess;

    @MockitoBean
    OrgDirectory orgDirectory;

    @MockitoBean
    RoleDirectory roleDirectory;

    @MockitoBean
    WecomGateway wecomGateway;

    @MockitoBean
    AuditLog auditLog;

    static final TestDatabase postgres = TestPostgres.database(ManagementEndpointIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @LocalServerPort
    int applicationPort;

    @LocalManagementPort
    int managementPort;

    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    private static HttpResponse<String> anonymousGet(int port, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(10)).GET().build();
        return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    @DisplayName("D1-3b 管理端口匿名抓取 prometheus 得 200 且含指标")
    void anonymousScrapeOnManagementPortReturnsMetrics() throws Exception {
        assertThat(managementPort).as("management server must listen on a port of its own").isNotEqualTo(applicationPort);

        HttpResponse<String> response = anonymousGet(managementPort, "/actuator/prometheus");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains(APPLICATION_METRIC);
    }

    @Test
    @DisplayName("D1-3b 主端口匿名访问 /actuator/prometheus 被拒")
    void anonymousScrapeOnApplicationPortIsRefused() throws Exception {
        HttpResponse<String> response = anonymousGet(applicationPort, "/actuator/prometheus");

        assertThat(response.statusCode()).isIn(401, 403, 404);
        assertThat(response.body()).doesNotContain(APPLICATION_METRIC);
    }

    @Test
    @DisplayName("D1-3b 管理端口匿名访问 env、beans、heapdump 被拒")
    void otherActuatorEndpointsOnManagementPortAreRefused() throws Exception {
        for (String path : new String[] {"/actuator/env", "/actuator/beans", "/actuator/heapdump"}) {
            HttpResponse<String> response = anonymousGet(managementPort, path);

            assertThat(response.statusCode()).as("anonymous GET %s on the management port", path).isIn(401, 403, 404);
        }
    }
}
