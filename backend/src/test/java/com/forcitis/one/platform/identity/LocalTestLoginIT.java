package com.forcitis.one.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

/** Card #1478: under the profile local the test login picks an existing account and starts an ordinary session; it adds no grant and skips no admission check. */
@ActiveProfiles("local")
@TestPropertySource(properties = "spring.flyway.enabled=true")
class LocalTestLoginIT extends IdentityIntegrationTest {

    static final String ACCOUNTS = "/api/v1/platform/local-accounts";
    static final String LOGIN = "/api/v1/platform/local-sessions";

    @BeforeEach
    void people() {
        knownEmployee(1001, EmploymentStatus.EMPLOYED);
        createAccount(1001, "employee");
        knownEmployee(1002, EmploymentStatus.EMPLOYED);
        createAccount(1002, "employee", "super_admin");
        knownEmployee(1003, EmploymentStatus.SEPARATED);
        createAccount(1003, "employee");
    }

    private MvcResult pick(UUID account) throws Exception {
        return postJson(LOGIN, "{\"accountId\":\"" + account + "\"}");
    }

    @Test
    @DisplayName("#1478 the list holds the enabled accounts with the employee's name and the roles held now")
    void listsAccounts() throws Exception {
        MvcResult listed = mvc.perform(get(ACCOUNTS)).andReturn();

        assertThat(listed.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(listed).findValuesAsString("userId")).contains(accountOf(1001).toString(), accountOf(1002).toString());
        assertThat(body(listed).toString()).contains("\"name\":\"Synthetic\"", "super_admin");
    }

    @Test
    @DisplayName("#1478 picking an account starts an ordinary session of that account, with only the grants it holds")
    void sessionOfThePickedAccount() throws Exception {
        MvcResult login = pick(accountOf(1001));

        assertThat(login.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(login).path("userId").asString()).isEqualTo(accountOf(1001).toString());
        assertThat(body(login).path("identityConfirmationRequired").asBoolean()).isFalse();
        Cookie session = new Cookie("SESSION", cookie(login, "SESSION"));
        MvcResult current = mvc.perform(get("/api/v1/platform/sessions:current").cookie(session)).andReturn();
        assertThat(current.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(current).toString()).contains(accountOf(1001).toString());
        // no grant bypass: the employee role does not read the role list, as for any other login
        assertThat(mvc.perform(get("/api/v1/platform/roles").cookie(session)).andReturn().getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("#1478 the admission checks of every login still apply: a separated employee and an unknown account get no session")
    void admissionStillApplies() throws Exception {
        for (UUID refused : new UUID[] {accountOf(1003), UUID.fromString("01900000-0000-7000-8000-0000000000ff"), UUID.randomUUID()}) {
            MvcResult result = pick(refused);
            assertThat(result.getResponse().getStatus()).as(refused.toString()).isIn(401, 403, 404);
            assertThat(cookie(result, "SESSION")).isNull();
        }
    }

    @Test
    @DisplayName("#1478 the audit trail records the login with its method in the detail")
    void audited() throws Exception {
        pick(accountOf(1001));

        assertThat(recorded()).anySatisfy(entry -> {
            assertThat(entry.action()).isEqualTo("platform.identity.login.succeeded");
            assertThat(entry.detail()).contains("method=LOCAL_TEST");
            assertThat(entry.loginMethod()).isNull();
        });
    }

    @Test
    @DisplayName("#1478 no audit entry of a local session names LOCAL_TEST as its login method (the column's CHECK does not list it): refused login, logout")
    void noEntryNamesTheMethodInTheColumn() throws Exception {
        pick(accountOf(1003));
        assertThat(recordedIndependently()).anySatisfy(entry -> {
            assertThat(entry.action()).isEqualTo("platform.identity.login.failed");
            assertThat(entry.loginMethod()).isNull();
        });

        MvcResult other = pick(accountOf(1002));
        assertThat(enterAdministratorMode(cookie(other, "SESSION"), 1001).getResponse().getStatus()).isEqualTo(403);
        assertThat(recordedIndependently()).anySatisfy(entry -> assertThat(entry.action()).contains("administrator-mode"));

        MvcResult login = pick(accountOf(1001));
        Cookie session = new Cookie("SESSION", cookie(login, "SESSION"));
        mvc.perform(post("/api/v1/platform/sessions:logout").cookie(session).with(xsrf())).andReturn();
        assertThat(recorded()).anySatisfy(entry -> {
            assertThat(entry.action()).isEqualTo("platform.identity.logout");
            assertThat(entry.loginMethod()).isNull();
        });
        assertThat(recorded()).noneMatch(entry -> "LOCAL_TEST".equals(entry.loginMethod()));
        assertThat(recordedIndependently()).noneMatch(entry -> "LOCAL_TEST".equals(entry.loginMethod()));
    }
}
