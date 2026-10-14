package com.forcitis.one.platform.numbering;

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
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.identity.api.Permission;
import com.forcitis.one.platform.identity.api.RoleDirectory;
import com.forcitis.one.platform.integration.api.WecomGateway;
import com.forcitis.one.platform.numbering.api.DocumentNumbers;
import com.forcitis.one.kernel.DocumentType;
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
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {"flowable.async-executor-activate=false", "flowable.database-schema-update=true",
        "spring.datasource.hikari.connection-timeout=2000"})
@AutoConfigureMockMvc
@Testcontainers
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

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18")
            .withInitScript("init-schemas.sql")
            .withUrlParam("currentSchema", "platform_approval");

    static final String RULES = "/api/v1/platform/numbering-rules";
    static final String LEGACY = "/api/v1/platform/legacy-numbers";
    static final LegalEntityId LE1 = new LegalEntityId(TestIds.uuid(1));
    static final RequestPostProcessor READER = user("7").authorities(() -> Permission.Codes.PLATFORM_NUMBERING_RULE_READ);
    static final RequestPostProcessor LEGACY_READER = user("6").authorities(() -> Permission.Codes.PLATFORM_NUMBERING_LEGACY_NUMBER_READ);
    static final RequestPostProcessor UPDATER = user("9").authorities(() -> Permission.Codes.PLATFORM_NUMBERING_RULE_UPDATE);

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    DocumentNumbers numbers;

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

    private String issue(DocumentType type, LegalEntityId legalEntity, LocalDate day) {
        return tx.execute(status -> numbers.next(type, legalEntity, day).value());
    }

    @Test
    @DisplayName("C1a-16 the rule list shows every rule; the quote starts on the unified default template, pending confirmation")
    void ruleListShowsTheDefaultQuoteTemplateAsPending() throws Exception {
        mvc.perform(get(RULES).with(READER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
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
            mvc.perform(get(path).with(user("8").authorities(() -> Permission.Codes.TIMESHEET_SUBMIT))).andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("the quote's first number follows the unified format with the registered code QT")
    void quoteNumbersCarryTheRegisteredTypeCode() {
        inForce("QUOTE");
        LocalDate day = LocalDate.of(2026, 10, 14);

        assertThat(issue(DocumentType.QUOTE, LE1, day)).isEqualTo("QT-FST-202610-0001");
        assertThat(issue(DocumentType.QUOTE, LE1, day)).isEqualTo("QT-FST-202610-0002");
        assertThat(issue(DocumentType.QUOTE, new LegalEntityId(TestIds.uuid(2)), day)).isEqualTo("QT-FIT-202610-0001");
    }

    @Test
    @DisplayName("an unknown legal entity cannot take a number, whatever the template")
    void unknownLegalEntityCannotIssue() {
        inForce("QUOTE", "TIMESHEET");
        LocalDate day = LocalDate.of(2027, 3, 3);

        assertThatThrownBy(() -> issue(DocumentType.QUOTE, new LegalEntityId(TestIds.uuid(99)), day))
                .isInstanceOfSatisfying(com.forcitis.one.kernel.DomainException.class, e -> assertThat(e.errorCode())
                        .isEqualTo(com.forcitis.one.platform.numbering.api.NumberingError.PLATFORM_NUMBERING_LEGAL_ENTITY_UNAVAILABLE));
        assertThatThrownBy(() -> issue(DocumentType.TIMESHEET, new LegalEntityId(TestIds.uuid(99)), day))
                .isInstanceOf(com.forcitis.one.kernel.DomainException.class);
    }

    @Test
    @DisplayName("a deactivated legal entity cannot take a new number")
    void deactivatedLegalEntityCannotIssue() {
        inForce("QUOTE", "TIMESHEET");
        LocalDate day = LocalDate.of(2027, 4, 4);
        assertThatThrownBy(() -> issue(DocumentType.QUOTE, new LegalEntityId(TestIds.uuid(3)), day)).isInstanceOf(com.forcitis.one.kernel.DomainException.class);
        assertThatThrownBy(() -> issue(DocumentType.TIMESHEET, new LegalEntityId(TestIds.uuid(3)), day)).isInstanceOf(com.forcitis.one.kernel.DomainException.class);
    }

    @Test
    @DisplayName("a template without {LE} (configured later by an administrator) shares one counter across legal entities, so numbers never repeat")
    void templateWithoutLegalEntityTokenSharesOneCounter() {
        LocalDate day = LocalDate.of(2027, 5, 6);
        inForce("TIMESHEET");
        NumberingFixtures.useTemplate(jdbc, "TIMESHEET", "{TYPE}-{yyyyMMdd}-{seq:4}", "DAILY");

        String first = issue(DocumentType.TIMESHEET, LE1, day);
        String second = issue(DocumentType.TIMESHEET, new LegalEntityId(TestIds.uuid(2)), day);

        assertThat(first).isEqualTo("TS-20270506-0001");
        assertThat(second).isEqualTo("TS-20270506-0002");
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
        assertThat(issue(DocumentType.TIMESHEET, LE1, day)).isEqualTo("TS-FST-202706-0001");
        assertThat(issue(DocumentType.TIMESHEET, new LegalEntityId(TestIds.uuid(2)), day)).isEqualTo("TS-FIT-202706-0001");
        assertThat(issue(DocumentType.JOURNAL_VOUCHER, LE1, day)).isEqualTo("JV-FST-202706-0001");
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
                .andExpect(jsonPath("$.pending.authorId").value("9"));
        assertThatThrownBy(() -> issue(DocumentType.QUOTE, LE1, LocalDate.of(2027, 7, 1)))
                .isInstanceOf(com.forcitis.one.kernel.DomainException.class).hasMessageContaining("no confirmed template");
    }

    @Test
    @DisplayName("D-1a-160 an original number is found by its beginning, a page at a time; a prefix shorter than 2 characters is refused")
    void legacyNumbersAreSearchableByPrefix() throws Exception {
        String[] numbers = {"SYN-PX-0001", "SYN-PX-0002", "SYN-PX-0003", "OTHER-0001"};
        for (int i = 0; i < numbers.length; i++) {
            register.register(new LegacyNumberEntry(new DocumentReference(DocumentType.QUOTE, doc(7000 + i)), numbers[i], Optional.of(LE1), "synthetic-prefix"));
        }

        String first = mvc.perform(get(LEGACY).param("documentType", "QUOTE").param("legacyNumberPrefix", "SYN-PX").param("limit", "2").with(LEGACY_READER))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].legacyNumber").value("SYN-PX-0001")).andExpect(jsonPath("$.nextCursor").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String cursor = new tools.jackson.databind.json.JsonMapper().readTree(first).path("nextCursor").asString();
        mvc.perform(get(LEGACY).param("documentType", "QUOTE").param("legacyNumberPrefix", "SYN-PX").param("limit", "2").param("cursor", cursor).with(LEGACY_READER))
                .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].legacyNumber").value("SYN-PX-0003"))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
        mvc.perform(get(LEGACY).param("documentType", "QUOTE").param("legacyNumberPrefix", "S").with(LEGACY_READER))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.errorCode").value("PLATFORM_NUMBERING_LEGACY_PREFIX_INVALID"));
    }

    @Test
    @DisplayName("C1a-16 an original number finds the new document, and the new document finds its original numbers")
    void legacyNumbersAreSearchableBothWays() throws Exception {
        register.register(new LegacyNumberEntry(new DocumentReference(DocumentType.QUOTE, doc(501)), "SYN-QU-0001", Optional.of(LE1), "synthetic-batch-1"));
        register.register(new LegacyNumberEntry(new DocumentReference(DocumentType.QUOTE, doc(501)), "SYN-QU-0001-R2", Optional.of(LE1), "synthetic-batch-1"));
        register.register(new LegacyNumberEntry(new DocumentReference(DocumentType.QUOTE, doc(502)), "SYN-QU-0002", Optional.of(new LegalEntityId(TestIds.uuid(2))), "synthetic-batch-1"));

        mvc.perform(get(LEGACY + ":resolve").param("documentType", "QUOTE").param("legacyNumber", "SYN-QU-0001").with(LEGACY_READER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value(TestIds.uuid(501).toString()))
                .andExpect(jsonPath("$.legalEntityId").value(LE1.value().toString()))
                .andExpect(jsonPath("$.importBatch").value("synthetic-batch-1"));
        mvc.perform(get(LEGACY).param("documentType", "QUOTE").param("documentId", TestIds.uuid(501).toString()).with(LEGACY_READER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].legacyNumber").value("SYN-QU-0001"))
                .andExpect(jsonPath("$.items[1].legacyNumber").value("SYN-QU-0001-R2"));
        mvc.perform(get(LEGACY + ":resolve").param("documentType", "QUOTE").param("legacyNumber", "SYN-QU-9999").with(LEGACY_READER))
                .andExpect(status().isNotFound());
        mvc.perform(get(LEGACY).param("documentType", "QUOTE").param("documentId", TestIds.uuid(999).toString()).with(LEGACY_READER))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
        assertThat(findDocument.find(DocumentType.TIMESHEET, "SYN-QU-0001")).isEmpty();
    }

    @Test
    void legacyNumberSearchNeedsThePermission() throws Exception {
        mvc.perform(get(LEGACY + ":resolve").param("documentType", "QUOTE").param("legacyNumber", "X")).andExpect(status().isUnauthorized());
        mvc.perform(get(LEGACY + ":resolve").param("documentType", "QUOTE").param("legacyNumber", "X").with(UPDATER)).andExpect(status().isForbidden());
        mvc.perform(get(LEGACY).param("documentType", "QUOTE").param("documentId", TestIds.uuid(1).toString()).with(UPDATER)).andExpect(status().isForbidden());
        mvc.perform(get(LEGACY + ":resolve").param("documentType", "QUOTE").param("legacyNumber", "X").with(READER)).andExpect(status().isForbidden());
        mvc.perform(get(LEGACY).param("documentType", "QUOTE").param("documentId", TestIds.uuid(1).toString()).with(READER)).andExpect(status().isForbidden());
        mvc.perform(get(RULES).with(LEGACY_READER)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("D-1a-152 an original number whose legal entity could not be worked out is stored without one and stays searchable")
    void legacyNumberWithoutLegalEntityIsStoredAndFound() throws Exception {
        register.register(new LegacyNumberEntry(new DocumentReference(DocumentType.QUOTE, doc(601)), "SYN-QU-NOLE", Optional.empty(), "synthetic-batch-5"));
        register.register(new LegacyNumberEntry(new DocumentReference(DocumentType.QUOTE, doc(601)), "SYN-QU-NOLE", Optional.empty(), "synthetic-batch-5"));

        assertThat(jdbc.sql("select count(*) from platform_numbering.legacy_numbers where legacy_number = 'SYN-QU-NOLE' and legal_entity_id is null")
                .query(Integer.class).single()).isEqualTo(1);
        assertThat(findDocument.find(DocumentType.QUOTE, "SYN-QU-NOLE")).hasValueSatisfying(e -> assertThat(e.legalEntity()).isEmpty());
        mvc.perform(get(LEGACY + ":resolve").param("documentType", "QUOTE").param("legacyNumber", "SYN-QU-NOLE").with(LEGACY_READER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value(TestIds.uuid(601).toString()))
                .andExpect(jsonPath("$.legalEntityId").doesNotExist());
        mvc.perform(get(LEGACY).param("documentType", "QUOTE").param("documentId", TestIds.uuid(601).toString()).with(LEGACY_READER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].legacyNumber").value("SYN-QU-NOLE"));
    }

    @Test
    @DisplayName("(document type, original number) is unique: re-running an import is a no-op, a second owner is refused")
    void legacyNumberIsUniquePerDocumentType() {
        LegacyNumberEntry first = new LegacyNumberEntry(new DocumentReference(DocumentType.TIMESHEET, doc(11)), "SYN-TS-1", Optional.of(LE1), "synthetic-batch-2");
        register.register(first);
        register.register(first);

        assertThat(jdbc.sql("select count(*) from platform_numbering.legacy_numbers where legacy_number = 'SYN-TS-1'")
                .query(Integer.class).single()).isEqualTo(1);
        assertThatThrownBy(() -> register.register(new LegacyNumberEntry(new DocumentReference(DocumentType.TIMESHEET, doc(12)), "SYN-TS-1", Optional.of(LE1), "synthetic-batch-3")))
                .isInstanceOfSatisfying(com.forcitis.one.kernel.DomainException.class, e -> assertThat(e.errorCode())
                        .isEqualTo(com.forcitis.one.platform.numbering.api.NumberingError.PLATFORM_NUMBERING_LEGACY_NUMBER_TAKEN));
        register.register(new LegacyNumberEntry(new DocumentReference(DocumentType.JOURNAL_VOUCHER, doc(12)), "SYN-TS-1", Optional.of(LE1), "synthetic-batch-3"));
        assertThat(findDocument.find(DocumentType.JOURNAL_VOUCHER, "SYN-TS-1")).isPresent();
    }

    @Test
    void theDatabaseItselfEnforcesTheUniqueConstraint() {
        String insert = "insert into platform_numbering.legacy_numbers (id, document_type, legacy_number, document_id, legal_entity_id, import_batch)"
                + " values (uuidv7(), 'QUOTE', 'SYN-DB-1', '" + TestIds.uuid(1) + "', '" + TestIds.uuid(1) + "', 'synthetic-batch-4')";
        jdbc.sql(insert).update();

        assertThatThrownBy(() -> jdbc.sql(insert).update()).isInstanceOf(DuplicateKeyException.class);
    }

    private static DocumentId doc(long number) {
        return new DocumentId(TestIds.uuid(number).toString());
    }

    @Test
    void entriesValidateThemselves() {
        assertThatThrownBy(() -> new LegacyNumberEntry(new DocumentReference(DocumentType.QUOTE, doc(1)), " ", Optional.of(LE1), "b")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LegacyNumberEntry(new DocumentReference(DocumentType.QUOTE, new DocumentId("7")), "X", Optional.of(LE1), "b")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LegacyNumberEntry(new DocumentReference(DocumentType.QUOTE, doc(1)), "X", Optional.of(LE1), "")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LegacyNumberEntry(new DocumentReference(DocumentType.QUOTE, doc(1)), "x".repeat(129), Optional.of(LE1), "b")).isInstanceOf(IllegalArgumentException.class);
    }
}
