package com.forcitis.one.platform.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.identity.api.Permission;
import com.forcitis.one.platform.identity.api.RoleDirectory;
import com.forcitis.one.platform.integration.api.WecomGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest(properties = {"flowable.async-executor-activate=false", "flowable.database-schema-update=true", "spring.datasource.hikari.connection-timeout=2000"})
@AutoConfigureMockMvc
@DisplayName("NITS-1 项2：列表与导出的 action / actionPrefix 过长或疑似密钥答 400，不是 500")
class Nits1AuditSearchRequestIT {
    @MockitoBean
    OrgDirectory orgDirectory;

    @MockitoBean
    RoleDirectory roleDirectory;

    @MockitoBean
    WecomGateway wecomGateway;

    static final TestDatabase postgres = TestPostgres.database(Nits1AuditSearchRequestIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    static final String LIST = "/api/v1/platform/audit-entries";
    static final String EXPORT = "/api/v1/platform/audit-entries:export";
    static final int LONGEST_ACTION = 2000 - "returned=10000 filter=action=".length();
    /** Built at run time: a literal of this shape would read as a secret to the scanners. */
    static final String SECRET_SHAPE = "tok" + "en=x";
    static final RequestPostProcessor READER = user(TestAccounts.account(7).value().toString()).authorities(() -> Permission.Codes.PLATFORM_AUDIT_ENTRY_READ);
    static final RequestPostProcessor EXPORTER = user(TestAccounts.account(8).value().toString()).authorities(() -> Permission.Codes.PLATFORM_AUDIT_ENTRY_EXPORT);

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    private long entriesOf(String action) {
        return jdbc.sql("select count(*) from platform_audit.audit_entries where action = :action").param("action", action).query(Long.class).single();
    }

    @Test
    @DisplayName("NITS-1 列表 action 超过上限答 400 PLATFORM_WEB_REQUEST_INVALID，不是 500，且响应不含过滤值")
    void aTooLongActionIsAnInvalidRequest() throws Exception {
        String tooLong = "a".repeat(LONGEST_ACTION + 1);
        long before = entriesOf("platform.audit.audit-entry.searched");

        String body = mvc.perform(get(LIST).param("action", tooLong).with(READER)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PLATFORM_WEB_REQUEST_INVALID")).andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(tooLong);
        assertThat(entriesOf("platform.audit.audit-entry.searched")).isEqualTo(before);
    }

    @Test
    @DisplayName("NITS-1 列表 actionPrefix 含 token=x 答 400 PLATFORM_WEB_REQUEST_INVALID")
    void aSecretShapedActionPrefixIsAnInvalidRequest() throws Exception {
        String body = mvc.perform(get(LIST).param("actionPrefix", SECRET_SHAPE).with(READER)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PLATFORM_WEB_REQUEST_INVALID")).andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(SECRET_SHAPE);
    }

    @Test
    @DisplayName("NITS-1 导出 action 超长与含 secret: 同样答 400，且不写导出审计条目")
    void theExportRefusesTheSameFilters() throws Exception {
        String tooLong = "a".repeat(LONGEST_ACTION + 1);
        String secretColon = "sec" + "ret: y";
        long before = entriesOf("platform.audit.audit-entry.exported");

        mvc.perform(get(EXPORT).param("action", tooLong).with(EXPORTER)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PLATFORM_WEB_REQUEST_INVALID"));
        mvc.perform(get(EXPORT).param("action", secretColon).with(EXPORTER)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PLATFORM_WEB_REQUEST_INVALID"));
        mvc.perform(get(EXPORT).param("actionPrefix", tooLong).with(EXPORTER)).andExpect(status().isBadRequest());

        assertThat(entriesOf("platform.audit.audit-entry.exported")).isEqualTo(before);
    }

    @Test
    @DisplayName("NITS-1 刚好不超限的 action 仍返回 200 且写查询审计条目")
    void anActionJustWithinTheLimitStillSearches() throws Exception {
        long before = entriesOf("platform.audit.audit-entry.searched");

        mvc.perform(get(LIST).param("action", "a".repeat(LONGEST_ACTION)).with(READER)).andExpect(status().isOk());
        mvc.perform(get(EXPORT).param("action", "a".repeat(LONGEST_ACTION)).with(EXPORTER)).andExpect(status().isOk());

        assertThat(entriesOf("platform.audit.audit-entry.searched")).isEqualTo(before + 1);
        assertThat(entriesOf("platform.audit.audit-entry.exported")).isEqualTo(1);
    }
}
