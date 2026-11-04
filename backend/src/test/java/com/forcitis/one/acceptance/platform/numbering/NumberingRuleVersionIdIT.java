package com.forcitis.one.acceptance.platform.numbering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestIds;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.kernel.DocumentTypeCode;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.workforce.api.LegalEntityCode;
import com.forcitis.one.organization.workforce.api.LegalEntityDirectory;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.identity.api.Permission;
import com.forcitis.one.platform.identity.api.RoleDirectory;
import com.forcitis.one.platform.integration.api.WecomGateway;
import com.forcitis.one.platform.numbering.api.DocumentNumbers;
import com.forcitis.one.platform.numbering.api.IssuedNumber;
import com.forcitis.one.platform.numbering.api.NumberingRuleVersionId;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * ADR-0033 item 1 point 5 (card NUM-VERSION-ID): a number taken from the numbering module names the rule version it was issued
 * under, by the primary key of that version row, so that a consumer can reference the version.
 */
@SpringBootTest(properties = {"flowable.async-executor-activate=false", "flowable.database-schema-update=true",
        "spring.datasource.hikari.connection-timeout=2000"})
@AutoConfigureMockMvc
class NumberingRuleVersionIdIT {
    @MockitoBean
    OrgDirectory orgDirectory;

    @MockitoBean
    RoleDirectory roleDirectory;

    @MockitoBean
    WecomGateway wecomGateway;

    @MockitoBean
    AuditLog auditLog;

    @MockitoBean
    LegalEntityDirectory legalEntityDirectory;

    @MockitoSpyBean
    IdGenerator ids;

