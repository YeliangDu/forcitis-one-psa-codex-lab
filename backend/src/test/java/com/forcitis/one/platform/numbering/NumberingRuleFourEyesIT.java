package com.forcitis.one.platform.numbering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.forcitis.one.kernel.DocumentType;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.identity.api.Permission;
import com.forcitis.one.platform.identity.api.RoleDirectory;
import com.forcitis.one.platform.integration.api.WecomGateway;
import com.forcitis.one.platform.numbering.api.DocumentNumbers;
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
@DisplayName("NumberingRuleFourEyes: ADR-0027 section 2 item 4, D-1a-147, D-1a-157")
class NumberingRuleFourEyesIT {
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
    static final LegalEntityId LE1 = new LegalEntityId(1);
    static final RequestPostProcessor AUTHOR = user("9").authorities(() -> Permission.Codes.PLATFORM_NUMBERING_RULE_UPDATE,
            () -> Permission.Codes.PLATFORM_NUMBERING_RULE_READ, () -> Permission.Codes.PLATFORM_NUMBERING_RULE_APPROVE);
    static final RequestPostProcessor APPROVER = user("10").authorities(() -> Permission.Codes.PLATFORM_NUMBERING_RULE_READ,
            () -> Permission.Codes.PLATFORM_NUMBERING_RULE_APPROVE);
    static final RequestPostProcessor FINANCE = user("11").authorities(() -> Permission.Codes.PLATFORM_NUMBERING_RULE_READ,
            () -> Permission.Codes.PLATFORM_NUMBERING_VOUCHER_RULE_APPROVE);
    static final RequestPostProcessor FINANCE_AND_APPROVER = user("12").authorities(() -> Permission.Codes.PLATFORM_NUMBERING_RULE_APPROVE,
            () -> Permission.Codes.PLATFORM_NUMBERING_VOUCHER_RULE_APPROVE);
    static final RequestPostProcessor NEITHER = user("13").authorities(() -> Permission.Codes.PLATFORM_NUMBERING_RULE_READ,
            () -> Permission.Codes.PLATFORM_NUMBERING_RULE_UPDATE);

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    DocumentNumbers numbers;

    @Autowired
    TransactionTemplate tx;

    @BeforeEach
    void seededRulesAndLegalEntity() {
        NumberingFixtures.restoreSeededDefaults(jdbc);
        jdbc.sql("""
                insert into organization_workforce.legal_entities (id, code, name, status)
                values (1, 'FST', 'Synthetic legal entity one', 'ACTIVE') on conflict (id) do nothing
                """).update();
    }

    private String issue(DocumentType type) {
        return tx.execute(status -> numbers.next(type, LE1, LocalDate.of(2027, 1, 20)).value());
    }

