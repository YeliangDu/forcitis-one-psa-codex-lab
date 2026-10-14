package com.forcitis.one.platform.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.identity.api.RoleDirectory;
import com.forcitis.one.platform.integration.api.WecomGateway;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {"flowable.async-executor-activate=false", "flowable.database-schema-update=true",
        "spring.datasource.hikari.connection-timeout=2000",
        "spring.flyway.locations=classpath:db/migration,classpath:db/testdata"})
@AutoConfigureMockMvc
@Testcontainers
class HttpContractIT {
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

    @Autowired
    MockMvc mvc;

    @Test
    @DisplayName("the version lives in the path: /api/1/... is not a route, /api/v1/... is")
    void numericVersionSegmentIsNotFound() throws Exception {
        mvc.perform(get("/api/1/organization/legal-entities").with(user("1"))).andExpect(status().isNotFound())
                .andExpect(header().exists(WorkContext.HEADER));
        mvc.perform(get("/api/v2/organization/legal-entities").with(user("1"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/organization/legal-entities").with(user("1")))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(404));
    }

    @Test
    @DisplayName("an unknown route answers a ProblemDetail with the correlation id of the request")
    void notFoundIsAProblemDetail() throws Exception {
        UUID given = UUID.randomUUID();
        mvc.perform(get("/api/1/organization/legal-entities").with(user("1")).header(WorkContext.HEADER, given.toString()))
                .andExpect(status().isNotFound())
                .andExpect(header().string(WorkContext.HEADER, given.toString()))
                .andExpect(jsonPath("$.status").value(404));
    }
}
