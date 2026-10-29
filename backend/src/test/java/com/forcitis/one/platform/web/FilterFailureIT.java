package com.forcitis.one.platform.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.identity.api.RoleDirectory;
import com.forcitis.one.platform.integration.api.WecomGateway;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

/** A failure in a filter happens before the dispatcher; it still answers a ProblemDetail with the correlation id. */
@SpringBootTest(properties = {"flowable.async-executor-activate=false", "flowable.database-schema-update=true",
        "forcitis.platform.approval.assignment.process-administrator-user-id=9001"})
@AutoConfigureMockMvc
@Import(FilterFailureIT.FailingFilter.class)
class FilterFailureIT {
    static final String HEADER = "X-Fail-In-Filter";

    @TestConfiguration(proxyBeanMethods = false)
    static class FailingFilter {
        @Bean
        @Order(Ordered.LOWEST_PRECEDENCE)
        Filter failingFilter() {
            return (request, response, chain) -> {
                if (((HttpServletRequest) request).getHeader(HEADER) != null) {
                    throw new IllegalStateException("a filter broke");
                }
                chain.doFilter(request, response);
            };
        }
    }

    @MockitoBean
    OrgDirectory orgDirectory;

    @MockitoBean
    RoleDirectory roleDirectory;

    @MockitoBean
    WecomGateway wecomGateway;

    @MockitoBean
    AuditLog auditLog;

    static final TestDatabase postgres = TestPostgres.database(FilterFailureIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper json;

    @Test
    @DisplayName("an exception thrown by a filter is answered as application/problem+json 500 PLATFORM_WEB_UNEXPECTED with the correlation id, and says nothing of the cause")
    void filterFailureIsAProblem() throws Exception {
        MvcResult result = mvc.perform(get("/actuator/health").header(HEADER, "1")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(500);
        assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
        assertThat(result.getResponse().getHeader(WorkContext.HEADER)).isNotBlank();
        var body = json.readTree(result.getResponse().getContentAsString());
        assertThat(body.path("errorCode").asString()).isEqualTo("PLATFORM_WEB_UNEXPECTED");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("a filter broke");
    }
}
