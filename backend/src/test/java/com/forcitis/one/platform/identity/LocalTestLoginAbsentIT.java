package com.forcitis.one.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.forcitis.one.kernel.SettableClock;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.platform.identity.application.LoginAsLocalTestUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** Card #1478: without the profile local the test login does not exist: no bean, no mapped path, and the paths are refused like any unknown path of the API. */
class LocalTestLoginAbsentIT extends IdentityIntegrationTest {

    @Autowired
    ApplicationContext context;

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mapping;

    @Test
    @DisplayName("#1478 no profile local: neither the service nor the controller is a bean, and no path of it is mapped")
    void notThere() {
        assertThat(context.getBeanNamesForType(LoginAsLocalTestUser.class)).isEmpty();
        assertThat(context.containsBean("localTestLoginController")).isFalse();
        assertThat(context.getBeanNamesForType(SettableClock.class)).isEmpty();
        assertThat(context.containsBean("localClockController")).isFalse();
        assertThat(mapping.getHandlerMethods().keySet().stream().flatMap(info -> info.getPatternValues().stream()))
                .noneMatch(pattern -> pattern.contains("/local-"));
    }

    @Test
    @DisplayName("#1478 no profile local: both paths are refused, a request that names an existing account gets no session")
    void refused() throws Exception {
        knownEmployee(1001, EmploymentStatus.EMPLOYED);
        createAccount(1001, "employee");

        var listed = mvc.perform(get("/api/v1/platform/local-accounts")).andReturn();
        var login = postJson("/api/v1/platform/local-sessions", "{\"accountId\":\"" + accountOf(1001) + "\"}");

        assertThat(listed.getResponse().getStatus()).isIn(401, 403, 404);
        assertThat(mvc.perform(get("/api/v1/platform/local-clocks")).andReturn().getResponse().getStatus()).isIn(401, 403, 404);
        assertThat(mvc.perform(post("/api/v1/platform/local-clocks:set").with(xsrf()).contentType("application/json").content("{\"at\":\"2026-03-15T08:00:00Z\"}")).andReturn().getResponse().getStatus()).isIn(401, 403, 404);
        assertThat(login.getResponse().getStatus()).isIn(401, 403, 404);
        assertThat(cookie(login, "SESSION")).isNull();
    }
}
