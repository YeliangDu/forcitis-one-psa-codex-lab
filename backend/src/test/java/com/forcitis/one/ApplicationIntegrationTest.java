package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.PublicEndpoint;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.integration.api.WecomGateway;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.method.HandlerMethod;
import com.forcitis.one.platform.identity.api.Permission;
import com.forcitis.one.platform.identity.api.RoleDirectory;
import com.forcitis.one.platform.numbering.api.DocumentNumber;
import com.forcitis.one.platform.numbering.api.DocumentNumbers;
import com.forcitis.one.platform.numbering.api.DocumentType;
import com.tngtech.archunit.core.domain.JavaClass;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Boot 4.1 + Flowable 8 + PostgreSQL 17: security default-deny, API versioning, typed outcomes, engine schema. */
@SpringBootTest(properties = {"flowable.async-executor-activate=false", "flowable.database-schema-update=true",
        "forcitis.platform.approval.assignment.process-administrator-user-id=9001",
        // The cached context is closed by the JVM shutdown hook after the container is gone; without a short timeout the
        // engine's close command (and the event publication registry) wait 30 s for a connection that can never come.
        "spring.datasource.hikari.connection-timeout=2000",
        "spring.flyway.locations=classpath:db/migration,classpath:db/testdata"})
@AutoConfigureMockMvc
@Testcontainers
class ApplicationIntegrationTest {

    // platform.approval resolves approvers from the organisation snapshot and roles (provided by other modules in the
    // finished application); here empty doubles stand in, so every task goes to the process administrator.
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

    static final LegalEntityId LE1 = new LegalEntityId(1L);

    static final String SUBMIT = "/api/v1/cost/timesheets/7:submit";
    static final String BODY = "{\"workPackageId\":1,\"hours\":8}";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    DocumentNumbers numbers;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    JsonMapper json;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping handlerMapping;

