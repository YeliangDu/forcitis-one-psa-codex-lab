package com.forcitis.one;

import com.forcitis.one.fixture.doubles.TestAccounts;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.PublicEndpoint;
import com.forcitis.one.organization.workforce.api.EmployeeProfile;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.integration.api.WecomGateway;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.method.HandlerMethod;
import com.forcitis.one.platform.identity.api.Permission;
import com.forcitis.one.platform.identity.api.RoleDirectory;
import com.forcitis.one.platform.numbering.api.DocumentNumber;
import com.forcitis.one.platform.numbering.api.DocumentNumbers;
import com.forcitis.one.kernel.DocumentTypeCode;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.forcitis.one.fixture.doubles.TestOrgIds;

@SpringBootTest(properties = {"flowable.async-executor-activate=false", "flowable.database-schema-update=true",
        // The cached context closes after the container is gone; without a short timeout the engine's close waits 30 s for a connection.
        "spring.datasource.hikari.connection-timeout=2000",
        "spring.flyway.locations=classpath:db/migration,classpath:db/testdata"})
@AutoConfigureMockMvc
class ApplicationIntegrationTest {
    // Doubles stand in for the organisation: only the project manager 2002 exists.
    @MockitoBean
    OrgDirectory orgDirectory;

    @MockitoBean
    RoleDirectory roleDirectory;

    @MockitoBean
    com.forcitis.one.organization.workforce.api.EmployeeLegalEntities employeeLegalEntities;

    @MockitoBean
    WecomGateway wecomGateway;

    @MockitoBean
    AuditLog auditLog;

    static final TestDatabase postgres = TestPostgres.database(ApplicationIntegrationTest.class, TestPostgres.MIGRATED_TESTDATA).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    /** HTTP header names are fixed by RFC 9110/6266 and the Idempotency-Key convention; they are not JSON names. */
    static final java.util.Set<String> STANDARD_HEADERS = java.util.Set.of("If-Match", "If-None-Match", "Idempotency-Key", "Content-Disposition");

    static final LegalEntityId LE1 = new LegalEntityId(TestIds.uuid(1));

    static final String SUBMIT = "/api/v1/cost/timesheets/7:submit";
    static final String BODY = "{\"hours\":8,\"projectManagerId\":\"" + TestAccounts.employee(2002).value() + "\"}";

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

