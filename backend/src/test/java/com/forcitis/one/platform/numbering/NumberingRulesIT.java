package com.forcitis.one.platform.numbering;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.fixture.doubles.TestAccounts;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.forcitis.one.TestIds;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.identity.api.Permission;
import com.forcitis.one.platform.identity.api.RoleDirectory;
import com.forcitis.one.platform.integration.api.WecomGateway;
import com.forcitis.one.platform.numbering.api.DocumentNumbers;
import com.forcitis.one.kernel.DocumentTypeCode;
import com.forcitis.one.platform.integration.api.ExternalSource;
import com.forcitis.one.platform.integration.api.MigrationBatchId;
import com.forcitis.one.platform.numbering.api.LegacyNumberEntry;
import com.forcitis.one.kernel.DocumentId;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.platform.numbering.api.FindDocumentByLegacyNumber;
import com.forcitis.one.platform.numbering.api.FindLegacyNumbersOfDocument;
import com.forcitis.one.platform.numbering.api.RegisterLegacyNumbers;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {"flowable.async-executor-activate=false", "flowable.database-schema-update=true",
        "spring.datasource.hikari.connection-timeout=2000"})
@AutoConfigureMockMvc
class NumberingRulesIT {
    @MockitoBean
    OrgDirectory orgDirectory;

    @MockitoBean
    RoleDirectory roleDirectory;

    @MockitoBean
    WecomGateway wecomGateway;

    @MockitoBean
    AuditLog auditLog;

    @MockitoBean
    com.forcitis.one.organization.workforce.api.LegalEntityDirectory legalEntityDirectory;

