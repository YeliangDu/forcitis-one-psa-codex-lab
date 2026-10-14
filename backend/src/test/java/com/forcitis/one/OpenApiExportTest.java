package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.identity.api.RoleDirectory;
import com.forcitis.one.platform.integration.api.WecomGateway;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.SerializationFeature;

/** Keys are sorted so the file only changes when the contract does. */
@SpringBootTest(properties = {"flowable.async-executor-activate=false", "flowable.database-schema-update=true",
        "forcitis.platform.approval.assignment.process-administrator-user-id=9001",
        "spring.datasource.hikari.connection-timeout=2000",
        "spring.flyway.locations=classpath:db/migration,classpath:db/testdata"})
@AutoConfigureMockMvc
@Testcontainers
class OpenApiExportTest {
    @MockitoBean
    OrgDirectory orgDirectory;

    @MockitoBean
    RoleDirectory roleDirectory;

    @MockitoBean
    WecomGateway wecomGateway;

    @MockitoBean
    AuditLog auditLog;

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18")
            .withInitScript("init-schemas.sql")
            .withUrlParam("currentSchema", "platform_approval");

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper json;

    @Test
    void exportsTheOpenApiDocumentForTheFrontendClient() throws Exception {
        String body = mvc.perform(get("/v3/api-docs").with(user("1"))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode document = json.readTree(body);
        assertThat(document.path("paths").size()).as("springdoc found no paths").isGreaterThan(0);
        Path target = Path.of("..", "frontend", "packages", "api", "openapi.json");
        Files.createDirectories(target.getParent());
        JsonMapper sorted = json.rebuild().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .enable(SerializationFeature.INDENT_OUTPUT).build();
        Files.writeString(target, sorted.writeValueAsString(sorted.treeToValue(document, Object.class)) + "\n");
    }

    @Test
    void pathsUseTheVersionSegmentAndResponseComponentsAreRequiredUnlessNullable() throws Exception {
        JsonNode document = json.readTree(mvc.perform(get("/v3/api-docs").with(user("1"))).andReturn().getResponse().getContentAsString());
        document.path("paths").propertyNames().forEach(path -> assertThat(path).startsWith("/api/v1/"));
        JsonNode syncRun = document.path("components").path("schemas").path("SyncRunResponse").path("required");
        assertThat(json.convertValue(syncRun, String[].class)).contains("syncRunId", "status", "startedAt")
                .doesNotContain("finishedAt", "heldReason", "releasedBy");
        assertThat(document.path("components").path("schemas").path("DecideTaskRequest").path("required").toString()).doesNotContain("reason");
    }
}