    /**
     * Anonymous access (SECURITY_INVARIANTS I-3, REIMAGINED_ARCHITECTURE §3.8, revised wording of the exit condition):
     * every handler that is not @PublicEndpoint answers 401; the @PublicEndpoint handlers are exactly the reviewed
     * snapshot architecture/public-endpoints.txt (same lines, same reasons); a public handler never grants anything to a
     * caller who proves nothing.
     */
    @Test
    void anonymousCallersAreRefusedExceptOnTheReviewedPublicEndpoints() throws Exception {
        List<String> checked = new ArrayList<>();
        List<String> publicActual = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> mapped : handlerMapping.getHandlerMethods().entrySet()) {
            PublicEndpoint publicEndpoint = mapped.getValue().getMethodAnnotation(PublicEndpoint.class);
            for (String pattern : mapped.getKey().getPatternValues()) {
                String url = pattern.replace("{version}", "v1").replaceAll("\\{[^}]+}", "1");
                for (var method : mapped.getKey().getMethodsCondition().getMethods()) {
                    var request = request(HttpMethod.valueOf(method.name()), url).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content("{}");
                    if (publicEndpoint == null) {
                        mvc.perform(request).andExpect(status().isUnauthorized());
                        checked.add(method + " " + url);
                    } else {
                        publicActual.add(method + " " + pattern + "  # " + publicEndpoint.reason());
                        int status = mvc.perform(request).andReturn().getResponse().getStatus();
                        assertThat(status).as("%s %s proves nothing, so it must not log anyone in", method, url)
                                .isGreaterThanOrEqualTo(400).isNotEqualTo(500);
                    }
                }
            }
        }
        assertThat(checked).isNotEmpty();
        List<String> snapshot = Files.readAllLines(Path.of("architecture/public-endpoints.txt")).stream()
                .map(String::strip).filter(line -> !line.isEmpty() && !line.startsWith("#")).toList();
        assertThat(publicActual).as("@PublicEndpoint handlers must equal architecture/public-endpoints.txt (CODEOWNERS review)")
                .containsExactlyInAnyOrderElementsOf(snapshot);
    }

    @Test
    void writesWithoutACsrfTokenAreForbidden() throws Exception {
        mvc.perform(post(SUBMIT).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());
        mvc.perform(post(SUBMIT).with(user("1001").authorities(() -> Permission.Codes.TIMESHEET_SUBMIT))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingPermissionIsForbidden() throws Exception {
        mvc.perform(post(SUBMIT).with(csrf()).with(user("1001").authorities(() -> Permission.Codes.TIMESHEET_APPROVE))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void submitStartsFlowableApprovalInItsOwnSchema() throws Exception {
        String body = mvc.perform(post(SUBMIT).with(csrf()).with(user("1001").authorities(() -> Permission.Codes.TIMESHEET_SUBMIT))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("SUBMITTED"))
                .andReturn().getResponse().getContentAsString();
        String instanceId = JsonMapper.builder().build().readTree(body).get("approvalInstanceId").asString();

        assertThat(count("platform_approval")).isGreaterThan(20);
        assertThat(count("public")).isZero();
        // The skeleton timesheet routes project hours only and carries no project manager: that single branch is skipped.
        assertThat(jdbc.queryForList("""
                select t.task_def_key_ from platform_approval.act_hi_taskinst t
                  join platform_approval.act_hi_varinst v on v.task_id_ = t.id_ and v.name_ = 'skippedWhenEmpty'
                 where t.proc_inst_id_ = ?""", String.class, instanceId))
                .containsExactly("projectManagerReview");
        assertThat(jdbc.queryForObject("select count(*) from platform_approval.act_hi_taskinst where proc_inst_id_ = ?",
                Integer.class, instanceId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from platform_approval.act_ru_task where proc_inst_id_ = ?",
                Integer.class, instanceId)).isZero();
        assertThat(jdbc.queryForObject("select end_time_ is not null from platform_approval.act_hi_procinst where id_ = ?",
                Boolean.class, instanceId)).isTrue();
    }

    @Test
    void typedOutcomesMapToHttpStatuses() throws Exception {
        mvc.perform(post(SUBMIT).with(csrf()).with(user("1001").authorities(() -> Permission.Codes.TIMESHEET_SUBMIT))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"workPackageId\":1,\"hours\":31}"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.outcome").value("EXCEEDS_REMAINING_HOURS"))
                .andExpect(jsonPath("$.errorCode").value("COST_TIMESHEET_EXCEEDS_REMAINING_HOURS"));
        mvc.perform(post(SUBMIT).with(csrf()).with(user("1001").authorities(() -> Permission.Codes.TIMESHEET_SUBMIT))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"workPackageId\":99,\"hours\":1}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.errorCode").value("COST_TIMESHEET_UNKNOWN_WORK_PACKAGE"));
    }

    @Test
    void unsupportedApiVersionIsRejected() throws Exception {
        mvc.perform(post("/api/v2/cost/timesheets/7:submit").with(csrf()).with(user("1001").authorities(() -> Permission.Codes.TIMESHEET_SUBMIT))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest());
    }

    // ---------- configurable business numbering ----------

    @Test
    void numbersFollowTheRuleResetYearlyAndAreSeparatePerLegalEntity() {
        LocalDate day = LocalDate.of(2026, 10, 14);
        // unified new format (ADR-0010 decision 5): {TYPE}-{LE}-{yyyyMM}-{seq:4}, reset monthly
        assertThat(issue(DocumentType.QUOTE, LE1, day)).isEqualTo("QT-1-202610-0001");
        assertThat(issue(DocumentType.QUOTE, LE1, day)).isEqualTo("QT-1-202610-0002");
        assertThat(issue(DocumentType.QUOTE, new LegalEntityId(2L), day)).isEqualTo("QT-2-202610-0001");
        assertThat(issue(DocumentType.QUOTE, LE1, LocalDate.of(2026, 11, 2))).isEqualTo("QT-1-202611-0001");
        assertThat(issue(DocumentType.JOURNAL_VOUCHER, LE1, day)).isEqualTo("JV-1-202610-000001");
    }

    @Test
    void rolledBackNumbersAreReissuedSoThereAreNoGaps() {
        LocalDate day = LocalDate.of(2026, 3, 1);
        String first = issue(DocumentType.TIMESHEET, LE1, day);
        String rolledBack = tx.execute(status -> {
            String n = numbers.next(DocumentType.TIMESHEET, LE1, day).value();
            status.setRollbackOnly();
            return n;
        });
        assertThat(first).isEqualTo("TS202603010001");
        assertThat(rolledBack).isEqualTo("TS202603010002");
        assertThat(issue(DocumentType.TIMESHEET, LE1, day)).isEqualTo("TS202603010002");
    }

    @Test
    void concurrentIssuingNeverDuplicatesOrSkips() throws Exception {
        LocalDate day = LocalDate.of(2026, 5, 5);
        Set<String> issued = ConcurrentHashMap.newKeySet();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        for (int i = 0; i < 20; i++) {
            pool.submit(() -> issued.add(issue(DocumentType.TIMESHEET, LE1, day)));
        }
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        assertThat(issued).hasSize(20).contains("TS202605050001", "TS202605050020");
    }

    @Test
    void issuingOutsideTheCallersTransactionIsRefused() {
        assertThatThrownBy(() -> numbers.next(DocumentType.QUOTE, LE1, LocalDate.now()))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void adminCanRedefineRulesButInvalidPatternsAreRejected() throws Exception {
        var admin = user("9").authorities(() -> Permission.Codes.PLATFORM_NUMBERING_RULE_UPDATE);
        mvc.perform(put("/api/v1/platform/numbering-rules/JOURNAL_VOUCHER").with(csrf()).with(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pattern\":\"JV-{seq:6}\",\"resetPolicy\":\"MONTHLY\",\"startValue\":1}"))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(put("/api/v1/platform/numbering-rules/JOURNAL_VOUCHER").with(csrf()).with(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pattern\":\"PZ{LE}-{yyyyMM}-{seq:5}\",\"resetPolicy\":\"MONTHLY\",\"startValue\":500}"))
                .andExpect(status().isNoContent());
        assertThat(issue(DocumentType.JOURNAL_VOUCHER, LE1, LocalDate.of(2026, 11, 1))).isEqualTo("PZ1-202611-00500");
        mvc.perform(put("/api/v1/platform/numbering-rules/JOURNAL_VOUCHER").with(csrf()).with(user("8").authorities(() -> Permission.Codes.TIMESHEET_SUBMIT))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pattern\":\"X{seq:2}\",\"resetPolicy\":\"NEVER\",\"startValue\":1}"))
                .andExpect(status().isForbidden());
    }

    // ---------- G42: every JSON name is camelCase and made of glossary words ----------

    @Test
    void openApiSchemaNamesArePascalCaseAndJsonPropertiesAndParametersCamelCaseGlossaryWords() throws Exception {
        String body = mvc.perform(get("/v3/api-docs").with(user("1"))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode doc = json.readTree(body);
        JsonNode schemas = doc.path("components").path("schemas");
        assertThat(schemas.size()).as("springdoc found no schemas: the guard would pass vacuously").isGreaterThan(0);
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, JsonNode> schema : schemas.properties()) {
            String name = schema.getKey();
            if (!name.matches("[A-Z][a-zA-Z0-9]*")) {
                problems.add("schema " + name + ": must be PascalCase (the request / response record name)");
            }
            words("schema " + name + ": ", name, problems);
            jsonProperties("schema " + name, schema.getValue(), problems);
        }
        for (Map.Entry<String, JsonNode> path : doc.path("paths").properties()) {
            for (Map.Entry<String, JsonNode> operation : path.getValue().properties()) {
                for (JsonNode parameter : operation.getValue().path("parameters")) {
                    String name = parameter.path("name").asString();
                    camelCase(operation.getKey().toUpperCase() + " " + path.getKey() + " " + parameter.path("in").asString()
                            + " parameter '" + name + "'", name, problems);
                }
            }
        }
        failG42("OpenAPI contract (/v3/api-docs)", problems);
    }

    @Test
    void serializedDomainEventKeysAreCamelCaseGlossaryWords() {
        List<String> problems = new ArrayList<>();
        int events = 0;
        for (JavaClass c : ArchitectureRulesTest.CLASSES) {
            if (!c.getPackageName().matches(".*\\.events(\\..*)?") || !c.isRecord()) {
                continue;
            }
            events++;
            JsonNode payload = json.readTree(json.writeValueAsString(sample(c.reflect())));
            jsonKeys(c.getSimpleName(), payload, problems);
        }
        assertThat(events).as("no event records found: the guard would pass vacuously").isGreaterThan(0);
        failG42("serialized domain events (application JsonMapper)", problems);
    }

    private static void jsonProperties(String where, JsonNode schema, List<String> problems) {
        for (Map.Entry<String, JsonNode> p : schema.path("properties").properties()) {
            camelCase(where + "." + p.getKey(), p.getKey(), problems);
            jsonProperties(where + "." + p.getKey(), p.getValue(), problems);
        }
        for (String nested : List.of("items", "additionalProperties")) {
            if (schema.path(nested).isObject()) {
                jsonProperties(where + "[]", schema.path(nested), problems);
            }
        }
        for (String composed : List.of("allOf", "oneOf", "anyOf")) {
            for (JsonNode part : schema.path(composed)) {
                jsonProperties(where, part, problems);
            }
        }
    }

    private static void jsonKeys(String where, JsonNode node, List<String> problems) {
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> p : node.properties()) {
                camelCase(where + "." + p.getKey(), p.getKey(), problems);
                jsonKeys(where + "." + p.getKey(), p.getValue(), problems);
            }
        } else if (node.isArray()) {
            node.forEach(child -> jsonKeys(where + "[]", child, problems));
        }
    }

    private static void camelCase(String where, String name, List<String> problems) {
        if (!name.matches("[a-z][a-zA-Z0-9]*")) {
            problems.add(where + ": JSON names are camelCase (snake_case only in the database, NAMING_STANDARD.md §2.3)");
        }
        words(where + ": ", name, problems);
    }

    private static void words(String where, String identifier, List<String> problems) {
        List<String> missing = Glossary.unregisteredWords(identifier);
        if (!missing.isEmpty()) {
            problems.add(where + Glossary.registerHint(missing));
        }
    }

    private static void failG42(String what, List<String> problems) {
        if (!problems.isEmpty()) {
            throw new AssertionError("G42 JSON naming in " + what + " (PascalCase schemas, camelCase glossary-word properties):\n  "
                    + String.join("\n  ", new java.util.LinkedHashSet<>(problems)));
        }
    }

    /** A valid instance of an event record: records are filled recursively with small positive / non-blank values. */
    private static Object sample(Class<?> type) {
        if (type == long.class || type == Long.class) {
            return 1L;
        }
        if (type == int.class || type == Integer.class) {
            return 1;
        }
        if (type == boolean.class || type == Boolean.class) {
            return true;
        }
        if (type == String.class) {
            return "x";
        }
        if (type == BigDecimal.class) {
            return BigDecimal.ONE;
        }
        if (type == LocalDate.class) {
            return LocalDate.of(2026, 10, 14);
        }
        if (type == java.time.Instant.class) {
            return java.time.Instant.parse("2026-10-14T00:00:00Z");
        }
        if (type == java.util.Currency.class) {
            return java.util.Currency.getInstance("CNY");
        }
        if (Set.class.isAssignableFrom(type)) {
            return Set.of();
        }
        if (List.class.isAssignableFrom(type)) {
            return List.of();
        }
        if (type.isEnum()) {
            return type.getEnumConstants()[0];
        }
        if (type.isRecord()) {
            RecordComponent[] components = type.getRecordComponents();
            Class<?>[] types = new Class<?>[components.length];
            Object[] args = new Object[components.length];
            for (int i = 0; i < components.length; i++) {
                types[i] = components[i].getType();
                args[i] = sample(types[i]);
            }
            try {
                var constructor = type.getDeclaredConstructor(types);
                constructor.setAccessible(true);
                return constructor.newInstance(args);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("cannot build a sample " + type.getName() + "; extend sample()", e);
            }
        }
        throw new IllegalStateException("no sample value for " + type.getName() + " in an event payload; extend sample()");
    }

    // ---------- data dictionary generated from the database ----------

    @Test
    void everyTableAndColumnIsDocumentedAndTheDictionaryIsGenerated() throws Exception {
        var rows = jdbc.queryForList("""
                select c.table_schema, c.table_name, c.column_name, c.data_type, c.is_nullable,
                       col_description(format('%I.%I', c.table_schema, c.table_name)::regclass, c.ordinal_position) as column_comment,
                       obj_description(format('%I.%I', c.table_schema, c.table_name)::regclass, 'pg_class') as table_comment
                  from information_schema.columns c
                 where c.table_schema in ('delivery_execution', 'platform_numbering', 'platform_scheduling')
                 order by c.table_schema, c.table_name, c.ordinal_position
                """);
        assertThat(rows).isNotEmpty();
        assertThat(rows).allSatisfy(r -> {
            assertThat(r.get("column_comment")).as("comment on %s.%s.%s", r.get("table_schema"), r.get("table_name"), r.get("column_name")).isNotNull();
            assertThat(r.get("table_comment")).isNotNull();
        });
        StringBuilder md = new StringBuilder("# Data dictionary (generated from PostgreSQL catalog)\n");
        String current = "";
        for (Map<String, Object> r : rows) {
            String table = r.get("table_schema") + "." + r.get("table_name");
            if (!table.equals(current)) {
                current = table;
                md.append("\n## ").append(table).append(" — ").append(r.get("table_comment"))
                        .append("\n\n| column | type | nullable | description |\n|---|---|---|---|\n");
            }
            md.append("| ").append(r.get("column_name")).append(" | ").append(r.get("data_type")).append(" | ")
                    .append(r.get("is_nullable")).append(" | ").append(r.get("column_comment")).append(" |\n");
        }
        Files.writeString(Path.of("target/data-dictionary.md"), md);
    }

    private String issue(DocumentType type, LegalEntityId legalEntity, LocalDate day) {
        DocumentNumber n = tx.execute(status -> numbers.next(type, legalEntity, day));
        return n.value();
    }

    private Integer count(String schema) {
        return jdbc.queryForObject("select count(*) from information_schema.tables where table_schema = ? and table_name like 'act_%'",
                Integer.class, schema);
    }
}
