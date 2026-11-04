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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.forcitis.one.kernel.AccountEmployees;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.platform.integration.api.WecomEntry;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;
import com.forcitis.one.fixture.doubles.TestAccounts;

/** Login of a person the snapshot does not list: nothing is registered (ADR-0069 s9). */
class UnknownWecomLoginIntegrationTest extends WorkforceIntegrationSupport {
    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper json;

    @Autowired
    AccountEmployees accountEmployees;

    /** csrf() swaps the token repository of the shared filter chain for good (other classes in this context use it too), so a hand-made cookie token is no longer read once one of them ran first. */
    private static MockHttpServletRequestBuilder withXsrf(MockHttpServletRequestBuilder request) {
        return request.with(csrf());
    }

    private void aNewHireTheSnapshotDoesNotListYet() {
        orgSource.snapshot = source(List.of(unit(1, null), unit(2, 1L)), List.of(head(2, 1), manager(2, 2)), List.of(post(100)),
                List.of(person(1, 2, 100L, null)));
        SnapshotSeeder.seed(jdbc, orgSource.snapshot);
        orgSource.singleLookupResult = List.of(person(3001, 2, 100L, 1L, "13900003001", "wx-new-hire"));
    }

    @Test
    @DisplayName("ADR-0069 s9 a WeCom login of a person the snapshot does not list creates no employee, no account and no event, and is refused")
    void loginOfAPersonTheSnapshotDoesNotListCreatesNothing() throws Exception {
        aNewHireTheSnapshotDoesNotListYet();
        when(wecomGateway.resolveWecomUserId("code-new-hire", WecomEntry.WORKBENCH)).thenReturn(Optional.of(new WecomUserId("wx-new-hire")));
        int employees = count("employees");

        MvcResult issued = mvc.perform(withXsrf(post("/api/v1/platform/login-states")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"entry\":\"WORKBENCH\"}")).andReturn();
        assertThat(issued.getResponse().getStatus()).as(issued.getResponse().getContentAsString()).isEqualTo(200);
        String state = json.readTree(issued.getResponse().getContentAsString()).path("state").asString();
        Cookie binding = issued.getResponse().getCookie("loginBinding");

        MvcResult login = mvc.perform(withXsrf(post("/api/v1/platform/sessions:wecom-login")).cookie(binding).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"code-new-hire\",\"state\":\"" + state + "\",\"entry\":\"WORKBENCH\"}")).andReturn();

        assertThat(login.getResponse().getStatus()).as(login.getResponse().getContentAsString()).isGreaterThanOrEqualTo(400);
        assertThat(count("employees")).isEqualTo(employees);
        assertThat(jdbc.sql("select count(*) from organization_workforce.employees where id = " + sqlEmployee(3001)).query(Integer.class).single()).isZero();
        assertThat(accountEmployees.accountOf(TestAccounts.employee(3001))).isEmpty();
        assertThat(orgSource.singleLookups.get()).as("the source is not asked").isZero();
    }
}
