package com.forcitis.one.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.platform.identity.api.IdentityError;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** #1174 (ADR-0069 section 10, H-1): the Seeyon single sign-on login is gone, not only switched off; the signed-ticket login stays. */
class SsoRemovedIT extends IdentityIntegrationTest {

    static final List<String> GONE_CLASSES = List.of("com.forcitis.one.platform.identity.application.LoginWithSso",
            "com.forcitis.one.platform.identity.application.SsoSettings", "com.forcitis.one.platform.identity.application.HandleEmployeeRegistered",
            "com.forcitis.one.platform.integration.api.SsoTicketVerifier", "com.forcitis.one.platform.integration.api.SsoVerdict");

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mappings;

    @Test
    @DisplayName("#1174 no endpoint is mapped under sessions:sso-login and a post to it is not a login")
    void noSsoEndpoint() throws Exception {
        Set<String> paths = mappings.getHandlerMethods().keySet().stream().map(RequestMappingInfo::toString).collect(java.util.stream.Collectors.toSet());
        assertThat(paths).noneMatch(path -> path.contains("sso-login"));
        assertThat(paths).anyMatch(path -> path.contains("sessions:ticket-login"));

        MvcResult result = postJson("/api/v1/platform/sessions:sso-login", "{\"ticket\":\"x\"}");

        assertThat(result.getResponse().getStatus()).isIn(401, 403, 404, 405);
        assertThat(cookie(result, "SESSION")).isNull();
    }

    @Test
    @DisplayName("#1174 the single sign-on classes and the listener of EmployeeRegistered are no classes any more")
    void classesAreGone() {
        for (String name : GONE_CLASSES) {
            assertThatThrownBy(() -> Class.forName(name)).as(name).isInstanceOf(ClassNotFoundException.class);
        }
    }

    @Test
    @DisplayName("#1174 the error code of the unavailable portal is gone")
    void errorCodeIsGone() {
        assertThat(java.util.Arrays.stream(IdentityError.values()).map(Enum::name)).doesNotContain("PLATFORM_IDENTITY_SSO_UNAVAILABLE");
    }
}