    /** I-3: only @PublicEndpoint handlers answer anonymously, exactly those in architecture/public-endpoints.txt; the rest answer 401. */
    @Test
    void anonymousCallersAreRefusedExceptOnTheReviewedPublicEndpoints() throws Exception {
        List<String> checked = new ArrayList<>();
        List<String> publicActual = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> mapped : handlerMapping.getHandlerMethods().entrySet()) {
            PublicEndpoint publicEndpoint = mapped.getValue().getMethodAnnotation(PublicEndpoint.class);
            for (String pattern : mapped.getKey().getPatternValues()) {
                String url = pattern.replaceAll("\\{[^}]+}", "1");
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
        mvc.perform(post(SUBMIT).with(user(TestAccounts.account(1001).value().toString()).authorities(() -> Permission.Codes.TIMESHEET_SUBMIT))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void flowableLivesInItsOwnSchema() {
        assertThat(count("platform_approval")).isGreaterThan(20);
        assertThat(count("public")).isZero();
    }

    @Test
    void unsupportedApiVersionIsNotARoute() throws Exception {
        mvc.perform(post("/api/v2/cost/timesheets/7:submit").with(csrf()).with(user(TestAccounts.account(1001).value().toString()).authorities(() -> Permission.Codes.TIMESHEET_SUBMIT))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void bareNumericVersionSegmentIsNotARoute() throws Exception {
        mvc.perform(post("/api/1/cost/timesheets/7:submit").with(csrf()).with(user(TestAccounts.account(1001).value().toString()).authorities(() -> Permission.Codes.TIMESHEET_SUBMIT))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound());
    }

    @BeforeEach
    void projectManagerExists() {
        org.mockito.Mockito.when(orgDirectory.findEmployee(TestAccounts.employee(2002))).thenReturn(java.util.Optional.of(new EmployeeProfile(
                TestAccounts.employee(2002), "person-2002", TestOrgIds.orgUnit(1), null, EmploymentStatus.EMPLOYED, false)));
    }

    @BeforeEach
    void everyEmployeeBelongsToLegalEntityOne() {
        org.mockito.Mockito.when(employeeLegalEntities.on(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(LE1);
    }

    @BeforeEach
    void legalEntitiesExist() {
        jdbc.update("""
                insert into organization_workforce.legal_entities (id, code, name, status)
                values ('%s', 'FST', 'Synthetic legal entity one', 'ACTIVE'), ('%s', 'FIT', 'Synthetic legal entity two', 'ACTIVE')
                on conflict (id) do nothing
                """.formatted(TestIds.uuid(1), TestIds.uuid(2)));
    }

    @BeforeEach
    void seededNumberingRulesAreConfirmed() {
        jdbc.update("""
                update platform_numbering.numbering_rule_versions
                   set status = 'CONFIRMED', confirmed_by = '01900000-0000-7000-8000-000000000101', confirmed_at = now(),
                       finance_confirmed_by = case when (select t.code from platform_numbering.numbering_rules r join platform_numbering.document_types t on t.document_type_id = r.document_type_id where r.numbering_rule_id = numbering_rule_versions.numbering_rule_id) = 'JOURNAL_VOUCHER' then '01900000-0000-7000-8000-000000000102'::uuid end,
                       finance_confirmed_at = case when (select t.code from platform_numbering.numbering_rules r join platform_numbering.document_types t on t.document_type_id = r.document_type_id where r.numbering_rule_id = numbering_rule_versions.numbering_rule_id) = 'JOURNAL_VOUCHER' then now() end
                 where version_number = 1 and status = 'PENDING_CONFIRMATION'
                """);
    }

    @Test
    void numbersFollowTheRuleResetYearlyAndAreSeparatePerLegalEntity() {
        LocalDate day = LocalDate.of(2026, 10, 14);
        // unified new format (ADR-0010 decision 5): {TYPE}-{LE}-{yyyyMM}-{seq:4}, reset monthly; {LE} is the legal entity code
        assertThat(issue(new DocumentTypeCode("QUOTE"), LE1, day)).isEqualTo("QT-FST-202610-0001");
        assertThat(issue(new DocumentTypeCode("QUOTE"), LE1, day)).isEqualTo("QT-FST-202610-0002");
        assertThat(issue(new DocumentTypeCode("QUOTE"), new LegalEntityId(TestIds.uuid(2)), day)).isEqualTo("QT-FIT-202610-0001");
        assertThat(issue(new DocumentTypeCode("QUOTE"), LE1, LocalDate.of(2026, 11, 2))).isEqualTo("QT-FST-202611-0001");
        assertThat(issue(new DocumentTypeCode("JOURNAL_VOUCHER"), LE1, day)).isEqualTo("JV-FST-202610-0001");
    }

    @Test
    void rolledBackNumbersAreReissuedSoThereAreNoGaps() {
        LocalDate day = LocalDate.of(2026, 3, 1);
        String first = issue(new DocumentTypeCode("TIMESHEET"), LE1, day);
        String rolledBack = tx.execute(status -> {
            String n = numbers.next(new DocumentTypeCode("TIMESHEET"), LE1, day).value();
            status.setRollbackOnly();
            return n;
        });
        assertThat(first).isEqualTo("TS-FST-202603-0001");
        assertThat(rolledBack).isEqualTo("TS-FST-202603-0002");
        assertThat(issue(new DocumentTypeCode("TIMESHEET"), LE1, day)).isEqualTo("TS-FST-202603-0002");
    }

    @Test
    void concurrentIssuingNeverDuplicatesOrSkips() throws Exception {
        LocalDate day = LocalDate.of(2026, 5, 5);
        Set<String> issued = ConcurrentHashMap.newKeySet();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        for (int i = 0; i < 20; i++) {
            pool.submit(() -> issued.add(issue(new DocumentTypeCode("TIMESHEET"), LE1, day)));
        }
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        assertThat(issued).hasSize(20).contains("TS-FST-202605-0001", "TS-FST-202605-0020");
    }

    @Test
    void issuingOutsideTheCallersTransactionIsRefused() {
        assertThatThrownBy(() -> numbers.next(new DocumentTypeCode("QUOTE"), LE1, LocalDate.now()))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    private String ruleVersion(String documentType) {
        return "\"" + jdbc.queryForObject("select version from platform_numbering.numbering_rules where document_type_id = (select document_type_id from platform_numbering.document_types where code = ?)", Long.class, documentType) + "\"";
    }

    @Test
    void adminCanRedefineRulesButInvalidPatternsAreRejected() throws Exception {
        var admin = user(TestAccounts.account(9).value().toString()).authorities(() -> Permission.Codes.PLATFORM_NUMBERING_RULE_UPDATE);
        mvc.perform(put("/api/v1/platform/numbering-rules/JOURNAL_VOUCHER").with(csrf()).with(admin).header("If-Match", ruleVersion("JOURNAL_VOUCHER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pattern\":\"JV-{seq:6}\",\"resetPolicy\":\"MONTHLY\",\"startValue\":1,\"effectiveFrom\":\"" + LocalDate.now().plusDays(1) + "\"}"))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(put("/api/v1/platform/numbering-rules/JOURNAL_VOUCHER").with(csrf()).with(admin).header("If-Match", ruleVersion("JOURNAL_VOUCHER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pattern\":\"PZ{LE}-{yyyyMM}-{seq:5}\",\"resetPolicy\":\"MONTHLY\",\"startValue\":500,\"effectiveFrom\":\"" + LocalDate.now().plusDays(1) + "\"}"))
                .andExpect(status().isOk()).andExpect(header().exists("ETag"));
        mvc.perform(post("/api/v1/platform/numbering-rules/JOURNAL_VOUCHER:confirm").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"templateVersion\":2}")
                        .with(user(TestAccounts.account(10).value().toString()).authorities(() -> Permission.Codes.PLATFORM_NUMBERING_RULE_APPROVE)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/platform/numbering-rules/JOURNAL_VOUCHER:confirm-finance").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"templateVersion\":2}")
                        .with(user(TestAccounts.account(11).value().toString()).authorities(() -> Permission.Codes.PLATFORM_NUMBERING_VOUCHER_RULE_APPROVE)))
                .andExpect(status().isOk());
        assertThat(issue(new DocumentTypeCode("JOURNAL_VOUCHER"), LE1, LocalDate.of(2026, 11, 1))).isEqualTo("PZFST-202611-00500");
        mvc.perform(put("/api/v1/platform/numbering-rules/JOURNAL_VOUCHER").with(csrf()).with(user(TestAccounts.account(8).value().toString()).authorities(() -> Permission.Codes.TIMESHEET_SUBMIT))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pattern\":\"X{seq:2}\",\"resetPolicy\":\"NEVER\",\"startValue\":1,\"effectiveFrom\":\"" + LocalDate.now().plusDays(1) + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void openApiSchemaNamesArePascalCaseAndJsonPropertiesAndParametersCamelCaseGlossaryWords() throws Exception {
        String body = mvc.perform(get("/v3/api-docs").with(user(TestAccounts.account(1).value().toString()))).andExpect(status().isOk())
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
                    if ("header".equals(parameter.path("in").asString()) && STANDARD_HEADERS.contains(name)) {
                        continue;
                    }
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
        if (type == com.forcitis.one.platform.approval.api.ProcessKey.class) {
            return com.forcitis.one.platform.approval.api.ProcessKey.of("cost_timesheet_approval");
        }
        if (type == java.util.UUID.class) {
            return java.util.UUID.fromString("0198a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5b");
        }
        if (type == com.forcitis.one.kernel.AccountingPeriod.class) {
            return new com.forcitis.one.kernel.AccountingPeriod(2026, 10);
        }
        if (type == com.forcitis.one.organization.workforce.api.LegalEntityCode.class) {
            return new com.forcitis.one.organization.workforce.api.LegalEntityCode("FST");
        }
        if (type == java.time.Year.class) {
            return java.time.Year.of(2026);
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
        if (type == java.util.UUID.class) {
            return java.util.UUID.fromString("00000000-0000-7000-8000-000000000001");
        }
        if (type == com.forcitis.one.kernel.AccountingPeriod.class) {
            return com.forcitis.one.kernel.AccountingPeriod.of(2026, 10);
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
        if (type.equals(com.forcitis.one.kernel.DocumentTypeCode.class)) {
            return new com.forcitis.one.kernel.DocumentTypeCode("TIMESHEET");
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

    @Test
    void everyTableAndColumnIsDocumentedAndTheDictionaryIsGenerated() throws Exception {
        var rows = jdbc.queryForList("""
                select c.table_schema, c.table_name, c.column_name, c.data_type, c.is_nullable,
                       col_description(format('%I.%I', c.table_schema, c.table_name)::regclass, c.ordinal_position) as column_comment,
                       obj_description(format('%I.%I', c.table_schema, c.table_name)::regclass, 'pg_class') as table_comment
                  from information_schema.columns c
                 where c.table_schema in ('platform_numbering', 'platform_scheduling')
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

    private String issue(DocumentTypeCode type, LegalEntityId legalEntity, LocalDate day) {
        return tx.execute(status -> numbers.next(type, legalEntity, day)).value();
    }

    private Integer count(String schema) {
        return jdbc.queryForObject("select count(*) from information_schema.tables where table_schema = ? and table_name like 'act_%'",
                Integer.class, schema);
    }
}
