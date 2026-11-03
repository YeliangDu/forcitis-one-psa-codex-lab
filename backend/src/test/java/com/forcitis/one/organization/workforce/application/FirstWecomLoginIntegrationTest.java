package com.forcitis.one.organization.workforce.application;

import static com.forcitis.one.fixture.doubles.TestOrgIds.sqlEmployee;
import static com.forcitis.one.organization.workforce.domain.Fixtures.head;
import static com.forcitis.one.organization.workforce.domain.Fixtures.manager;
import static com.forcitis.one.organization.workforce.domain.Fixtures.person;
import static com.forcitis.one.organization.workforce.domain.Fixtures.post;
import static com.forcitis.one.organization.workforce.domain.Fixtures.source;
import static com.forcitis.one.organization.workforce.domain.Fixtures.unit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.forcitis.one.kernel.AccountEmployees;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.SystemActors;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.kernel.event.ActorType;
import com.forcitis.one.organization.workforce.events.EmployeeRegistered;
import com.forcitis.one.platform.integration.api.SsoTicketVerifier;
import com.forcitis.one.platform.integration.api.SsoVerdict;
import com.forcitis.one.platform.integration.api.WecomEntry;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;
import com.forcitis.one.fixture.doubles.TestAccounts;

/** Login of a person the snapshot does not list yet: no session user and no job is acting, so the registration acts as SYSTEM. */
@TestPropertySource(properties = "forcitis.platform.identity.sso.enabled=true")
class FirstWecomLoginIntegrationTest extends WorkforceIntegrationSupport {
    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper json;

    @Autowired
    ApplicationEvents events;

    @Autowired
    AccountEmployees accountEmployees;

    @MockitoBean
    SsoTicketVerifier portal;

    private static MockHttpServletRequestBuilder withXsrf(MockHttpServletRequestBuilder request) {
        return request.cookie(new Cookie("XSRF-TOKEN", "test-xsrf-token")).header("X-XSRF-TOKEN", "test-xsrf-token");
    }

    private void aNewHireTheSnapshotDoesNotListYet() {
        orgSource.snapshot = source(List.of(unit(1, null), unit(2, 1L)), List.of(head(2, 1), manager(2, 2)), List.of(post(100)),
                List.of(person(1, 2, 100L, null)));
        SnapshotSeeder.seed(jdbc, orgSource.snapshot);
        orgSource.singleLookupResult = List.of(person(3001, 2, 100L, 1L, "13900003001", "wx-new-hire"));
    }

    private void registeredAndSignedIn(MvcResult login) throws Exception {
        assertThat(login.getResponse().getStatus()).as(login.getResponse().getContentAsString()).isEqualTo(200);
        assertThat(jdbc.sql("select count(*) from organization_workforce.employees where id = " + sqlEmployee(3001)).query(Integer.class).single()).isEqualTo(1);
        assertThat(accountEmployees.accountOf(TestAccounts.employee(3001))).as("the account is opened").isPresent();
        assertThat(events.stream(EmployeeRegistered.class).map(EmployeeRegistered::envelope)).singleElement().satisfies(envelope -> {
            assertThat(envelope.actorType()).isEqualTo(ActorType.SYSTEM);
            assertThat(envelope.actorId()).isEqualTo(SystemActors.idOf("organization.workforce.login-registration"));
        });
    }

    @Test
    @DisplayName("P2-F1 a WeCom login of an employee the snapshot does not list yet but the source finds registers them, opens the account and signs in")
    void firstLoginOfANewHireSignsIn() throws Exception {
        aNewHireTheSnapshotDoesNotListYet();
        when(wecomGateway.resolveWecomUserId("code-new-hire", WecomEntry.WORKBENCH)).thenReturn(Optional.of(new WecomUserId("wx-new-hire")));

        MvcResult issued = mvc.perform(withXsrf(post("/api/v1/platform/login-states")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"entry\":\"WORKBENCH\"}")).andReturn();
        assertThat(issued.getResponse().getStatus()).as(issued.getResponse().getContentAsString()).isEqualTo(200);
        String state = json.readTree(issued.getResponse().getContentAsString()).path("state").asString();
        Cookie binding = issued.getResponse().getCookie("loginBinding");

        registeredAndSignedIn(mvc.perform(withXsrf(post("/api/v1/platform/sessions:wecom-login")).cookie(binding).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"code-new-hire\",\"state\":\"" + state + "\",\"entry\":\"WORKBENCH\"}")).andReturn());
    }

    @Test
    @DisplayName("P2 second recheck L-5 a single sign-on login of an employee the snapshot does not list yet but the source finds registers them, opens the account and signs in")
    void firstSingleSignOnOfANewHireSignsIn() throws Exception {
        aNewHireTheSnapshotDoesNotListYet();
        when(portal.verify("ticket-new-hire")).thenReturn(new SsoVerdict.LoginName("13900003001"));

        registeredAndSignedIn(mvc.perform(withXsrf(post("/api/v1/platform/sessions:sso-login")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"ticket\":\"ticket-new-hire\"}")).andReturn());
    }
}
