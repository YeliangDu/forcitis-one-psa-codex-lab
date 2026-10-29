package com.forcitis.one.acceptance.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.AccountsAcceptanceSupport;
import com.forcitis.one.kernel.PublicEndpoint;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * RULE-337 as corrected 2026-10-21 (ADR-0009 decisions 1 and 5, C1a-07): every endpoint not marked public answers an anonymous caller
 * with 401, and a caller-supplied identity header (login-user) is never read: who the caller is comes from the session only. The
 * endpoints are the ones the running application maps (the handler mapping), the public ones are those the application itself marks
 * with the kernel annotation PublicEndpoint.
 */
class AnonymousRequestsDeniedIT extends AccountsAcceptanceSupport {

    static final String CURRENT = "/api/v1/platform/sessions:current";
    /** Header the old gateway forwarded to the services as the caller's identity. */
    static final String FORGED_HEADER = "login-user";

    static final TestDatabase postgres = TestPostgres.database(AnonymousRequestsDeniedIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mappings;

    private record Endpoint(HttpMethod method, String path) {
        @Override
        public String toString() {
            return method + " " + path;
        }
    }

    /** Every method and path pattern of the application that is not marked public, path variables filled with a synthetic UUID. */
    private List<Endpoint> protectedEndpoints() {
        TreeSet<String> seen = new TreeSet<>();
        List<Endpoint> endpoints = new ArrayList<>();
        for (var mapped : mappings.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = mapped.getKey();
            HandlerMethod handler = mapped.getValue();
            if (handler.getMethodAnnotation(PublicEndpoint.class) != null || info.getPathPatternsCondition() == null) {
                continue;
            }
            List<String> methods = info.getMethodsCondition().getMethods().stream().map(Enum::name).toList();
            for (String method : methods.isEmpty() ? List.of("GET", "POST", "PUT", "PATCH", "DELETE") : methods) {
                for (String pattern : info.getPathPatternsCondition().getPatternValues()) {
                    String path = pattern.replaceAll("\\{[^/}]+\\}", UUID.randomUUID().toString());
                    if (seen.add(method + " " + path)) {
                        endpoints.add(new Endpoint(HttpMethod.valueOf(method), path));
                    }
                }
            }
        }
        return endpoints;
    }

    private MockHttpServletResponse call(Endpoint endpoint, String forgedIdentity) throws Exception {
        var builder = request(endpoint.method(), endpoint.path()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}")
                .header("If-Match", "\"0\"").header("Idempotency-Key", UUID.randomUUID().toString());
        if (forgedIdentity != null) {
            builder = builder.header(FORGED_HEADER, forgedIdentity);
        }
        return mvc.perform(builder).andReturn().getResponse();
    }

    @Test
    @DisplayName("RULE-337 C1a-07：未标记公开的端点，匿名请求一律 401（逐个端点，含所有写方法；带有效 CSRF 令牌，排除 403 来自 CSRF 的可能）")
    void everyEndpointThatIsNotPublicRefusesAnAnonymousCaller() throws Exception {
        List<Endpoint> endpoints = protectedEndpoints();
        assertThat(endpoints).as("the application maps many endpoints that are not public").hasSizeGreaterThan(20);

        List<String> notRefused = new ArrayList<>();
        for (Endpoint endpoint : endpoints) {
            int status = call(endpoint, null).getStatus();
            if (status != 401) {
                notRefused.add(endpoint + " answered " + status);
            }
        }

        assertThat(notRefused).as("anonymous calls of %d endpoints that are not marked public", endpoints.size()).isEmpty();
    }

    @Test
    @DisplayName("RULE-337：伪造的 login-user 请求头被忽略——匿名请求带着它仍是 401，逐个端点")
    void aForgedLoginUserHeaderDoesNotAuthenticate() throws Exception {
        UUID unit = newOrgUnitId();
        orgUnit(unit, null, null);
        UUID person = newEmployeeId();
        employee(person, unit, null);
        SignedIn someone = signIn(person);
        String forged = "{\"id\":\"" + someone.userId() + "\",\"userType\":2,\"tenantId\":1}";

        List<String> accepted = new ArrayList<>();
        for (Endpoint endpoint : protectedEndpoints()) {
            for (String header : List.of(forged, someone.userId().toString(), "1")) {
                int status = call(endpoint, header).getStatus();
                if (status != 401) {
                    accepted.add(endpoint + " with " + FORGED_HEADER + "=" + header + " answered " + status);
                }
            }
        }

        assertThat(accepted).isEmpty();
    }

    @Test
    @DisplayName("RULE-337：有会话时身份只来自会话——同一请求带着另一个人的 login-user 头，答出的仍是会话的用户")
    void theIdentityComesFromTheSessionNotFromTheHeader() throws Exception {
        UUID unit = newOrgUnitId();
        orgUnit(unit, null, null);
        UUID first = newEmployeeId();
        UUID second = newEmployeeId();
        employee(first, unit, null);
        employee(second, unit, null);
        SignedIn caller = signIn(first);
        SignedIn other = signIn(second);

        MockHttpServletResponse current = mvc.perform(get(CURRENT).cookie(new Cookie("SESSION", caller.session()))
                .header(FORGED_HEADER, "{\"id\":\"" + other.userId() + "\",\"userType\":2}")).andReturn().getResponse();

        assertThat(current.getStatus()).as(current.getContentAsString()).isEqualTo(200);
        assertThat((String) JsonPath.read(current.getContentAsString(), "$.userId")).isEqualTo(caller.userId().toString()).isNotEqualTo(other.userId().toString());
    }
}