    static final TestDatabase postgres = TestPostgres.database(NumberingRuleVersionIdIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    static final String RULES = "/api/v1/platform/numbering-rules";
    static final String VERSIONS = "platform_numbering.numbering_rule_versions";
    static final LegalEntityId LE1 = new LegalEntityId(TestIds.uuid(1));
    static final LocalDate FAR_DAY = LocalDate.of(2099, 1, 20);
    static final RequestPostProcessor AUTHOR = user(TestAccounts.account(9).value().toString()).authorities(
            () -> Permission.Codes.PLATFORM_NUMBERING_RULE_UPDATE, () -> Permission.Codes.PLATFORM_NUMBERING_RULE_READ);
    static final RequestPostProcessor APPROVER = user(TestAccounts.account(10).value().toString()).authorities(
            () -> Permission.Codes.PLATFORM_NUMBERING_RULE_READ, () -> Permission.Codes.PLATFORM_NUMBERING_RULE_APPROVE);

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    DocumentNumbers numbers;

    @Autowired
    TransactionTemplate tx;

    @BeforeEach
    void seededQuoteRuleInForce() {
        jdbc.sql("alter table " + VERSIONS + " disable trigger numbering_rule_versions_guard").update();
        try {
            jdbc.sql("delete from " + VERSIONS).update();
            jdbc.sql("""
                    insert into platform_numbering.numbering_rule_versions (numbering_rule_version_id, numbering_rule_id, version_number, template_text, reset_policy, start_value, effective_from_date, authored_at, status, source_kind, confirmed_by, confirmed_at)
                    select uuidv7(), numbering_rule_id, 1, '{TYPE}-{LE}-{yyyyMM}-{seq:4}', 'MONTHLY', 1, DATE '2000-01-01', now(), 'CONFIRMED', 'SEEDED', '01900000-0000-7000-8000-000000000101', now()
                      from platform_numbering.numbering_rules
                    """).update();
        } finally {
            jdbc.sql("alter table " + VERSIONS + " enable trigger numbering_rule_versions_guard").update();
        }
        jdbc.sql("update platform_numbering.numbering_rules set version = 0, event_count = 0").update();
        jdbc.sql("delete from platform_numbering.numbering_counters").update();
        Mockito.when(legalEntityDirectory.lockActiveCode(ArgumentMatchers.any())).thenReturn(Optional.empty());
        Mockito.when(legalEntityDirectory.lockActiveCode(LE1)).thenReturn(Optional.of(new LegalEntityCode("FST")));
    }

    private IssuedNumber issue(LocalDate day) {
        return tx.execute(status -> numbers.next(new DocumentTypeCode("QUOTE"), LE1, day));
    }

    private UUID versionRowId(int versionNumber) {
        return jdbc.sql("select numbering_rule_version_id from " + VERSIONS
                + " where numbering_rule_id = (select numbering_rule_id from platform_numbering.numbering_rules where document_type_id = (select document_type_id from platform_numbering.document_types where code = 'QUOTE')) and version_number = :n")
                .param("n", versionNumber).query(UUID.class).single();
    }

    private String versionStatus(int versionNumber) {
        return jdbc.sql("select status from " + VERSIONS
                + " where numbering_rule_id = (select numbering_rule_id from platform_numbering.numbering_rules where document_type_id = (select document_type_id from platform_numbering.document_types where code = 'QUOTE')) and version_number = :n")
                .param("n", versionNumber).query(String.class).single();
    }

    private void saveSecondVersionEffectiveTomorrow() throws Exception {
        saveSecondVersionEffectiveOn(LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(1));
    }

    private void saveSecondVersionEffectiveOn(LocalDate effectiveFrom) throws Exception {
        String etag = "\"" + jdbc.sql("select version from platform_numbering.numbering_rules where document_type_id = (select document_type_id from platform_numbering.document_types where code = 'QUOTE')").query(Long.class).single() + "\"";
        String tomorrow = effectiveFrom.toString();
        mvc.perform(put(RULES + "/QUOTE").header("If-Match", etag).with(csrf()).with(AUTHOR).contentType(MediaType.APPLICATION_JSON)
                .content("{\"pattern\":\"{TYPE}-{LE}-{yyyyMM}-{seq:5}\",\"resetPolicy\":\"MONTHLY\",\"startValue\":1,\"effectiveFrom\":\"" + tomorrow + "\"}"))
                .andExpect(status().isOk());
    }

    private void confirmSecondVersion() throws Exception {
        mvc.perform(post(RULES + "/QUOTE:confirm").with(csrf()).with(APPROVER).contentType(MediaType.APPLICATION_JSON)
                .content("{\"templateVersion\":2}")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("NV-01 取号返回的 ruleVersionId 等于所用已确认模板版本在库里的主键")
    void issuedNumberNamesTheVersionRowItWasIssuedUnder() {
        IssuedNumber issued = issue(FAR_DAY);

        assertThat(issued.ruleVersionId()).isEqualTo(new NumberingRuleVersionId(versionRowId(1)));
    }

    // RULE-491
    @Test
    @DisplayName("NV-02 规则新增版本后，新版本取号返回的是新版本的 ID，旧日期仍返回旧版本的 ID（生效日分界）")
    void eachBusinessDateGetsTheIdOfTheVersionInForceThen() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        saveSecondVersionEffectiveOn(today.plusDays(1));
        confirmSecondVersion();
        UUID first = versionRowId(1);
        UUID second = versionRowId(2);

        assertThat(second).isNotEqualTo(first);
        assertThat(issue(today).ruleVersionId()).isEqualTo(new NumberingRuleVersionId(first));
        assertThat(issue(FAR_DAY).ruleVersionId()).isEqualTo(new NumberingRuleVersionId(second));
    }

    @Test
    @DisplayName("NV-03 版本被确认、被取代时 ID 不变（同一版本行在状态流转前后 ID 相同）")
    void aVersionKeepsItsIdWhenItsStatusMoves() throws Exception {
        saveSecondVersionEffectiveTomorrow();
        UUID firstBefore = versionRowId(1);
        UUID secondPending = versionRowId(2);
        assertThat(versionStatus(2)).isEqualTo("PENDING_CONFIRMATION");

        confirmSecondVersion();

        assertThat(versionStatus(2)).isEqualTo("CONFIRMED");
        assertThat(versionRowId(2)).isEqualTo(secondPending);
        assertThat(versionRowId(1)).isEqualTo(firstBefore);
        assertThat(issue(FAR_DAY).ruleVersionId()).isEqualTo(new NumberingRuleVersionId(secondPending));
    }

    @Test
    @DisplayName("NV-03b 待确认版本被后一次修改取代时，被取代版本的 ID 不变")
    void aSupersededVersionKeepsItsId() throws Exception {
        saveSecondVersionEffectiveTomorrow();
        UUID secondId = versionRowId(2);
        assertThat(versionStatus(2)).isEqualTo("PENDING_CONFIRMATION");

        saveSecondVersionEffectiveTomorrow();

        assertThat(versionStatus(2)).isEqualTo("SUPERSEDED");
        assertThat(versionRowId(2)).isEqualTo(secondId);
        assertThat(versionRowId(3)).isNotEqualTo(secondId);
    }

    @Test
    @DisplayName("NV-06 库里版本行的主键就是新增版本时创建的那个 ID（插入不另取新 ID）")
    void insertedRowCarriesTheIdCreatedWhenTheVersionWasProposed() throws Exception {
        UUID proposed = UUID.fromString("01900000-0000-7000-8000-0000000000a1");
        Mockito.doReturn(proposed).doCallRealMethod().when(ids).next();

        saveSecondVersionEffectiveTomorrow();

        assertThat(versionRowId(2)).isEqualTo(proposed);
    }

    @Test
    @DisplayName("NV-04 同一版本的多次取号返回同一个 ID")
    void sameVersionGivesTheSameIdEveryTime() {
        IssuedNumber first = issue(FAR_DAY);
        IssuedNumber second = issue(FAR_DAY);
        IssuedNumber third = issue(FAR_DAY.plusDays(1));

        assertThat(second.number()).isNotEqualTo(first.number());
        assertThat(second.ruleVersionId()).isEqualTo(first.ruleVersionId());
        assertThat(third.ruleVersionId()).isEqualTo(first.ruleVersionId());
    }
}
