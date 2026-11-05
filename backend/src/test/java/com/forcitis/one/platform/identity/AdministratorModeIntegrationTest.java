package com.forcitis.one.platform.identity;

import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.fixture.doubles.TestOrgIds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.identity.api.Permission;
import com.forcitis.one.platform.integration.api.WecomEntry;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

@DisplayName("AdminModeBoundary: ADR-0026 5, ADR-0027 2.5 the administrator mode holds the platform permissions only")
class AdministratorModeIntegrationTest extends IdentityIntegrationTest {

    static final String EXIT = "/api/v1/platform/sessions:exit-administrator-mode";
    static final List<String> PLATFORM_PERMISSIONS = List.of(Permission.Codes.PLATFORM_IDENTITY_ROLE_READ,
            Permission.Codes.PLATFORM_IDENTITY_ROLE_UPDATE, Permission.Codes.PLATFORM_IDENTITY_ACCOUNT_ROLE_UPDATE,
            Permission.Codes.PLATFORM_IDENTITY_SESSION_DELETE, Permission.Codes.ORGANIZATION_WORKFORCE_SEPARATION_HOLD_APPROVE);

    @Autowired
    MeterRegistry meters;

    private JsonNode me(String session) throws Exception {
        return body(mvc.perform(get(SessionLifecycleIntegrationTest.CURRENT).cookie(new Cookie("SESSION", session))).andReturn());
    }

    private List<String> permissions(String session) throws Exception {
        return me(session).path("permissionCodes").valueStream().map(n -> n.asString()).toList();
    }

    private int status(String url, String session) throws Exception {
        return mvc.perform(get(url).cookie(new Cookie("SESSION", session))).andReturn().getResponse().getStatus();
    }

    @Test
    @DisplayName("an ordinary session of a super_admin holder has no platform permission: the role only allows entering the mode")
    void ordinarySessionHasNoPlatformPermission() throws Exception {
        createAccount(9, "super_admin", "employee");
        String session = loginWithWecom(9);

        assertThat(me(session).path("mode").asString()).isEqualTo("ORDINARY");
        assertThat(me(session).path("administratorModeExpiresAt").isNull() || me(session).path("administratorModeExpiresAt").isMissingNode()).isTrue();
        assertThat(permissions(session)).doesNotContainAnyElementsOf(PLATFORM_PERMISSIONS)
                .contains("platform.approval:task:read", "platform.approval:task:approve");
        assertThat(status("/api/v1/platform/roles", session)).isEqualTo(403);
    }

    @Test
    @DisplayName("entering needs a fresh proof of the same person; the session then holds exactly the platform permissions for one hour")
    void enteringGivesExactlyThePlatformPermissions() throws Exception {
        createAccount(9, "super_admin", "employee");
        String session = loginWithWecom(9);

        double entriesBefore = meters.counter("forcitis.platform.identity.administrator.mode.entries").count();
        Instant before = clock.instant();
        MvcResult entered = enterAdministratorMode(session, 9);
        Instant after = clock.instant();

        assertThat(entered.getResponse().getStatus()).isEqualTo(200);
        assertThat(Instant.parse(body(entered).path("administratorModeExpiresAt").asString())).isBetween(before.plus(Duration.ofHours(1)),
                after.plus(Duration.ofHours(1)));
        String elevated = cookie(entered, "SESSION");
        assertThat(elevated).as("the session id changes with the privilege").isNotBlank().isNotEqualTo(session);
        assertThat(me(elevated).path("mode").asString()).isEqualTo("ADMINISTRATOR");
        assertThat(Instant.parse(me(elevated).path("administratorModeExpiresAt").asString())).isBetween(before.plus(Duration.ofHours(1)),
                after.plus(Duration.ofHours(1)));
        assertThat(permissions(elevated)).containsExactlyInAnyOrderElementsOf(PLATFORM_PERMISSIONS);
        assertThat(status("/api/v1/platform/roles", elevated)).isEqualTo(200);
        assertThat(status("/api/v1/platform/audit-entries", elevated)).as("no audit viewing in the administrator mode").isEqualTo(403);
        assertThat(recorded()).extracting(AuditEntry::action).contains("platform.identity.administrator-mode.entered");
        assertThat(meters.counter("forcitis.platform.identity.administrator.mode.entries").count()).isEqualTo(entriesBefore + 1.0);
    }

