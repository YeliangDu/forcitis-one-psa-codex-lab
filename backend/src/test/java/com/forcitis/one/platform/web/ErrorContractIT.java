package com.forcitis.one.platform.web;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.fixture.doubles.TestAccounts;
import static org.assertj.core.api.Assertions.assertThat;
import static com.forcitis.one.platform.approval.OrdinarySessions.user;

import com.forcitis.one.TestPostgres;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Review H-3 (guard 5): every endpoint of every controller answers a refusal as application/problem+json with an errorCode that the
 * contract lists - when nobody is signed in (401), when the permission is missing (403), and when a permitted caller sends nonsense.
 */
@SpringBootTest(properties = {"flowable.async-executor-activate=false", "flowable.database-schema-update=true",
        "forcitis.platform.approval.assignment.process-administrator-user-id=9001", "spring.datasource.hikari.connection-timeout=2000"})
@AutoConfigureMockMvc
class ErrorContractIT {
    @MockitoBean
    OrgDirectory orgDirectory;

    @MockitoBean
    RoleDirectory roleDirectory;

    @MockitoBean
    WecomGateway wecomGateway;

    @MockitoBean
    AuditLog auditLog;

    static final TestDatabase postgres = TestPostgres.database(ErrorContractIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mapping;

    @Autowired
    JsonMapper json;

    /**
     * Endpoints that still answer 500 to an empty body, owned by the approval work stream (a null outcome is dereferenced before it is
     * checked). Remove the entry when it is fixed: the test fails for an entry that is no longer needed.
     */
    private static final java.util.Set<String> KNOWN_SERVER_FAULTS = java.util.Set.of();

    private final java.util.Set<String> serverFaults = new java.util.TreeSet<>();

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
        if (response.getStatus() == 500) {
            serverFaults.add(endpoint.toString());
            if (!KNOWN_SERVER_FAULTS.contains(endpoint.toString())) {
                problems.add(endpoint + " " + situation + ": answers 500 " + body.path("errorCode").asString()
                        + "; nonsense from the caller must be refused with 4xx, not break the server");
            }
        }
        if (!body.hasNonNull("errorCode") || !body.hasNonNull("status") || body.path("status").asInt() != response.getStatus()) {
            problems.add(endpoint + " " + situation + ": the problem has no errorCode or a wrong status: " + response.getContentAsString());
        }
    }

    @Test
    @DisplayName("401, 403 and the refusals of a permitted caller are application/problem+json with errorCode on every endpoint")
    void everyRefusalIsAProblem() throws Exception {
        List<GrantedAuthority> all = com.forcitis.one.platform.approval.OrdinarySessions.with(Arrays.stream(Permission.values()).map(Permission::code).toArray(String[]::new));
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
                    if (forged.getStatus() != 401 && forged.getStatus() != 403) {
                        problems.add(endpoint + " without a CSRF token: expected 401 or 403, was " + forged.getStatus());
                    }
                }
                assertProblem(endpoint, "anonymous", anonymous, problems);
                if (anonymous.getStatus() != 401) {
                    problems.add(endpoint + " anonymous: expected 401, was " + anonymous.getStatus());
                }
                MockHttpServletResponse nothing = mvc.perform(request(endpoint).with(user(TestAccounts.account(5).value().toString()).authorities(List.of())).with(
                        org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())).andReturn().getResponse();
                assertProblem(endpoint, "without permission", nothing, problems);
                // Method security runs after the body is bound, so an unreadable body is answered 400 before the permission is looked at.
                var authorization = endpoint.handler().getMethodAnnotation(org.springframework.security.access.prepost.PreAuthorize.class);
                boolean bindingFirst = nothing.getStatus() == 400 && nothing.getContentAsString().contains("PLATFORM_WEB_REQUEST_INVALID");
                if (authorization != null && authorization.value().contains("hasAuthority") && nothing.getStatus() != 403 && !bindingFirst) {
                    problems.add(endpoint + " without permission: expected 403, was " + nothing.getStatus());
                }
            }
            MockHttpServletResponse permitted = mvc.perform(request(endpoint).with(user(TestAccounts.account(5).value().toString()).authorities(all)).with(
                    org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())).andReturn().getResponse();
            assertProblem(endpoint, "permitted caller, nonsense input", permitted, problems);
        }
        assertThat(problems).isEmpty();
        assertThat(serverFaults).as("an entry of KNOWN_SERVER_FAULTS that no longer fails must be deleted").isEqualTo(KNOWN_SERVER_FAULTS);
    }
}
