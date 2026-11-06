package com.forcitis.one.platform.numbering;

import com.forcitis.one.BusinessDay;
import com.forcitis.one.TestDatabase;
import com.forcitis.one.fixture.doubles.TestAccounts;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.forcitis.one.TestIds;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.DocumentTypeCode;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.identity.api.Permission;
import com.forcitis.one.platform.identity.api.RoleDirectory;
import com.forcitis.one.platform.integration.api.WecomGateway;
import com.forcitis.one.platform.numbering.api.DocumentNumbers;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {"flowable.async-executor-activate=false", "flowable.database-schema-update=true",
        "spring.datasource.hikari.connection-timeout=2000"})
@AutoConfigureMockMvc
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

    @MockitoBean
    com.forcitis.one.organization.workforce.api.LegalEntityDirectory legalEntityDirectory;

    static final TestDatabase postgres = TestPostgres.database(NumberingRuleFourEyesIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    static final String RULES = "/api/v1/platform/numbering-rules";
    static final LegalEntityId LE1 = new LegalEntityId(TestIds.uuid(1));
    static final LocalDate BUSINESS_DAY = LocalDate.of(2099, 1, 20);
    static final RequestPostProcessor AUTHOR = user(TestAccounts.account(9).value().toString()).authorities(() -> Permission.Codes.PLATFORM_NUMBERING_RULE_UPDATE,
            () -> Permission.Codes.PLATFORM_NUMBERING_RULE_READ, () -> Permission.Codes.PLATFORM_NUMBERING_RULE_APPROVE);
    static final RequestPostProcessor APPROVER = user(TestAccounts.account(10).value().toString()).authorities(() -> Permission.Codes.PLATFORM_NUMBERING_RULE_READ,
            () -> Permission.Codes.PLATFORM_NUMBERING_RULE_APPROVE);
    static final RequestPostProcessor FINANCE = user(TestAccounts.account(11).value().toString()).authorities(() -> Permission.Codes.PLATFORM_NUMBERING_RULE_READ,
            () -> Permission.Codes.PLATFORM_NUMBERING_VOUCHER_RULE_APPROVE);
    static final RequestPostProcessor FINANCE_AND_APPROVER = user(TestAccounts.account(12).value().toString()).authorities(() -> Permission.Codes.PLATFORM_NUMBERING_RULE_APPROVE,
            () -> Permission.Codes.PLATFORM_NUMBERING_VOUCHER_RULE_APPROVE);
    static final RequestPostProcessor NEITHER = user(TestAccounts.account(13).value().toString()).authorities(() -> Permission.Codes.PLATFORM_NUMBERING_RULE_READ,
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
        org.mockito.Mockito.when(legalEntityDirectory.lockActiveCode(org.mockito.ArgumentMatchers.any())).thenReturn(java.util.Optional.empty());
        org.mockito.Mockito.when(legalEntityDirectory.lockActiveCode(LE1))
                .thenReturn(java.util.Optional.of(new com.forcitis.one.organization.workforce.api.LegalEntityCode("FST")));
    }

    private String issue(DocumentTypeCode type) {
        return tx.execute(status -> numbers.next(type, LE1, BUSINESS_DAY).value());
    }

    private String issueOn(LocalDate day) {
        return tx.execute(status -> numbers.next(new DocumentTypeCode("QUOTE"), LE1, day).value());
    }

    private static LocalDate today() {
        return BusinessDay.today();
    }

    private static LocalDate tomorrow() {
        return today().plusDays(1);
    }

    private ResultActions save(RequestPostProcessor who, String type, String pattern, LocalDate effectiveFrom) throws Exception {
        return mvc.perform(put(RULES + "/" + type).header("If-Match", ruleVersion(type)).with(csrf()).with(who).contentType(MediaType.APPLICATION_JSON)
                .content("{\"pattern\":\"" + pattern + "\",\"resetPolicy\":\"MONTHLY\",\"startValue\":1,\"effectiveFrom\":\"" + effectiveFrom + "\"}"));
    }

    private String ruleVersion(String type) {
        return "\"" + jdbc.sql("select version from platform_numbering.numbering_rules where document_type_id = (select document_type_id from platform_numbering.document_types where code = :t)").param("t", type).query(Long.class).single() + "\"";
    }

    private ResultActions confirm(RequestPostProcessor who, String type, int templateVersion) throws Exception {
        return mvc.perform(post(RULES + "/" + type + ":confirm").with(csrf()).with(who).contentType(MediaType.APPLICATION_JSON)
                .content("{\"templateVersion\":" + templateVersion + "}"));
    }

    private ResultActions confirmByFinance(RequestPostProcessor who, String type, int templateVersion) throws Exception {
        return mvc.perform(post(RULES + "/" + type + ":confirm-finance").with(csrf()).with(who).contentType(MediaType.APPLICATION_JSON)
                .content("{\"templateVersion\":" + templateVersion + "}"));
    }

    @Test
    @DisplayName("the seeded template waits for confirmation and issues nothing; one confirmation by a person with the approve permission puts it in force")
    void seededTemplateIssuesNothingUntilConfirmed() throws Exception {
        assertThatThrownBy(() -> issue(new DocumentTypeCode("QUOTE"))).isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).errorCode().code()).isEqualTo("PLATFORM_NUMBERING_RULE_NOT_CONFIRMED"));
        assertThat(jdbc.sql("select count(*) from platform_numbering.numbering_counters").query(Long.class).single()).as("and burns no number").isZero();

        confirm(APPROVER, "QUOTE", 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmationStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.pending").doesNotExist());

        assertThat(issue(new DocumentTypeCode("QUOTE"))).isEqualTo("QT-FST-209901-0001");
    }

    @Test
    @DisplayName("the author of a version cannot confirm it, even holding the approve permission; someone else can")
    void theAuthorCannotConfirm() throws Exception {
        NumberingFixtures.confirmSeeded(jdbc, "QUOTE");
        save(AUTHOR, "QUOTE", "{TYPE}-{LE}-{yyyyMM}-{seq:5}", tomorrow()).andExpect(status().isOk());

        confirm(AUTHOR, "QUOTE", 2)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("PLATFORM_NUMBERING_CONFIRMER_IS_AUTHOR"));
        assertThat(issue(new DocumentTypeCode("QUOTE"))).as("the confirmed version keeps issuing meanwhile").isEqualTo("QT-FST-209901-0001");

        confirm(APPROVER, "QUOTE", 2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templateVersion").value(2))
                .andExpect(jsonPath("$.pattern").value("{TYPE}-{LE}-{yyyyMM}-{seq:5}"));
        assertThat(issue(new DocumentTypeCode("QUOTE"))).isEqualTo("QT-FST-209901-00002");
    }

    @Test
    @DisplayName("B1-H1 a confirmation names the version the confirmer looked at: a version saved meanwhile makes it fail with 409, for both confirmations")
    void aNewerVersionSavedMeanwhileIsNotConfirmedBlind() throws Exception {
        NumberingFixtures.confirmSeeded(jdbc, "QUOTE");
        save(AUTHOR, "QUOTE", "{TYPE}-{LE}-{yyyyMM}-{seq:5}", tomorrow()).andExpect(status().isOk());
        save(AUTHOR, "QUOTE", "{TYPE}-{LE}-{yyyyMM}-{seq:6}", tomorrow()).andExpect(status().isOk());

        confirm(APPROVER, "QUOTE", 2)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PLATFORM_NUMBERING_VERSION_MISMATCH"));
        confirmByFinance(FINANCE, "JOURNAL_VOUCHER", 7).andExpect(status().isConflict());
        assertThat(jdbc.sql("select status from platform_numbering.numbering_rule_versions where numbering_rule_id = (select numbering_rule_id from platform_numbering.numbering_rules where document_type_id = (select document_type_id from platform_numbering.document_types where code = 'QUOTE')) and version_number = 3")
                .query(String.class).single()).isEqualTo("PENDING_CONFIRMATION");

        confirm(APPROVER, "QUOTE", 3).andExpect(status().isOk()).andExpect(jsonPath("$.pattern").value("{TYPE}-{LE}-{yyyyMM}-{seq:6}"));
        mvc.perform(post(RULES + "/QUOTE:confirm").with(csrf()).with(APPROVER).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    @DisplayName("confirming needs the approve permission; the update permission alone saves versions but confirms none")
    void confirmingNeedsThePermission() throws Exception {
        confirm(NEITHER, "QUOTE", 1).andExpect(status().isForbidden());
        mvc.perform(post(RULES + "/QUOTE:confirm").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"templateVersion\":1}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(RULES + "/QUOTE:confirm").with(APPROVER).contentType(MediaType.APPLICATION_JSON).content("{\"templateVersion\":1}"))
                .andExpect(status().isForbidden());
        confirmByFinance(APPROVER, "TIMESHEET", 1).andExpect(status().isForbidden());
        save(APPROVER, "QUOTE", "{TYPE}-{yyyyMM}-{seq:5}", tomorrow()).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a second change before confirmation replaces the waiting version; nothing waits when nothing was changed")
    void replacingTheWaitingVersionAndNothingToConfirm() throws Exception {
        NumberingFixtures.confirmSeeded(jdbc, "QUOTE");
        confirm(APPROVER, "QUOTE", 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PLATFORM_NUMBERING_NOTHING_TO_CONFIRM"));

        save(AUTHOR, "QUOTE", "{TYPE}-{yyyyMM}-{seq:5}", tomorrow()).andExpect(status().isOk());
        save(AUTHOR, "QUOTE", "{TYPE}-{yyyyMM}-{seq:6}", tomorrow()).andExpect(status().isOk());

        mvc.perform(get(RULES + "/QUOTE").with(APPROVER))
                .andExpect(jsonPath("$.pending.templateVersion").value(3))
                .andExpect(jsonPath("$.pending.pattern").value("{TYPE}-{yyyyMM}-{seq:6}"));
        assertThat(jdbc.sql("select status from platform_numbering.numbering_rule_versions where numbering_rule_id = (select numbering_rule_id from platform_numbering.numbering_rules where document_type_id = (select document_type_id from platform_numbering.document_types where code = 'QUOTE')) order by version_number")
                .query(String.class).list()).containsExactly("CONFIRMED", "SUPERSEDED", "PENDING_CONFIRMATION");
    }

    @Test
    @DisplayName("B1-H3 the author's effective date is part of the version: issuing uses the highest confirmed version that has started by the business date")
    void issuingFollowsTheEffectiveDate() throws Exception {
        NumberingFixtures.confirmSeeded(jdbc, "QUOTE");
        save(AUTHOR, "QUOTE", "{TYPE}-{LE}-{yyyyMM}-{seq:5}", BUSINESS_DAY.plusMonths(1)).andExpect(status().isOk());
        confirm(APPROVER, "QUOTE", 2).andExpect(status().isOk());

        assertThat(issueOn(BUSINESS_DAY)).as("before the new template starts").isEqualTo("QT-FST-209901-0001");
        assertThat(issueOn(BUSINESS_DAY.plusMonths(1))).as("from its day on")
                .isEqualTo("QT-FST-209902-00001");
        assertThat(issueOn(BUSINESS_DAY.plusMonths(2))).isEqualTo("QT-FST-209903-00001");
    }

    @Test
    @DisplayName("B1-H3 / user decision: a day earlier than the confirmation day is refused with a clear code, and the author's date is never rewritten")
    void anEffectiveDateInThePastIsRefused() throws Exception {
        NumberingFixtures.confirmSeeded(jdbc, "QUOTE");
        LocalDate past = today().minusDays(1);
        save(AUTHOR, "QUOTE", "{TYPE}-{LE}-{yyyyMM}-{seq:5}", past).andExpect(status().isOk());

        confirm(APPROVER, "QUOTE", 2)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errorCode").value("PLATFORM_NUMBERING_EFFECTIVE_DATE_PASSED"));

        assertThat(jdbc.sql("select effective_from_date from platform_numbering.numbering_rule_versions where numbering_rule_id = (select numbering_rule_id from platform_numbering.numbering_rules where document_type_id = (select document_type_id from platform_numbering.document_types where code = 'QUOTE')) and version_number = 2")
                .query(LocalDate.class).single()).isEqualTo(past);
        save(AUTHOR, "QUOTE", "{TYPE}-{LE}-{yyyyMM}-{seq:5}", today()).andExpect(status().isOk());
        confirm(APPROVER, "QUOTE", 3).andExpect(status().isOk());
    }

    @Test
    @DisplayName("a template saved without an effective date is refused")
    void theEffectiveDateIsRequired() throws Exception {
        mvc.perform(put(RULES + "/QUOTE").header("If-Match", ruleVersion("QUOTE")).with(csrf()).with(AUTHOR).contentType(MediaType.APPLICATION_JSON)
                .content("{\"pattern\":\"{TYPE}-{seq:4}\",\"resetPolicy\":\"NEVER\",\"startValue\":1}")).andExpect(status().isUnprocessableContent());
    }

    @Test
    @DisplayName("D-1a-157 a voucher template needs the business confirmation and the finance confirmation from two other people")
    void aVoucherTemplateNeedsTheFinanceConfirmationToo() throws Exception {
        confirm(APPROVER, "JOURNAL_VOUCHER", 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmationStatus").value("PENDING_CONFIRMATION"))
                .andExpect(jsonPath("$.requiresFinanceConfirmation").value(true))
                .andExpect(jsonPath("$.pending.confirmedBy").value(TestAccounts.account(10).value().toString()));
        assertThatThrownBy(() -> issue(new DocumentTypeCode("JOURNAL_VOUCHER"))).isInstanceOf(DomainException.class);

        confirmByFinance(FINANCE_AND_APPROVER, "JOURNAL_VOUCHER", 1).andExpect(status().isOk());
        assertThat(issue(new DocumentTypeCode("JOURNAL_VOUCHER"))).isEqualTo("JV-FST-209901-0001");
        assertThat(jdbc.sql("select confirmed_by || '/' || finance_confirmed_by from platform_numbering.numbering_rule_versions where numbering_rule_id = (select numbering_rule_id from platform_numbering.numbering_rules where document_type_id = (select document_type_id from platform_numbering.document_types where code = 'JOURNAL_VOUCHER'))")
                .query(String.class).single()).isEqualTo(TestAccounts.account(10).value() + "/" + TestAccounts.account(12).value());
    }

    @Test
    @DisplayName("D-1a-157 the same person cannot give both confirmations, and the finance confirmation is for vouchers only")
    void theSamePersonCannotConfirmTwice() throws Exception {
        confirm(FINANCE_AND_APPROVER, "JOURNAL_VOUCHER", 1).andExpect(status().isOk());

        confirmByFinance(FINANCE_AND_APPROVER, "JOURNAL_VOUCHER", 1)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("PLATFORM_NUMBERING_CONFIRMATION_REPEATED"));
        confirmByFinance(FINANCE, "JOURNAL_VOUCHER", 1).andExpect(status().isOk());

        confirmByFinance(FINANCE, "QUOTE", 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PLATFORM_NUMBERING_FINANCE_CONFIRMATION_NOT_REQUIRED"));
    }

    @Test
    @DisplayName("the database holds the line too: a saved version is immutable, a confirmation is only ever added, and no version is confirmed by its own author")
    void theSchemaEnforcesImmutabilityAndFourEyes() {
        NumberingFixtures.confirmSeeded(jdbc, "QUOTE");
        jdbc.sql("""
                insert into platform_numbering.numbering_rule_versions (numbering_rule_version_id, numbering_rule_id, version_number, template_text, reset_policy, start_value, effective_from_date, author_id, authored_at, status, source_kind)
                select uuidv7(), numbering_rule_id, 2, '{TYPE}-{seq:4}', 'NEVER', 1, DATE '2099-02-01', :author, now(), 'PENDING_CONFIRMATION', 'AUTHORED'
                  from platform_numbering.numbering_rules where document_type_id = (select document_type_id from platform_numbering.document_types where code = 'QUOTE')
                """).param("author", TestAccounts.account(9).value()).update();

        assertThatThrownBy(() -> jdbc.sql("update platform_numbering.numbering_rule_versions set template_text = '{TYPE}-{seq:9}' where version_number = 1").update())
                .hasMessageContaining("immutable");
        assertThatThrownBy(() -> jdbc.sql("update platform_numbering.numbering_rule_versions set effective_from_date = DATE '2001-01-01' where version_number = 2").update())
                .hasMessageContaining("immutable");
        assertThatThrownBy(() -> jdbc.sql("delete from platform_numbering.numbering_rule_versions where numbering_rule_id = (select numbering_rule_id from platform_numbering.numbering_rules where document_type_id = (select document_type_id from platform_numbering.document_types where code = 'QUOTE')) and version_number = 1").update())
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.sql("update platform_numbering.numbering_rule_versions set confirmed_by = " + TestAccounts.sql(9) + "::uuid, confirmed_at = now(), status = 'CONFIRMED' where numbering_rule_id = (select numbering_rule_id from platform_numbering.numbering_rules where document_type_id = (select document_type_id from platform_numbering.document_types where code = 'QUOTE')) and version_number = 2").update())
                .hasMessageContaining("ck_numbering_rule_versions_confirmer_is_not_author");
        assertThatThrownBy(() -> jdbc.sql("update platform_numbering.numbering_rule_versions set confirmed_by = " + TestAccounts.sql(55) + "::uuid, confirmed_at = now() where numbering_rule_id = (select numbering_rule_id from platform_numbering.numbering_rules where document_type_id = (select document_type_id from platform_numbering.document_types where code = 'QUOTE')) and version_number = 1").update())
                .as("B1-M4 a confirmation cannot be replaced").hasMessageContaining("cannot be changed or taken back");
        assertThatThrownBy(() -> jdbc.sql("update platform_numbering.numbering_rule_versions set confirmed_by = null, confirmed_at = null where numbering_rule_id = (select numbering_rule_id from platform_numbering.numbering_rules where document_type_id = (select document_type_id from platform_numbering.document_types where code = 'QUOTE')) and version_number = 1").update())
                .as("nor taken back").hasMessageContaining("cannot be changed or taken back");
        assertThatThrownBy(() -> jdbc.sql("update platform_numbering.numbering_rule_versions set status = 'PENDING_CONFIRMATION' where numbering_rule_id = (select numbering_rule_id from platform_numbering.numbering_rules where document_type_id = (select document_type_id from platform_numbering.document_types where code = 'QUOTE')) and version_number = 1").update())
                .as("a confirmed version never goes back").hasMessageContaining("cannot go from CONFIRMED to PENDING_CONFIRMATION");
        assertThatThrownBy(() -> jdbc.sql("update platform_numbering.numbering_rule_versions set status = 'SUPERSEDED' where numbering_rule_id = (select numbering_rule_id from platform_numbering.numbering_rules where document_type_id = (select document_type_id from platform_numbering.document_types where code = 'QUOTE')) and version_number = 1").update())
                .hasMessageContaining("cannot go from CONFIRMED to SUPERSEDED");
        assertThatThrownBy(() -> jdbc.sql("""
                insert into platform_numbering.numbering_rule_versions (numbering_rule_version_id, numbering_rule_id, version_number, template_text, reset_policy, start_value, effective_from_date, authored_at, status, source_kind)
                select uuidv7(), numbering_rule_id, 3, '{TYPE}-{seq:4}', 'NEVER', 1, DATE '2099-02-01', now(), 'PENDING_CONFIRMATION', 'SEEDED'
                  from platform_numbering.numbering_rules where document_type_id = (select document_type_id from platform_numbering.document_types where code = 'QUOTE')
                """).update()).as("one waiting version per document type").hasMessageContaining("uk_numbering_rule_versions_numbering_rule_id");
    }
}
