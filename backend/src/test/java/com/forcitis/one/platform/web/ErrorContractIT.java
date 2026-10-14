package com.forcitis.one.platform.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.identity.api.Permission;
import com.forcitis.one.platform.identity.api.RoleDirectory;
import com.forcitis.one.platform.integration.api.WecomGateway;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Review H-3 (guard 5): every endpoint of every controller answers a refusal as application/problem+json with an errorCode that the
 * contract lists - when nobody is signed in (401), when the permission is missing (403), and when a permitted caller sends nonsense.
 */
@SpringBootTest(properties = {"flowable.async-executor-activate=false", "flowable.database-schema-update=true",
        "forcitis.platform.approval.assignment.process-administrator-user-id=9001", "spring.datasource.hikari.connection-timeout=2000"})
@AutoConfigureMockMvc
@Testcontainers
class ErrorContractIT {
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

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mapping;

    @Autowired
    JsonMapper json;

    private record Endpoint(HttpMethod method, String path, HandlerMethod handler, boolean publicEndpoint) {
        @Override
        public String toString() {
            return method + " " + path;
        }
    }

    private List<Endpoint> endpoints() {
        List<Endpoint> found = new ArrayList<>();
        mapping.getHandlerMethods().forEach((info, handler) -> {
            if (!handler.getBeanType().getName().startsWith("com.forcitis.one.")) {
                return;
            }
            boolean publicEndpoint = handler.hasMethodAnnotation(com.forcitis.one.kernel.PublicEndpoint.class);
            for (String pattern : info.getPatternValues()) {
                for (var method : info.getMethodsCondition().getMethods()) {
                    found.add(new Endpoint(HttpMethod.valueOf(method.name()), pattern, handler, publicEndpoint));
                }
            }
        });
        return found;
    }

    private static String concrete(String pattern) {
        Matcher variable = Pattern.compile("\\{([^}/]+)}").matcher(pattern);
        StringBuilder out = new StringBuilder();
        while (variable.find()) {
            String name = variable.group(1);
            variable.appendReplacement(out, name.equals("documentType") ? "QUOTE" : name.equals("calendarYear") ? "2026" : "424242");
        }
        variable.appendTail(out);
        return out.toString();
    }

    private MockHttpServletRequestBuilder request(Endpoint endpoint) {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.request(endpoint.method(), concrete(endpoint.path()));
        if (endpoint.method() != HttpMethod.GET) {
            builder.contentType(MediaType.APPLICATION_JSON).content("{}");
        }
        return builder;
    }

    private void assertProblem(Endpoint endpoint, String situation, MockHttpServletResponse response, List<String> problems) throws Exception {
        if (response.getStatus() < 400) {
            return;
        }
        String type = response.getContentType();
        if (type == null || !type.startsWith("application/problem+json")) {
            problems.add(endpoint + " " + situation + ": " + response.getStatus() + " answers " + type + ", not application/problem+json");
            return;
        }
        JsonNode body = json.readTree(response.getContentAsString());
        if (!body.hasNonNull("errorCode") || !body.hasNonNull("status") || body.path("status").asInt() != response.getStatus()) {
            problems.add(endpoint + " " + situation + ": the problem has no errorCode or a wrong status: " + response.getContentAsString());
        }
    }

    @Test
    @DisplayName("401, 403 and the refusals of a permitted caller are application/problem+json with errorCode on every endpoint")
    void everyRefusalIsAProblem() throws Exception {
        List<GrantedAuthority> all = Arrays.stream(Permission.values()).<GrantedAuthority>map(p -> p::code).toList();
        List<String> problems = new ArrayList<>();
        List<Endpoint> endpoints = endpoints();
        assertThat(endpoints).as("the test must see the controllers").hasSizeGreaterThan(20);
        for (Endpoint endpoint : endpoints) {
            if (!endpoint.publicEndpoint()) {
                MockHttpServletResponse anonymous = mvc.perform(request(endpoint).with(
                        org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())).andReturn().getResponse();
                if (endpoint.method() != HttpMethod.GET) {
                    MockHttpServletResponse forged = mvc.perform(request(endpoint)).andReturn().getResponse();
                    assertProblem(endpoint, "without a CSRF token", forged, problems);
                }
                assertProblem(endpoint, "anonymous", anonymous, problems);
                if (anonymous.getStatus() != 401) {
                    problems.add(endpoint + " anonymous: expected 401, was " + anonymous.getStatus());
                }
                MockHttpServletResponse nothing = mvc.perform(request(endpoint).with(user("5").authorities(List.of())).with(
                        org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())).andReturn().getResponse();
                assertProblem(endpoint, "without permission", nothing, problems);
            }
            MockHttpServletResponse permitted = mvc.perform(request(endpoint).with(user("5").authorities(all)).with(
                    org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())).andReturn().getResponse();
            assertProblem(endpoint, "permitted caller, nonsense input", permitted, problems);
        }
        assertThat(problems).isEmpty();
    }
}