    @Test
    @DisplayName("the mode is not extended: a second entry while it is active is refused, and after the hour the session is ordinary again")
    void notExtendedAndEndsAfterAnHour() throws Exception {
        createAccount(9, "super_admin", "employee");
        String elevated = cookie(enterAdministratorMode(loginWithWecom(9), 9), "SESSION");

        assertThat(enterAdministratorMode(elevated, 9).getResponse().getStatus()).isEqualTo(403);

        clock.advance(Duration.ofMinutes(59));
        assertThat(me(elevated).path("mode").asString()).isEqualTo("ADMINISTRATOR");
        clock.advance(Duration.ofMinutes(2));
        assertThat(me(elevated).path("mode").asString()).isEqualTo("ORDINARY");
        assertThat(status("/api/v1/platform/roles", elevated)).isEqualTo(403);
        assertThat(permissions(elevated)).contains("platform.approval:task:read");
    }

    @Test
    @DisplayName("leaving the mode returns to the ordinary session and is audited")
    void leaving() throws Exception {
        createAccount(9, "super_admin", "employee");
        String elevated = cookie(enterAdministratorMode(loginWithWecom(9), 9), "SESSION");

        MvcResult left = mvc.perform(post(EXIT).with(xsrf()).cookie(new Cookie("SESSION", elevated))).andReturn();

        assertThat(left.getResponse().getStatus()).isEqualTo(204);
        assertThat(me(cookie(left, "SESSION")).path("mode").asString()).isEqualTo("ORDINARY");
        assertThat(recorded()).extracting(AuditEntry::action).contains("platform.identity.administrator-mode.exited");
    }

