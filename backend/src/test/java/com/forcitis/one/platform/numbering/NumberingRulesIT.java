package com.forcitis.one.platform.numbering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
        "forcitis.platform.approval.assignment.process-administrator-user-id=9001",
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

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18")
            .withInitScript("init-schemas.sql")
            .withUrlParam("currentSchema", "platform_approval");

    static final String RULES = "/api/v1/platform/numbering-rules";
    static final String LEGACY = "/api/v1/platform/legacy-numbers";
    static final LegalEntityId LE1 = new LegalEntityId(1);
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
        jdbc.sql("""
                insert into organization_workforce.legal_entities (id, code, name, status)
                values (1, 'FST', 'Synthetic legal entity one', 'ACTIVE'), (2, 'FIT', 'Synthetic legal entity two', 'ACTIVE')
                on conflict (id) do nothing
                """).update();
    }

    private String issue(DocumentType type, LegalEntityId legalEntity, LocalDate day) {
        return tx.execute(status -> numbers.next(type, legalEntity, day).value());
    }

    @Test
    @DisplayName("C1a-16 the rule list shows every rule; the quote starts on the unified default template, pending confirmation")
    void ruleListShowsTheDefaultQuoteTemplateAsPending() throws Exception {
        mvc.perform(get(RULES).with(READER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[?(@.documentType == 'QUOTE')].pattern").value("{TYPE}-{LE}-{yyyyMM}-{seq:4}"))
                .andExpect(jsonPath("$[?(@.documentType == 'QUOTE')].resetPolicy").value("MONTHLY"))
                .andExpect(jsonPath("$[?(@.documentType == 'QUOTE')].documentTypeCode").value("QT"))
                .andExpect(jsonPath("$[?(@.documentType == 'QUOTE')].confirmationStatus").value("PENDING_CONFIRMATION"))
                .andExpect(jsonPath("$[?(@.documentType == 'QUOTE')].capacity").value(9999));
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
        LocalDate day = LocalDate.of(2026, 10, 14);

        assertThat(issue(DocumentType.QUOTE, LE1, day)).isEqualTo("QT-FST-202610-0001");
        assertThat(issue(DocumentType.QUOTE, LE1, day)).isEqualTo("QT-FST-202610-0002");
        assertThat(issue(DocumentType.QUOTE, new LegalEntityId(2), day)).isEqualTo("QT-FIT-202610-0001");
    }

    @Test
    @DisplayName("an unknown legal entity cannot take a number, whatever the template")
    void unknownLegalEntityCannotIssue() {
        LocalDate day = LocalDate.of(2027, 3, 3);

        assertThatThrownBy(() -> issue(DocumentType.QUOTE, new LegalEntityId(99), day))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("99");
        assertThatThrownBy(() -> issue(DocumentType.TIMESHEET, new LegalEntityId(99), day))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("99");
    }

    @Test
    @DisplayName("a deactivated legal entity cannot take a new number")
    void deactivatedLegalEntityCannotIssue() {
        LocalDate day = LocalDate.of(2027, 4, 4);
        jdbc.sql("insert into organization_workforce.legal_entities (id, code, name, status) values (3, 'FOT', 'Synthetic three', 'INACTIVE') on conflict (id) do update set status = 'INACTIVE'").update();
        try {
            assertThatThrownBy(() -> issue(DocumentType.QUOTE, new LegalEntityId(3), day))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("3");
            assertThatThrownBy(() -> issue(DocumentType.TIMESHEET, new LegalEntityId(3), day))
                    .isInstanceOf(IllegalStateException.class);
        } finally {
            jdbc.sql("delete from organization_workforce.legal_entities where id = 3").update();
        }
    }

    @Test
    @DisplayName("a template without {LE} (configured later by an administrator) shares one counter across legal entities, so numbers never repeat")
    void templateWithoutLegalEntityTokenSharesOneCounter() {
        LocalDate day = LocalDate.of(2027, 5, 6);
        jdbc.sql("update platform_numbering.numbering_rules set pattern = '{TYPE}-{yyyyMMdd}-{seq:4}', reset_policy = 'DAILY' where document_type = 'TIMESHEET'").update();
        try {
            String first = issue(DocumentType.TIMESHEET, LE1, day);
            String second = issue(DocumentType.TIMESHEET, new LegalEntityId(2), day);

            assertThat(first).isEqualTo("TS-20270506-0001");
            assertThat(second).isEqualTo("TS-20270506-0002");
        } finally {
            jdbc.sql("update platform_numbering.numbering_rules set pattern = '{TYPE}-{LE}-{yyyyMM}-{seq:4}', reset_policy = 'MONTHLY' where document_type = 'TIMESHEET'").update();
        }
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
        LocalDate day = LocalDate.of(2027, 6, 7);
        assertThat(issue(DocumentType.TIMESHEET, LE1, day)).isEqualTo("TS-FST-202706-0001");
        assertThat(issue(DocumentType.TIMESHEET, new LegalEntityId(2), day)).isEqualTo("TS-FIT-202706-0001");
        assertThat(issue(DocumentType.JOURNAL_VOUCHER, LE1, day)).isEqualTo("JV-FST-202706-0001");
    }

    @Test
    @DisplayName("saving a rule is the administrator's confirmation")
    void savingConfirmsTheRule() throws Exception {
        try {
            mvc.perform(put(RULES + "/QUOTE").with(csrf()).with(UPDATER).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"pattern\":\"{TYPE}-{LE}-{yyyyMM}-{seq:5}\",\"resetPolicy\":\"MONTHLY\",\"startValue\":1}"))
                    .andExpect(status().isNoContent());
            mvc.perform(get(RULES + "/QUOTE").with(READER))
                    .andExpect(jsonPath("$.confirmationStatus").value("CONFIRMED"))
                    .andExpect(jsonPath("$.pattern").value("{TYPE}-{LE}-{yyyyMM}-{seq:5}"));
        } finally {
            // put the shared fixture back to the seeded default so other tests keep seeing the pending template
            jdbc.sql("update platform_numbering.numbering_rules set pattern = '{TYPE}-{LE}-{yyyyMM}-{seq:4}',"
                    + " confirmation_status = 'PENDING_CONFIRMATION' where document_type = 'QUOTE'").update();
        }
    }

    @Test
    @DisplayName("C1a-16 an original number finds the new document, and the new document finds its original numbers")
    void legacyNumbersAreSearchableBothWays() throws Exception {
        register.register(new LegacyNumberEntry(new DocumentReference(DocumentType.QUOTE, new DocumentId("501")), "SYN-QU-0001", LE1, "synthetic-batch-1"));
        register.register(new LegacyNumberEntry(new DocumentReference(DocumentType.QUOTE, new DocumentId("501")), "SYN-QU-0001-R2", LE1, "synthetic-batch-1"));
        register.register(new LegacyNumberEntry(new DocumentReference(DocumentType.QUOTE, new DocumentId("502")), "SYN-QU-0002", new LegalEntityId(2), "synthetic-batch-1"));

        mvc.perform(get(LEGACY).param("documentType", "QUOTE").param("legacyNumber", "SYN-QU-0001").with(LEGACY_READER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value("501"))
                .andExpect(jsonPath("$.legalEntityId").value("1"))
                .andExpect(jsonPath("$.importBatch").value("synthetic-batch-1"));
        mvc.perform(get(LEGACY).param("documentType", "QUOTE").param("documentId", "501").with(LEGACY_READER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].legacyNumber").value("SYN-QU-0001"))
                .andExpect(jsonPath("$[1].legacyNumber").value("SYN-QU-0001-R2"));
        mvc.perform(get(LEGACY).param("documentType", "QUOTE").param("legacyNumber", "SYN-QU-9999").with(LEGACY_READER))
                .andExpect(status().isNotFound());
        mvc.perform(get(LEGACY).param("documentType", "QUOTE").param("documentId", "999").with(LEGACY_READER))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        assertThat(findDocument.find(DocumentType.TIMESHEET, "SYN-QU-0001")).isEmpty();
    }

    @Test
    void legacyNumberSearchNeedsThePermission() throws Exception {
        mvc.perform(get(LEGACY).param("documentType", "QUOTE").param("legacyNumber", "X")).andExpect(status().isUnauthorized());
        mvc.perform(get(LEGACY).param("documentType", "QUOTE").param("legacyNumber", "X").with(UPDATER)).andExpect(status().isForbidden());
        mvc.perform(get(LEGACY).param("documentType", "QUOTE").param("documentId", "1").with(UPDATER)).andExpect(status().isForbidden());
        mvc.perform(get(LEGACY).param("documentType", "QUOTE").param("legacyNumber", "X").with(READER)).andExpect(status().isForbidden());
        mvc.perform(get(LEGACY).param("documentType", "QUOTE").param("documentId", "1").with(READER)).andExpect(status().isForbidden());
        mvc.perform(get(RULES).with(LEGACY_READER)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("(document type, original number) is unique: re-running an import is a no-op, a second owner is refused")
    void legacyNumberIsUniquePerDocumentType() {
        LegacyNumberEntry first = new LegacyNumberEntry(new DocumentReference(DocumentType.TIMESHEET, new DocumentId("11")), "SYN-TS-1", LE1, "synthetic-batch-2");
        register.register(first);
        register.register(first);

        assertThat(jdbc.sql("select count(*) from platform_numbering.legacy_numbers where legacy_number = 'SYN-TS-1'")
                .query(Integer.class).single()).isEqualTo(1);
        assertThatThrownBy(() -> register.register(new LegacyNumberEntry(new DocumentReference(DocumentType.TIMESHEET, new DocumentId("12")), "SYN-TS-1", LE1, "synthetic-batch-3")))
                .isInstanceOf(IllegalStateException.class);
        register.register(new LegacyNumberEntry(new DocumentReference(DocumentType.JOURNAL_VOUCHER, new DocumentId("12")), "SYN-TS-1", LE1, "synthetic-batch-3"));
        assertThat(findDocument.find(DocumentType.JOURNAL_VOUCHER, "SYN-TS-1")).isPresent();
    }

    @Test
    void theDatabaseItselfEnforcesTheUniqueConstraint() {
        String insert = "insert into platform_numbering.legacy_numbers (document_type, legacy_number, document_id, legal_entity_id, import_batch)"
                + " values ('QUOTE', 'SYN-DB-1', 1, 1, 'synthetic-batch-4')";
        jdbc.sql(insert).update();

        assertThatThrownBy(() -> jdbc.sql(insert).update()).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void entriesValidateThemselves() {
        assertThatThrownBy(() -> new LegacyNumberEntry(new DocumentReference(DocumentType.QUOTE, new DocumentId("1")), " ", LE1, "b")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LegacyNumberEntry(new DocumentReference(DocumentType.QUOTE, new DocumentId("0")), "X", LE1, "b")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LegacyNumberEntry(new DocumentReference(DocumentType.QUOTE, new DocumentId("1")), "X", LE1, "")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LegacyNumberEntry(new DocumentReference(DocumentType.QUOTE, new DocumentId("1")), "x".repeat(129), LE1, "b")).isInstanceOf(IllegalArgumentException.class);
    }
}
