package com.forcitis.one.platform.web;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.fixture.doubles.TestAccounts;
import static org.assertj.core.api.Assertions.assertThat;
import static com.forcitis.one.platform.approval.OrdinarySessions.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.identity.api.RoleDirectory;
import com.forcitis.one.platform.integration.api.WecomGateway;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"flowable.async-executor-activate=false", "flowable.database-schema-update=true",
        "spring.datasource.hikari.connection-timeout=2000",
        "spring.flyway.locations=classpath:db/migration,classpath:db/testdata"})
@AutoConfigureMockMvc
class HttpContractIT {
    @MockitoBean
    OrgDirectory orgDirectory;

    @MockitoBean
    RoleDirectory roleDirectory;

    @MockitoBean
    WecomGateway wecomGateway;

    @MockitoBean
    AuditLog auditLog;

    static final TestDatabase postgres = TestPostgres.database(HttpContractIT.class, TestPostgres.MIGRATED_TESTDATA).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    IdempotencyScope scope;

    @Test
    @DisplayName("the version lives in the path: /api/1/... is not a route, /api/v1/... is")
    void numericVersionSegmentIsNotFound() throws Exception {
        mvc.perform(get("/api/1/organization/legal-entities").with(user(TestAccounts.account(1).value().toString()))).andExpect(status().isNotFound())
                .andExpect(header().exists(WorkContext.HEADER));
        mvc.perform(get("/api/v2/organization/legal-entities").with(user(TestAccounts.account(1).value().toString()))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/organization/legal-entities").with(user(TestAccounts.account(1).value().toString())))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(404));
    }

    @Test
    @DisplayName("an unknown route answers a ProblemDetail with the correlation id of the request")
    void notFoundIsAProblemDetail() throws Exception {
        UUID given = UUID.randomUUID();
        mvc.perform(get("/api/1/organization/legal-entities").with(user(TestAccounts.account(1).value().toString())).header(WorkContext.HEADER, given.toString()))
                .andExpect(status().isNotFound())
                .andExpect(header().string(WorkContext.HEADER, given.toString()))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("a response field that can be null is declared nullable in the contract, a required one is not; a page's nextCursor is nullable")
    void nullableFieldsAreDeclaredNullable() throws Exception {
        tools.jackson.databind.JsonNode schemas = new tools.jackson.databind.json.JsonMapper().readTree(
                mvc.perform(get("/v3/api-docs").with(user(TestAccounts.account(1).value().toString()))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .path("components").path("schemas");

        assertThat(allowsNull(schemas.path("AuditEntryResponse").path("properties").path("detail"))).isTrue();
        assertThat(schemas.path("AuditEntryResponse").path("required").toString()).doesNotContain("\"detail\"").contains("\"action\"");
        assertThat(allowsNull(schemas.path("SourceReferenceResponse").path("properties").path("effectiveTo"))).isTrue();
        assertThat(allowsNull(schemas.path("LoginResponse").path("properties").path("administratorModeExpiresAt"))).isTrue();
        assertThat(allowsNull(schemas.path("LoginResponse").path("properties").path("userId"))).isFalse();
        assertThat(allowsNull(schemas.path("CursorPageRoleResponse").path("properties").path("nextCursor"))).isTrue();
        assertThat(schemas.path("CursorPageRoleResponse").path("required").toString()).isEqualTo("[\"items\"]");
    }

    @Test
    @DisplayName("the confirmation status of a source reference is its own component with its own values, not the one of the numbering rules")
    void sourceReferenceConfirmationStatusListsItsOwnValues() throws Exception {
        tools.jackson.databind.JsonNode schemas = new tools.jackson.databind.json.JsonMapper().readTree(
                mvc.perform(get("/v3/api-docs").with(user(TestAccounts.account(1).value().toString()))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .path("components").path("schemas");

        assertThat(schemas.path("SourceReferenceResponse").path("properties").path("confirmationStatus").path("$ref").asString())
                .isEqualTo("#/components/schemas/SourceReferenceConfirmationStatus");
        java.util.List<String> values = new java.util.ArrayList<>();
        schemas.path("SourceReferenceConfirmationStatus").path("enum").forEach(value -> values.add(value.asString()));
        assertThat(values).containsExactlyInAnyOrder("PENDING", "CONFIRMED", "REJECTED");
        java.util.List<String> numbering = new java.util.ArrayList<>();
        schemas.path("ConfirmationStatus").path("enum").forEach(value -> numbering.add(value.asString()));
        assertThat(numbering).containsExactlyInAnyOrder("PENDING_CONFIRMATION", "CONFIRMED");
    }

    /** A list that answers a bare array instead of a cursor page, with the reason: the whole tree is needed to draw the hierarchy. */
    private static final java.util.Map<String, String> UNPAGED_LISTS = java.util.Map.of(
            "/api/v1/organization/org-units", "the organisation tree is drawn and scoped as a whole and is bounded by the size of the firm");

    @Test
    @DisplayName("every list endpoint answers a cursor page with limit and cursor parameters, or is an unpaged list with its reason on record")
    void listsArePagedOrExempt() throws Exception {
        tools.jackson.databind.JsonNode paths = new tools.jackson.databind.json.JsonMapper().readTree(
                mvc.perform(get("/v3/api-docs").with(user(TestAccounts.account(1).value().toString()))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("paths");
        java.util.Set<String> unpaged = new java.util.TreeSet<>();
        int paged = 0;
        for (var path : paths.properties()) {
            String last = path.getKey().substring(path.getKey().lastIndexOf('/') + 1);
            tools.jackson.databind.JsonNode get = path.getValue().path("get");
            if (get.isMissingNode() || last.startsWith("{") || last.contains(":")) {
                continue;
            }
            tools.jackson.databind.JsonNode content = get.path("responses").path("200").path("content");
            tools.jackson.databind.JsonNode schema = content.properties().iterator().next().getValue().path("schema");
            java.util.Set<String> parameters = new java.util.TreeSet<>();
            get.path("parameters").forEach(parameter -> parameters.add(parameter.path("name").asString()));
            if ("array".equals(schema.path("type").asString())) {
                unpaged.add(path.getKey());
            } else if (schema.path("$ref").asString().contains("CursorPage")) {
                assertThat(parameters).as(path.getKey()).contains("limit", "cursor");
                paged++;
            }
        }
        assertThat(paged).isPositive();
        assertThat(unpaged).as("a list that returns a bare array needs an entry with its reason").containsExactlyInAnyOrderElementsOf(UNPAGED_LISTS.keySet());
    }

    @Test
    @DisplayName("the original-number list declares documentType as required and its two search keys as alternatives; the exact lookup is its own operation")
    void legacyNumberSearchKeysAreAlternatives() throws Exception {
        tools.jackson.databind.JsonNode paths = new tools.jackson.databind.json.JsonMapper().readTree(
                mvc.perform(get("/v3/api-docs").with(user(TestAccounts.account(1).value().toString()))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("paths");
        java.util.Map<String, Boolean> required = new java.util.TreeMap<>();
        paths.path("/api/v1/platform/legacy-numbers").path("get").path("parameters").forEach(p -> required.put(p.path("name").asString(), p.path("required").asBoolean()));

        assertThat(required).containsEntry("documentType", true).containsEntry("legacyNumberPrefix", false).containsEntry("documentId", false)
                .doesNotContainKey("legacyNumber");
        assertThat(paths.path("/api/v1/platform/legacy-numbers:resolve").path("get").isMissingNode()).isFalse();
    }

    @Test
    @DisplayName("Idempotency-Key is offered exactly where the answer is stored: on POSTs, never under /sessions or on the login states, which hand out credentials")
    void idempotencyKeyIsOfferedExactlyWhereItIsStored() throws Exception {
        tools.jackson.databind.JsonNode paths = new tools.jackson.databind.json.JsonMapper().readTree(
                mvc.perform(get("/v3/api-docs").with(user(TestAccounts.account(1).value().toString()))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("paths");
        int offered = 0;
        for (var path : paths.properties()) {
            for (var operation : path.getValue().properties()) {
                boolean declared = false;
                for (tools.jackson.databind.JsonNode parameter : operation.getValue().path("parameters")) {
                    declared |= "Idempotency-Key".equals(parameter.path("name").asString());
                }
                String method = operation.getKey().toUpperCase(java.util.Locale.ROOT);
                assertThat(declared).as(method + " " + path.getKey()).isEqualTo(scope.applies(method, path.getKey()));
                offered += declared ? 1 : 0;
            }
        }
        assertThat(offered).as("the 21 endpoints of idempotent-endpoints.txt").isEqualTo(21);
        assertThat(scope.applies("POST", "/api/v1/platform/source-references")).isFalse();
        assertThat(scope.applies("POST", "/api/v1/platform/sessions:logout")).isFalse();
        assertThat(scope.applies("POST", "/api/v1/platform/login-states")).isFalse();
        assertThat(scope.applies("PUT", "/api/v1/organization/legal-entities/1")).isFalse();
    }

    private static boolean allowsNull(tools.jackson.databind.JsonNode property) {
        for (tools.jackson.databind.JsonNode type : property.path("type")) {
            if ("null".equals(type.asString())) {
                return true;
            }
        }
        return false;
    }
}