    @Test
    @DisplayName("nobody else gets in: a person without super_admin, or a code that proves another person, is refused and audited")
    void refusals() throws Exception {
        createAccount(9, "super_admin", "employee");
        createAccount(1001, "employee");
        String ordinary = loginWithWecom(1001);

        assertThat(enterAdministratorMode(ordinary, 1001).getResponse().getStatus()).isEqualTo(403);
        String holder = loginWithWecom(9);
        assertThat(enterAdministratorMode(holder, 1001).getResponse().getStatus()).as("the code proves another person").isEqualTo(403);
        assertThat(me(holder).path("mode").asString()).isEqualTo("ORDINARY");
        assertThat(recordedIndependently()).extracting(AuditEntry::action).contains("platform.identity.administrator-mode.refused");
        assertThat(mvc.perform(post(ENTER_ADMINISTRATOR_MODE).with(xsrf()).contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn().getResponse().getStatus())
                .as("not signed in").isEqualTo(401);
    }

    @Test
    @DisplayName("ADR-0027 1.4 taking super_admin away ends the mode within 60 s even without an event")
    void takingSuperAdminAwayEndsTheSession() throws Exception {
        createAccount(9, "super_admin", "employee");
        String elevated = cookie(enterAdministratorMode(loginWithWecom(9), 9), "SESSION");
        assertThat(status("/api/v1/platform/roles", elevated)).isEqualTo(200);
        jdbc.sql("update platform_identity.account_role_grants set effective_to_at = greatest(now(), effective_from_at), revocation_cause_kind = 'OPERATIONS', revocation_audit_entry_id = uuidv7(), updated_at = now() where effective_to_at is null and user_id = (select user_id from platform_identity.user_accounts where employee_id = " + TestOrgIds.sqlEmployee(9) + ") and role_id = (select role_id from platform_identity.roles where code = 'super_admin')").update();

        clock.advance(Duration.ofSeconds(61));

        assertThat(status("/api/v1/platform/roles", elevated)).isEqualTo(401);
    }

    @Test
    @DisplayName("B1-M2 a refused entry to the administrator mode still uses up the OAuth state, so it cannot be tried again")
    void aRefusedEntryConsumesTheState() throws Exception {
        createAccount(1100, "employee");
        String session = loginWithWecom(1100);

        MvcResult refused = enterAdministratorMode(session, 1100);

        assertThat(refused.getResponse().getStatus()).isEqualTo(403);
        assertThat(count("login_states")).isZero();
    }

    @Test
    @DisplayName("B1-M1 when the hour ends by itself the exit is audited once and alerts, like a manual exit")
    void naturalExpiryIsAudited() throws Exception {
        createAccount(9, "super_admin", "employee");
        String elevated = cookie(enterAdministratorMode(loginWithWecom(9), 9), "SESSION");

        clock.advance(Duration.ofMinutes(61));
        me(elevated);
        me(elevated);

        assertThat(recordedIndependently()).filteredOn(entry -> entry.action().equals("platform.identity.administrator-mode.exited"))
                .singleElement().satisfies(entry -> assertThat(entry.detail()).startsWith("expired at "));
    }

    @Test
    @DisplayName("B1-M1 leaving the mode by hand changes the session id")
    void leavingRotatesTheSessionId() throws Exception {
        createAccount(9, "super_admin", "employee");
        String elevated = cookie(enterAdministratorMode(loginWithWecom(9), 9), "SESSION");

        MvcResult left = mvc.perform(post(EXIT).with(xsrf()).cookie(new Cookie("SESSION", elevated))).andReturn();

        assertThat(cookie(left, "SESSION")).isNotBlank().isNotEqualTo(elevated);
    }

    @Test
    @DisplayName("the system management navigation is visible in the administrator mode, with the role page and the authorization requests page, in sort order; in an ordinary session it is not")
    void systemManagementIsVisibleInTheAdministratorMode() throws Exception {
        createAccount(9, "super_admin", "employee");
        String ordinary = loginWithWecom(9);
        String elevated = cookie(enterAdministratorMode(ordinary, 9), "SESSION");

        var entries = me(elevated).path("navigation");
        var system = entries.valueStream().filter(n -> n.path("name").asString().equals("\u7cfb\u7edf\u7ba1\u7406")).findFirst();

        assertThat(system).isPresent();
        assertThat(system.get().path("children").valueStream().map(n -> n.path("routePath").asString()).toList()).containsExactly("/admin/roles", "/admin/authorization-requests");
        assertThat(me(loginWithWecom(9)).path("navigation").valueStream().map(n -> n.path("name").asString())).doesNotContain("\u7cfb\u7edf\u7ba1\u7406");
    }

    @Test
    @DisplayName("INT-SEC-H1 administratorModeCannotHandleApprovals: neither the administrator mode nor an emergency session sees or handles approval tasks (I-11)")
    void administratorModeCannotHandleApprovals() throws Exception {
        String administrator = administratorSession(9);
        String emergency = emergencySession(8);
        String ordinary = loginWithWecom(1600);
        String base = "/api/v1/platform/approval-tasks";

        assertThat(status(base, ordinary)).as("an ordinary session still has its inbox").isEqualTo(200);
        for (String session : List.of(administrator, emergency)) {
            assertThat(status(base, session)).isEqualTo(403);
            assertThat(status(base + "/" + com.forcitis.one.TestIds.uuid(1), session)).isEqualTo(403);
            assertThat(status("/api/v1/platform/approval-task-records", session)).isEqualTo(403);
            assertThat(status("/api/v1/platform/approval-task-overviews", session)).isEqualTo(403);
            String body = "{\"outcome\":\"APPROVE\",\"reason\":\"x\",\"assigneeId\":\"" + TestAccounts.employee(1).value() + "\"}";
            for (String action : List.of(":claim", ":release", ":decide", ":return", ":transfer", ":reassign")) {
                assertThat(mvc.perform(post(base + "/" + com.forcitis.one.TestIds.uuid(1) + action).with(xsrf()).cookie(new Cookie("SESSION", session))
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus()).as(action).isEqualTo(403);
            }
        }
    }
}