    static final TestDatabase postgres = TestPostgres.database(NumberingRulesIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    static final String RULES = "/api/v1/platform/numbering-rules";
    static final String LEGACY = "/api/v1/platform/legacy-numbers";
    static final MigrationBatchId BATCH = new MigrationBatchId(TestIds.uuid(9001));
    static final LegalEntityId LE1 = new LegalEntityId(TestIds.uuid(1));
    static final RequestPostProcessor READER = user(TestAccounts.account(7).value().toString()).authorities(() -> Permission.Codes.PLATFORM_NUMBERING_RULE_READ);
    static final RequestPostProcessor LEGACY_READER = user(TestAccounts.account(6).value().toString()).authorities(() -> Permission.Codes.PLATFORM_NUMBERING_LEGACY_NUMBER_READ);
    static final RequestPostProcessor UPDATER = user(TestAccounts.account(9).value().toString()).authorities(() -> Permission.Codes.PLATFORM_NUMBERING_RULE_UPDATE);

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    DocumentNumbers numbers;

    @Autowired
    com.forcitis.one.platform.numbering.api.GlobalDocumentNumbers globalNumbers;

    @Autowired
    RegisterLegacyNumbers register;

    @Autowired
    FindDocumentByLegacyNumber findDocument;

    @Autowired
    FindLegacyNumbersOfDocument findLegacyNumbers;

    @Autowired
    TransactionTemplate tx;

    @BeforeEach
    void legalEntitiesExist() {
        NumberingFixtures.restoreSeededDefaults(jdbc);
        org.mockito.Mockito.when(legalEntityDirectory.lockActiveCode(org.mockito.ArgumentMatchers.any())).thenReturn(java.util.Optional.empty());
        org.mockito.Mockito.when(legalEntityDirectory.lockActiveCode(new LegalEntityId(TestIds.uuid(1))))
                .thenReturn(java.util.Optional.of(new com.forcitis.one.organization.workforce.api.LegalEntityCode("FST")));
        org.mockito.Mockito.when(legalEntityDirectory.lockActiveCode(new LegalEntityId(TestIds.uuid(2))))
                .thenReturn(java.util.Optional.of(new com.forcitis.one.organization.workforce.api.LegalEntityCode("FIT")));
    }

    private void inForce(String... types) {
        for (String type : types) {
            NumberingFixtures.confirmSeeded(jdbc, type);
        }
    }

    private String issue(DocumentTypeCode type, LegalEntityId legalEntity, LocalDate day) {
        return tx.execute(status -> numbers.next(type, legalEntity, day).value());
    }

    @Test
    @DisplayName("C1a-16 the rule list shows every rule (the five seed rules); the quote starts on the unified default template, pending confirmation")
    void ruleListShowsTheDefaultQuoteTemplateAsPending() throws Exception {
        mvc.perform(get(RULES).with(READER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(6))
                .andExpect(jsonPath("$.items[*].documentType").value(org.hamcrest.Matchers.containsInAnyOrder(
                        "BONUS_INPUT", "EMPLOYEE", "JOURNAL_VOUCHER", "QUOTE", "RECRUITMENT_REQUISITION", "TIMESHEET")))
                .andExpect(jsonPath("$.items[?(@.documentType == 'RECRUITMENT_REQUISITION')].confirmationStatus").value("PENDING_CONFIRMATION"))
                .andExpect(jsonPath("$.items[?(@.documentType == 'QUOTE')].pattern").value("{TYPE}-{LE}-{yyyyMM}-{seq:4}"))
                .andExpect(jsonPath("$.items[?(@.documentType == 'QUOTE')].resetPolicy").value("MONTHLY"))
                .andExpect(jsonPath("$.items[?(@.documentType == 'QUOTE')].documentTypeCode").value("QT"))
                .andExpect(jsonPath("$.items[?(@.documentType == 'QUOTE')].confirmationStatus").value("PENDING_CONFIRMATION"))
                .andExpect(jsonPath("$.items[?(@.documentType == 'QUOTE')].capacity").value(9999));
    }

    @Test
    void oneRuleCanBeReadByDocumentType() throws Exception {
        mvc.perform(get(RULES + "/QUOTE").with(READER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentType").value("QUOTE"))
                .andExpect(jsonPath("$.startValue").value(1));
        mvc.perform(get(RULES + "/NO_SUCH_TYPE").with(READER)).andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("reading rules needs numbering-rule:read; the update permission alone does not read")
    void readingNeedsTheReadPermission() throws Exception {
        for (String path : new String[] {RULES, RULES + "/QUOTE"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).with(UPDATER)).andExpect(status().isForbidden());
            mvc.perform(get(path).with(user(TestAccounts.account(8).value().toString()).authorities(() -> Permission.Codes.TIMESHEET_SUBMIT))).andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("the quote's first number follows the unified format with the registered code QT")
    void quoteNumbersCarryTheRegisteredTypeCode() {
        inForce("QUOTE");
        LocalDate day = LocalDate.of(2026, 10, 14);

        assertThat(issue(new DocumentTypeCode("QUOTE"), LE1, day)).isEqualTo("QT-FST-202610-0001");
        assertThat(issue(new DocumentTypeCode("QUOTE"), LE1, day)).isEqualTo("QT-FST-202610-0002");
        assertThat(issue(new DocumentTypeCode("QUOTE"), new LegalEntityId(TestIds.uuid(2)), day)).isEqualTo("QT-FIT-202610-0001");
    }

    @Test
    @DisplayName("an unknown legal entity cannot take a number, whatever the template")
    void unknownLegalEntityCannotIssue() {
        inForce("QUOTE", "TIMESHEET");
        LocalDate day = LocalDate.of(2027, 3, 3);

        assertThatThrownBy(() -> issue(new DocumentTypeCode("QUOTE"), new LegalEntityId(TestIds.uuid(99)), day))
                .isInstanceOfSatisfying(com.forcitis.one.kernel.DomainException.class, e -> assertThat(e.errorCode())
                        .isEqualTo(com.forcitis.one.platform.numbering.api.NumberingError.PLATFORM_NUMBERING_LEGAL_ENTITY_UNAVAILABLE));
        assertThatThrownBy(() -> issue(new DocumentTypeCode("TIMESHEET"), new LegalEntityId(TestIds.uuid(99)), day))
                .isInstanceOf(com.forcitis.one.kernel.DomainException.class);
    }

    @Test
    @DisplayName("a deactivated legal entity cannot take a new number")
    void deactivatedLegalEntityCannotIssue() {
        inForce("QUOTE", "TIMESHEET");
        LocalDate day = LocalDate.of(2027, 4, 4);
        assertThatThrownBy(() -> issue(new DocumentTypeCode("QUOTE"), new LegalEntityId(TestIds.uuid(3)), day)).isInstanceOf(com.forcitis.one.kernel.DomainException.class);
        assertThatThrownBy(() -> issue(new DocumentTypeCode("TIMESHEET"), new LegalEntityId(TestIds.uuid(3)), day)).isInstanceOf(com.forcitis.one.kernel.DomainException.class);
    }

    @Test
    @DisplayName("a template without {LE} (configured later by an administrator) shares one counter across legal entities, so numbers never repeat")
    void templateWithoutLegalEntityTokenSharesOneCounter() {
        LocalDate day = LocalDate.of(2027, 5, 6);
        inForce("TIMESHEET");
        NumberingFixtures.useTemplate(jdbc, "TIMESHEET", "{TYPE}-{yyyyMMdd}-{seq:4}", "DAILY");

        String first = issue(new DocumentTypeCode("TIMESHEET"), LE1, day);
        String second = issue(new DocumentTypeCode("TIMESHEET"), new LegalEntityId(TestIds.uuid(2)), day);

        assertThat(first).isEqualTo("TS-20270506-0001");
        assertThat(second).isEqualTo("TS-20270506-0002");
        assertThat(jdbc.sql("select count(*) from platform_numbering.numbering_counters where document_type_id = (select document_type_id from platform_numbering.document_types where code = 'TIMESHEET') and legal_entity_id is null")
                .query(Long.class).single()).as("the shared counter is the one row without a legal entity, not a made-up id").isEqualTo(1L);
        assertThatThrownBy(() -> jdbc.sql("insert into platform_numbering.numbering_counters (numbering_counter_id, document_type_id, counter_scope_kind, legal_entity_id, period_key, last_value)"
                + " values (uuidv7(), (select document_type_id from platform_numbering.document_types where code = 'TIMESHEET'), 'GLOBAL', null, '20270506', 1)").update())
                .as("a second row for the shared counter is refused although its legal entity is null").hasMessageContaining("uk_numbering_counters_document_type_legal_entity_period");
    }

    private String issueGlobal(DocumentTypeCode type, LocalDate day) {
        return tx.execute(status -> globalNumbers.nextGlobal(type, day).value());
    }

    @Test
    @DisplayName("#963 the employee code type starts on {TYPE}{seq:6}, pending confirmation: nextGlobal issues nothing until it is confirmed")
    void employeeCodeIsNotIssuedBeforeTheTemplateIsConfirmed() {
        assertThat(jdbc.sql("select template_text || '/' || reset_policy || '/' || status from platform_numbering.numbering_rule_versions v join platform_numbering.numbering_rules r using (numbering_rule_id)"
                + " join platform_numbering.document_types t using (document_type_id) where t.code = 'EMPLOYEE'").query(String.class).single()).isEqualTo("{TYPE}{seq:6}/NEVER/PENDING_CONFIRMATION");
        assertThatThrownBy(() -> issueGlobal(new DocumentTypeCode("EMPLOYEE"), LocalDate.of(2026, 10, 30)))
                .isInstanceOfSatisfying(com.forcitis.one.kernel.DomainException.class, e -> assertThat(e.errorCode())
                        .isEqualTo(com.forcitis.one.platform.numbering.api.NumberingError.PLATFORM_NUMBERING_RULE_NOT_CONFIRMED));
        assertThat(jdbc.sql("select count(*) from platform_numbering.numbering_counters").query(Long.class).single()).as("a refused issue leaves no counter").isZero();
    }

    @Test
    @DisplayName("#963 once confirmed, nextGlobal issues EMP000001, EMP000002 ... from one counter that no date or legal entity splits, and a rollback gives the number back")
    void confirmedEmployeeCodesAreUniqueAndGapless() {
        inForce("EMPLOYEE");
        DocumentTypeCode employee = new DocumentTypeCode("EMPLOYEE");

        assertThat(issueGlobal(employee, LocalDate.of(2026, 10, 30))).isEqualTo("EMP000001");
        assertThat(issueGlobal(employee, LocalDate.of(2027, 1, 2))).as("a later year does not restart it").isEqualTo("EMP000002");
        assertThatThrownBy(() -> tx.execute(status -> {
            globalNumbers.nextGlobal(employee, LocalDate.of(2026, 10, 30));
            throw new IllegalStateException("the hire is refused after the number was taken");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(issueGlobal(employee, LocalDate.of(2026, 10, 30))).as("the rolled-back number is issued again, no gap").isEqualTo("EMP000003");
        assertThat(jdbc.sql("select count(*) from platform_numbering.numbering_counters where document_type_id = (select document_type_id from platform_numbering.document_types where code = 'EMPLOYEE')")
                .query(Long.class).single()).isEqualTo(1L);
    }

    @Test
    @DisplayName("#963 nextGlobal refuses a template with {LE} (PLATFORM_NUMBERING_RULE_INVALID) and issues no number; next still needs a legal entity")
    void nextGlobalRefusesATemplateWithTheLegalEntityToken() {
        inForce("EMPLOYEE", "QUOTE");
        NumberingFixtures.useTemplate(jdbc, "EMPLOYEE", "{TYPE}-{LE}-{seq:6}", "NEVER");

        assertThatThrownBy(() -> issueGlobal(new DocumentTypeCode("EMPLOYEE"), LocalDate.of(2026, 10, 30)))
                .isInstanceOfSatisfying(com.forcitis.one.kernel.DomainException.class, e -> assertThat(e.errorCode())
                        .isEqualTo(com.forcitis.one.platform.numbering.api.NumberingError.PLATFORM_NUMBERING_RULE_INVALID));
        assertThat(jdbc.sql("select count(*) from platform_numbering.numbering_counters").query(Long.class).single()).isZero();
        assertThat(issue(new DocumentTypeCode("QUOTE"), LE1, LocalDate.of(2026, 10, 14))).as("the legal-entity entry is unchanged").isEqualTo("QT-FST-202610-0001");
    }

    @Test
    @DisplayName("#963 nextGlobal outside a transaction is refused, as next is: the counter must roll back with the caller's write")
    void nextGlobalNeedsTheCallersTransaction() {
        inForce("EMPLOYEE");
        assertThatThrownBy(() -> globalNumbers.nextGlobal(new DocumentTypeCode("EMPLOYEE"), LocalDate.of(2026, 10, 30)))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        assertThat(jdbc.sql("select count(*) from platform_numbering.numbering_counters").query(Long.class).single()).as("nothing was counted").isZero();
    }

    @Test
    @DisplayName("#963 two employee codes taken at once are different")
    void concurrentEmployeeCodesAreDifferent() throws Exception {
        inForce("EMPLOYEE");
        DocumentTypeCode employee = new DocumentTypeCode("EMPLOYEE");
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(8);
        try {
            java.util.List<java.util.concurrent.Future<String>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < 24; i++) {
                futures.add(pool.submit(() -> issueGlobal(employee, LocalDate.of(2026, 10, 30))));
            }
            java.util.Set<String> seen = new java.util.TreeSet<>();
            for (java.util.concurrent.Future<String> f : futures) {
                seen.add(f.get());
            }
            assertThat(seen).hasSize(24).first().isEqualTo("EMP000001");
            assertThat(seen).last().isEqualTo("EMP000024");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("the counter table refuses a counter scope kind that does not match its legal entity, and an unknown kind")
    void counterTableRefusesMismatchedScopeAndUnknownKind() {
        String insert = "insert into platform_numbering.numbering_counters (numbering_counter_id, document_type_id, counter_scope_kind, legal_entity_id, period_key, last_value)"
                + " values (uuidv7(), (select document_type_id from platform_numbering.document_types where code = 'QUOTE'), '%s', %s, '202701', 1)";

        assertThatThrownBy(() -> jdbc.sql(insert.formatted("GLOBAL", "'" + LE1.value() + "'")).update())
                .as("a global scope must not name a legal entity")
                .hasMessageContaining("ck_numbering_counters_scope_kind_matches_legal_entity");
        assertThatThrownBy(() -> jdbc.sql(insert.formatted("LEGAL_ENTITY", "null")).update())
                .as("a per-legal-entity scope must name a legal entity")
                .hasMessageContaining("ck_numbering_counters_scope_kind_matches_legal_entity");
        assertThatThrownBy(() -> jdbc.sql(insert.formatted("REGIONAL", "null")).update())
                .as("only the two known kinds are stored")
                .hasMessageContaining("ck_numbering_counters_counter_scope_kind");
    }

    @Test
    @DisplayName("the timesheet and the journal voucher start on the unified template with their registered codes, pending confirmation")
    void timesheetAndVoucherUseTheUnifiedTemplate() throws Exception {
        for (String type : new String[] {"TIMESHEET", "JOURNAL_VOUCHER"}) {
            mvc.perform(get(RULES + "/" + type).with(READER))
                    .andExpect(jsonPath("$.pattern").value("{TYPE}-{LE}-{yyyyMM}-{seq:4}"))
                    .andExpect(jsonPath("$.resetPolicy").value("MONTHLY"))
                    .andExpect(jsonPath("$.confirmationStatus").value("PENDING_CONFIRMATION"));
        }
        inForce("TIMESHEET", "JOURNAL_VOUCHER");
        LocalDate day = LocalDate.of(2027, 6, 7);
        assertThat(issue(new DocumentTypeCode("TIMESHEET"), LE1, day)).isEqualTo("TS-FST-202706-0001");
        assertThat(issue(new DocumentTypeCode("TIMESHEET"), new LegalEntityId(TestIds.uuid(2)), day)).isEqualTo("TS-FIT-202706-0001");
        assertThat(issue(new DocumentTypeCode("JOURNAL_VOUCHER"), LE1, day)).isEqualTo("JV-FST-202706-0001");
    }

    @Test
    @DisplayName("ADR-0027 2.4 saving a rule only saves a new version that waits for someone else's confirmation; it confirms nothing")
    void savingDoesNotConfirm() throws Exception {
        String body = "{\"pattern\":\"{TYPE}-{LE}-{yyyyMM}-{seq:5}\",\"resetPolicy\":\"MONTHLY\",\"startValue\":1,\"effectiveFrom\":\""
                + java.time.LocalDate.now().plusDays(1) + "\"}";
        mvc.perform(put(RULES + "/QUOTE").with(csrf()).with(UPDATER).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPreconditionRequired()).andExpect(jsonPath("$.errorCode").value("PLATFORM_WEB_VERSION_REQUIRED"));
        mvc.perform(put(RULES + "/QUOTE").header("If-Match", "\"999\"").with(csrf()).with(UPDATER).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPreconditionFailed());
        String etag = mvc.perform(get(RULES + "/QUOTE").with(READER)).andReturn().getResponse().getHeader("ETag");
        mvc.perform(put(RULES + "/QUOTE").header("If-Match", etag).with(csrf()).with(UPDATER).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(header().exists("ETag"));

        mvc.perform(get(RULES + "/QUOTE").with(READER))
                .andExpect(jsonPath("$.confirmationStatus").value("PENDING_CONFIRMATION"))
                .andExpect(jsonPath("$.pending.templateVersion").value(2))
                .andExpect(jsonPath("$.pending.pattern").value("{TYPE}-{LE}-{yyyyMM}-{seq:5}"))
                .andExpect(jsonPath("$.pending.authorId").value(TestAccounts.account(9).value().toString()));
        assertThatThrownBy(() -> issue(new DocumentTypeCode("QUOTE"), LE1, LocalDate.of(2027, 7, 1)))
                .isInstanceOf(com.forcitis.one.kernel.DomainException.class).hasMessageContaining("no confirmed template");
    }

    @Test
    @DisplayName("D-1a-160 an original number is found by its beginning, a page at a time; a prefix shorter than 2 characters is refused")
    void legacyNumbersAreSearchableByPrefix() throws Exception {
        String[] numbers = {"SYN-PX-0001", "SYN-PX-0002", "SYN-PX-0003", "OTHER-0001"};
        for (int i = 0; i < numbers.length; i++) {
            register.register(new LegacyNumberEntry(new DocumentReference(new DocumentTypeCode("QUOTE"), doc(7000 + i)), ExternalSource.LEGACY_SYSTEM, numbers[i], Optional.of(LE1), BATCH));
        }

        String first = mvc.perform(get(LEGACY).param("sourceSystem", "LEGACY_SYSTEM").param("documentType", "QUOTE").param("legacyNumberPrefix", "SYN-PX").param("limit", "2").with(LEGACY_READER))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].legacyNumber").value("SYN-PX-0001")).andExpect(jsonPath("$.nextCursor").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String cursor = new tools.jackson.databind.json.JsonMapper().readTree(first).path("nextCursor").asString();
        mvc.perform(get(LEGACY).param("sourceSystem", "LEGACY_SYSTEM").param("documentType", "QUOTE").param("legacyNumberPrefix", "SYN-PX").param("limit", "2").param("cursor", cursor).with(LEGACY_READER))
                .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].legacyNumber").value("SYN-PX-0003"))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
        mvc.perform(get(LEGACY).param("sourceSystem", "LEGACY_SYSTEM").param("documentType", "QUOTE").param("legacyNumberPrefix", "S").with(LEGACY_READER))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.errorCode").value("PLATFORM_NUMBERING_LEGACY_PREFIX_INVALID"));
    }

    @Test
    @DisplayName("C1a-16 an original number finds the new document, and the new document finds its original numbers")
    void legacyNumbersAreSearchableBothWays() throws Exception {
        register.register(new LegacyNumberEntry(new DocumentReference(new DocumentTypeCode("QUOTE"), doc(501)), ExternalSource.LEGACY_SYSTEM, "SYN-QU-0001", Optional.of(LE1), BATCH));
        register.register(new LegacyNumberEntry(new DocumentReference(new DocumentTypeCode("QUOTE"), doc(501)), ExternalSource.LEGACY_SYSTEM, "SYN-QU-0001-R2", Optional.of(LE1), BATCH));
        register.register(new LegacyNumberEntry(new DocumentReference(new DocumentTypeCode("QUOTE"), doc(502)), ExternalSource.LEGACY_SYSTEM, "SYN-QU-0002", Optional.of(new LegalEntityId(TestIds.uuid(2))), BATCH));

        mvc.perform(get(LEGACY + ":resolve").param("sourceSystem", "LEGACY_SYSTEM").param("documentType", "QUOTE").param("legacyNumber", "SYN-QU-0001").with(LEGACY_READER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value(TestIds.uuid(501).toString()))
                .andExpect(jsonPath("$.legalEntityId").value(LE1.value().toString()))
                .andExpect(jsonPath("$.migrationBatchId").value(BATCH.toString()));
        mvc.perform(get(LEGACY).param("documentType", "QUOTE").param("documentId", TestIds.uuid(501).toString()).with(LEGACY_READER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].legacyNumber").value("SYN-QU-0001"))
                .andExpect(jsonPath("$.items[1].legacyNumber").value("SYN-QU-0001-R2"));
        mvc.perform(get(LEGACY + ":resolve").param("sourceSystem", "LEGACY_SYSTEM").param("documentType", "QUOTE").param("legacyNumber", "SYN-QU-9999").with(LEGACY_READER))
                .andExpect(status().isNotFound());
        mvc.perform(get(LEGACY).param("documentType", "QUOTE").param("documentId", TestIds.uuid(999).toString()).with(LEGACY_READER))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
        assertThat(findDocument.find(ExternalSource.LEGACY_SYSTEM, new DocumentTypeCode("TIMESHEET"), "SYN-QU-0001")).isEmpty();
    }

    @Test
    void legacyNumberSearchNeedsThePermission() throws Exception {
        mvc.perform(get(LEGACY + ":resolve").param("sourceSystem", "LEGACY_SYSTEM").param("documentType", "QUOTE").param("legacyNumber", "X")).andExpect(status().isUnauthorized());
        mvc.perform(get(LEGACY + ":resolve").param("sourceSystem", "LEGACY_SYSTEM").param("documentType", "QUOTE").param("legacyNumber", "X").with(UPDATER)).andExpect(status().isForbidden());
        mvc.perform(get(LEGACY).param("documentType", "QUOTE").param("documentId", TestIds.uuid(1).toString()).with(UPDATER)).andExpect(status().isForbidden());
        mvc.perform(get(LEGACY + ":resolve").param("sourceSystem", "LEGACY_SYSTEM").param("documentType", "QUOTE").param("legacyNumber", "X").with(READER)).andExpect(status().isForbidden());
        mvc.perform(get(LEGACY).param("documentType", "QUOTE").param("documentId", TestIds.uuid(1).toString()).with(READER)).andExpect(status().isForbidden());
        mvc.perform(get(RULES).with(LEGACY_READER)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("D-1a-152 an original number whose legal entity could not be worked out is stored without one and stays searchable")
    void legacyNumberWithoutLegalEntityIsStoredAndFound() throws Exception {
        register.register(new LegacyNumberEntry(new DocumentReference(new DocumentTypeCode("QUOTE"), doc(601)), ExternalSource.LEGACY_SYSTEM, "SYN-QU-NOLE", Optional.empty(), BATCH));
        register.register(new LegacyNumberEntry(new DocumentReference(new DocumentTypeCode("QUOTE"), doc(601)), ExternalSource.LEGACY_SYSTEM, "SYN-QU-NOLE", Optional.empty(), BATCH));

        assertThat(jdbc.sql("select count(*) from platform_numbering.legacy_numbers where legacy_number = 'SYN-QU-NOLE' and legal_entity_id is null")
                .query(Integer.class).single()).isEqualTo(1);
        assertThat(findDocument.find(ExternalSource.LEGACY_SYSTEM, new DocumentTypeCode("QUOTE"), "SYN-QU-NOLE")).hasValueSatisfying(e -> assertThat(e.legalEntity()).isEmpty());
        mvc.perform(get(LEGACY + ":resolve").param("sourceSystem", "LEGACY_SYSTEM").param("documentType", "QUOTE").param("legacyNumber", "SYN-QU-NOLE").with(LEGACY_READER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value(TestIds.uuid(601).toString()))
                .andExpect(jsonPath("$.legalEntityId").doesNotExist());
        mvc.perform(get(LEGACY).param("documentType", "QUOTE").param("documentId", TestIds.uuid(601).toString()).with(LEGACY_READER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].legacyNumber").value("SYN-QU-NOLE"));
    }

    @Test
    @DisplayName("(source system, document type, original number) is unique: re-running an import is a no-op, a second owner is refused")
    void legacyNumberIsUniquePerDocumentType() {
        LegacyNumberEntry first = new LegacyNumberEntry(new DocumentReference(new DocumentTypeCode("TIMESHEET"), doc(11)), ExternalSource.LEGACY_SYSTEM, "SYN-TS-1", Optional.of(LE1), BATCH);
        register.register(first);
        register.register(first);

        assertThat(jdbc.sql("select count(*) from platform_numbering.legacy_numbers where legacy_number = 'SYN-TS-1'")
                .query(Integer.class).single()).isEqualTo(1);
        assertThatThrownBy(() -> register.register(new LegacyNumberEntry(new DocumentReference(new DocumentTypeCode("TIMESHEET"), doc(12)), ExternalSource.LEGACY_SYSTEM, "SYN-TS-1", Optional.of(LE1), BATCH)))
                .isInstanceOfSatisfying(com.forcitis.one.kernel.DomainException.class, e -> assertThat(e.errorCode())
                        .isEqualTo(com.forcitis.one.platform.numbering.api.NumberingError.PLATFORM_NUMBERING_LEGACY_NUMBER_TAKEN));
        register.register(new LegacyNumberEntry(new DocumentReference(new DocumentTypeCode("JOURNAL_VOUCHER"), doc(12)), ExternalSource.LEGACY_SYSTEM, "SYN-TS-1", Optional.of(LE1), BATCH));
        assertThat(findDocument.find(ExternalSource.LEGACY_SYSTEM, new DocumentTypeCode("JOURNAL_VOUCHER"), "SYN-TS-1")).isPresent();
    }

    @Test
    void theDatabaseItselfEnforcesTheUniqueConstraint() {
        String insert = "insert into platform_numbering.legacy_numbers (id, source_system, document_type_id, legacy_number, document_id, legal_entity_id, migration_batch_id)"
                + " values (uuidv7(), 'LEGACY_SYSTEM', (select document_type_id from platform_numbering.document_types where code = 'QUOTE'), 'SYN-DB-1', '" + TestIds.uuid(1) + "', '" + TestIds.uuid(1) + "', '" + BATCH + "')";
        jdbc.sql(insert).update();

        assertThatThrownBy(() -> jdbc.sql(insert).update()).isInstanceOf(DuplicateKeyException.class);
    }

    private static DocumentId doc(long number) {
        return new DocumentId(TestIds.uuid(number));
    }

    @Test
    void entriesValidateThemselves() {
        assertThatThrownBy(() -> new LegacyNumberEntry(new DocumentReference(new DocumentTypeCode("QUOTE"), doc(1)), ExternalSource.LEGACY_SYSTEM, " ", Optional.of(LE1), BATCH)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LegacyNumberEntry(new DocumentReference(new DocumentTypeCode("QUOTE"), doc(1)), ExternalSource.LEGACY_SYSTEM, "X", Optional.of(LE1), new MigrationBatchId(new java.util.UUID(0, 0)))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LegacyNumberEntry(new DocumentReference(new DocumentTypeCode("QUOTE"), doc(1)), ExternalSource.LEGACY_SYSTEM, "x".repeat(129), Optional.of(LE1), BATCH)).isInstanceOf(IllegalArgumentException.class);
    }
}
