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

    /** Ids of this system (R-1a) are string / uuid on the wire; the rest are named here until R-1b gives them their own ids. */
    static final java.util.Set<String> OWN_ID_FIELDS = java.util.Set.of("AttributeLegalEntityRequest.legalEntityId", "AuditEntryResponse.entryId",
            "AuthorizationRequestView.requestId", "AuthorizationRequestView.roleId", "HandledTaskResponse.recordId", "HistoryEntry.recordId",
            "LegacyNumberResponse.documentId", "LegacyNumberResponse.legalEntityId", "LegacyNumberResponse.migrationBatchId", "LegalEntityResponse.legalEntityId", "Period.legalEntityId",
            "RoleResponse.roleId", "SyncRunResponse.syncRunId", "SyncRunResponse.releasedByRunId", "ProblemDetail.correlationId",
            "RegisterSourceReferenceRequest.ownId", "SourceReferenceResponse.ownId", "SourceReferenceResponse.sourceReferenceId",
            "parameter legalEntityId", "parameter syncRunId", "parameter requestId", "parameter roleId", "parameter sourceReferenceId", "parameter documentId /api/v1/platform/legacy-numbers");

    /** Ids taken from the organisation source (A8) and the engine's task, instance and document keys: R-1b, listed by name so a new id field has to be classified. */
    static final java.util.Set<String> OTHER_ID_NAMES = java.util.Set.of("actorId", "onBehalfOfId", "subjectUserId", "userId", "orgUnitId", "employeeId",
            "headEmployeeId", "parentOrgUnitId", "regionalReceivablesManagerId", "ancestorOrgUnitIds", "orgUnitAndBelowIds", "authorId", "assigneeId",
            "subjectId", "taskId", "instanceId", "documentId", "sourceId");

    @Test
    void idFieldsOfThisSystemAreStringUuidAndEveryOtherIdFieldIsClassified() throws Exception {
        JsonNode document = json.readTree(mvc.perform(get("/v3/api-docs").with(user("1"))).andReturn().getResponse().getContentAsString());
        java.util.Map<String, JsonNode> idFields = new java.util.TreeMap<>();
        document.path("components").path("schemas").properties().forEach(schema -> schema.getValue().path("properties").properties().forEach(property -> {
            if (isIdName(property.getKey())) {
                idFields.put(schema.getKey() + "." + property.getKey(), property.getValue().has("items") ? property.getValue().path("items") : property.getValue());
            }
        }));
        document.path("paths").properties().forEach(path -> path.getValue().properties().forEach(operation -> operation.getValue().path("parameters").forEach(parameter -> {
            String name = parameter.path("name").asString();
            if (isIdName(name)) {
                idFields.put("parameter " + name + (name.equals("documentId") ? " " + path.getKey() : ""), parameter.path("schema"));
            }
        })));
        assertThat(idFields).as("the document has id fields").isNotEmpty();

        java.util.List<String> problems = new java.util.ArrayList<>();
        idFields.forEach((key, schema) -> {
            if (OWN_ID_FIELDS.contains(key)) {
                boolean stringType = schema.path("type").isArray() ? schema.path("type").toString().contains("\"string\"") : schema.path("type").asString().equals("string");
                if (!stringType || !schema.path("format").asString().equals("uuid")) {
                    problems.add(key + " is an id of this system and must be string / uuid, found " + schema.path("type") + " / " + schema.path("format"));
                }
                if (schema.has("example")) {
                    try {
                        com.forcitis.one.kernel.Uuids.parseCanonical(schema.path("example").asString());
                    } catch (IllegalArgumentException notCanonical) {
                        problems.add(key + " has an example that is not a lower-case canonical UUID");
                    }
                }
            } else if (!OTHER_ID_NAMES.contains(key.replaceFirst("^parameter ", "").replaceFirst("^.*\\.", "").replaceFirst(" /.*$", ""))) {
                problems.add(key + " looks like an id: list it in OWN_ID_FIELDS (string / uuid) or, until R-1b, in OTHER_ID_NAMES");
            }
        });
        assertThat(problems).isEmpty();
    }

    private static boolean isIdName(String name) {
        return name.equals("id") || name.endsWith("Id") || name.endsWith("Ids");
    }
}