    private void save(RequestPostProcessor who, String type, String pattern) throws Exception {
        mvc.perform(put(RULES + "/" + type).with(csrf()).with(who).contentType(MediaType.APPLICATION_JSON)
                .content("{\"pattern\":\"" + pattern + "\",\"resetPolicy\":\"MONTHLY\",\"startValue\":1}")).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("the seeded template waits for confirmation and issues nothing; one confirmation by a person with the approve permission puts it in force")
    void seededTemplateIssuesNothingUntilConfirmed() throws Exception {
        assertThatThrownBy(() -> issue(DocumentType.QUOTE)).isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).errorCode().code()).isEqualTo("PLATFORM_NUMBERING_RULE_NOT_CONFIRMED"));
        assertThat(jdbc.sql("select count(*) from platform_numbering.numbering_counters").query(Long.class).single()).as("and burns no number").isZero();

        mvc.perform(post(RULES + "/QUOTE:confirm").with(csrf()).with(APPROVER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmationStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.pending").doesNotExist());

        assertThat(issue(DocumentType.QUOTE)).isEqualTo("QT-FST-202701-0001");
    }

    @Test
    @DisplayName("the author of a version cannot confirm it, even holding the approve permission; someone else can")
    void theAuthorCannotConfirm() throws Exception {
        NumberingFixtures.confirmSeeded(jdbc, "QUOTE");
        save(AUTHOR, "QUOTE", "{TYPE}-{LE}-{yyyyMM}-{seq:5}");

        mvc.perform(post(RULES + "/QUOTE:confirm").with(csrf()).with(AUTHOR))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("PLATFORM_NUMBERING_CONFIRMER_IS_AUTHOR"));
        assertThat(issue(DocumentType.QUOTE)).as("the confirmed version keeps issuing meanwhile").isEqualTo("QT-FST-202701-0001");

        mvc.perform(post(RULES + "/QUOTE:confirm").with(csrf()).with(APPROVER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templateVersion").value(2))
                .andExpect(jsonPath("$.pattern").value("{TYPE}-{LE}-{yyyyMM}-{seq:5}"));
        assertThat(issue(DocumentType.QUOTE)).isEqualTo("QT-FST-202701-00002");
    }

    @Test
    @DisplayName("confirming needs the approve permission; the update permission alone saves versions but confirms none")
    void confirmingNeedsThePermission() throws Exception {
        mvc.perform(post(RULES + "/QUOTE:confirm").with(csrf()).with(NEITHER)).andExpect(status().isForbidden());
        mvc.perform(post(RULES + "/QUOTE:confirm").with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(post(RULES + "/QUOTE:confirm").with(APPROVER)).andExpect(status().isForbidden());
        mvc.perform(post(RULES + "/TIMESHEET:confirm-finance").with(csrf()).with(APPROVER)).andExpect(status().isForbidden());
        mvc.perform(put(RULES + "/QUOTE").with(csrf()).with(APPROVER).contentType(MediaType.APPLICATION_JSON)
                .content("{\"pattern\":\"{TYPE}-{yyyyMM}-{seq:5}\",\"resetPolicy\":\"MONTHLY\",\"startValue\":1}")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a second change before confirmation replaces the waiting version; nothing waits when nothing was changed")
    void replacingTheWaitingVersionAndNothingToConfirm() throws Exception {
        NumberingFixtures.confirmSeeded(jdbc, "QUOTE");
        mvc.perform(post(RULES + "/QUOTE:confirm").with(csrf()).with(APPROVER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PLATFORM_NUMBERING_NOTHING_TO_CONFIRM"));

        save(AUTHOR, "QUOTE", "{TYPE}-{yyyyMM}-{seq:5}");
        save(AUTHOR, "QUOTE", "{TYPE}-{yyyyMM}-{seq:6}");

        mvc.perform(get(RULES + "/QUOTE").with(APPROVER))
                .andExpect(jsonPath("$.pending.templateVersion").value(3))
                .andExpect(jsonPath("$.pending.pattern").value("{TYPE}-{yyyyMM}-{seq:6}"));
        assertThat(jdbc.sql("select status from platform_numbering.numbering_rule_versions where document_type = 'QUOTE' order by version_number")
                .query(String.class).list()).containsExactly("CONFIRMED", "SUPERSEDED", "PENDING_CONFIRMATION");
    }

    @Test
    @DisplayName("D-1a-157 a voucher template needs the business confirmation and the finance confirmation from two other people")
    void aVoucherTemplateNeedsTheFinanceConfirmationToo() throws Exception {
        mvc.perform(post(RULES + "/JOURNAL_VOUCHER:confirm").with(csrf()).with(APPROVER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmationStatus").value("PENDING_CONFIRMATION"))
                .andExpect(jsonPath("$.requiresFinanceConfirmation").value(true))
                .andExpect(jsonPath("$.pending.confirmedBy").value("10"));
        assertThatThrownBy(() -> issue(DocumentType.JOURNAL_VOUCHER)).isInstanceOf(DomainException.class);

        mvc.perform(post(RULES + "/JOURNAL_VOUCHER:confirm-finance").with(csrf()).with(FINANCE_AND_APPROVER))
                .andExpect(status().isOk());
        assertThat(issue(DocumentType.JOURNAL_VOUCHER)).isEqualTo("JV-FST-202701-0001");
        assertThat(jdbc.sql("select confirmed_by || '/' || finance_confirmed_by from platform_numbering.numbering_rule_versions where document_type = 'JOURNAL_VOUCHER'")
                .query(String.class).single()).isEqualTo("10/12");
    }

    @Test
    @DisplayName("D-1a-157 the same person cannot give both confirmations, and the finance confirmation is for vouchers only")
    void theSamePersonCannotConfirmTwice() throws Exception {
        mvc.perform(post(RULES + "/JOURNAL_VOUCHER:confirm").with(csrf()).with(FINANCE_AND_APPROVER)).andExpect(status().isOk());

        mvc.perform(post(RULES + "/JOURNAL_VOUCHER:confirm-finance").with(csrf()).with(FINANCE_AND_APPROVER))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("PLATFORM_NUMBERING_CONFIRMATION_REPEATED"));
        mvc.perform(post(RULES + "/JOURNAL_VOUCHER:confirm-finance").with(csrf()).with(FINANCE)).andExpect(status().isOk());

        mvc.perform(post(RULES + "/QUOTE:confirm-finance").with(csrf()).with(FINANCE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PLATFORM_NUMBERING_FINANCE_CONFIRMATION_NOT_REQUIRED"));
    }

    @Test
    @DisplayName("the database holds the line too: a saved version is immutable, and no version is confirmed by its own author")
    void theSchemaEnforcesImmutabilityAndFourEyes() {
        NumberingFixtures.confirmSeeded(jdbc, "QUOTE");
        jdbc.sql("""
                insert into platform_numbering.numbering_rule_versions (document_type, version_number, template_text, reset_policy, start_value, author_id, authored_at, status)
                values ('QUOTE', 2, '{TYPE}-{seq:4}', 'NEVER', 1, 9, now(), 'PENDING_CONFIRMATION')
                """).update();

        assertThatThrownBy(() -> jdbc.sql("update platform_numbering.numbering_rule_versions set template_text = '{TYPE}-{seq:9}' where version_number = 1").update())
                .hasMessageContaining("immutable");
        assertThatThrownBy(() -> jdbc.sql("delete from platform_numbering.numbering_rule_versions where document_type = 'QUOTE' and version_number = 1").update())
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.sql("update platform_numbering.numbering_rule_versions set confirmed_by = 9, confirmed_at = now(), status = 'CONFIRMED' where document_type = 'QUOTE' and version_number = 2").update())
                .hasMessageContaining("ck_numbering_rule_versions_confirmer_is_not_author");
        assertThatThrownBy(() -> jdbc.sql("""
                insert into platform_numbering.numbering_rule_versions (document_type, version_number, template_text, reset_policy, start_value, authored_at, status)
                values ('QUOTE', 3, '{TYPE}-{seq:4}', 'NEVER', 1, now(), 'PENDING_CONFIRMATION')
                """).update()).as("one waiting version per document type").hasMessageContaining("uk_numbering_rule_versions_document_type");
    }
}
