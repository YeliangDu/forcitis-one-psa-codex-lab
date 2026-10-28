package com.forcitis.one.scenario;

import static com.forcitis.one.organization.workforce.domain.Fixtures.head;
import static com.forcitis.one.organization.workforce.domain.Fixtures.person;
import static com.forcitis.one.organization.workforce.domain.Fixtures.post;
import static com.forcitis.one.organization.workforce.domain.Fixtures.source;
import static com.forcitis.one.organization.workforce.domain.Fixtures.unit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.fixture.doubles.TestOrgIds;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.application.SyncOrgSnapshot;
import com.forcitis.one.organization.workforce.application.VersionTables;
import com.forcitis.one.organization.workforce.application.SyncResult;
import com.forcitis.one.organization.workforce.domain.SyncRunStatus;
import com.forcitis.one.organization.workforce.domain.SyncTrigger;
import com.forcitis.one.platform.identity.IdentityIntegrationTest;
import com.forcitis.one.platform.identity.domain.EmergencyAdmin;
import com.forcitis.one.platform.identity.domain.EmergencyAdminRepository;
import com.forcitis.one.platform.identity.domain.PasswordHasher;
import com.forcitis.one.platform.identity.domain.Totp;
import com.forcitis.one.platform.identity.domain.TotpSecretVault;
import com.forcitis.one.platform.integration.api.OrgSource;
import com.forcitis.one.platform.integration.api.SourcePerson;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;

@ExtendWith(OutputCaptureExtension.class)
@DisplayName("SyncLockoutGuard: ADR-0027 section 1 item 7, ADR-0028 (a wrong source cannot take every administrator away; only the right person lifts each kind of hold)")
class SyncLockoutGuardIntegrationTest extends IdentityIntegrationTest {

    static final String RUNS = "/api/v1/organization/sync-runs";

    @MockitoBean
    OrgSource orgSource;

    @Autowired
    SyncOrgSnapshot sync;

    private List<SourcePerson> everyone;

    @BeforeEach
    void tenPeopleTwoOfThemSuperAdministrators() {
        awaitListeners();
        VersionTables.truncate(jdbc, """
                truncate table organization_workforce.sync_diffs, organization_workforce.sync_runs,
                       organization_workforce.legal_entity_attributions,
                       organization_workforce.org_unit_versions, organization_workforce.assignment_versions,
                       organization_workforce.assignments, organization_workforce.employees,
                       organization_workforce.org_units, organization_workforce.positions restart identity cascade
                """);
        everyone = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            everyone.add(person(i, 2, 100L, null));
        }
        syncWith(everyone);
        awaitListeners();
        jdbc.sql("delete from platform_identity.account_roles").update();
        jdbc.sql("delete from platform_identity.user_accounts").update();
        for (long id : new long[] {1, 2, 3, 7, 9}) {
            knownEmployee(id, EmploymentStatus.EMPLOYED);
        }
        createAccount(1, "super_admin", "employee");
        createAccount(2, "super_admin", "employee");
        createAccount(3, "security_auditor", "employee");
        createAccount(7, "org_data_admin", "employee");
        createAccount(9, "employee");
    }

    @AfterEach
    void settle() {
        awaitListeners();
    }

    private void awaitListeners() {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (jdbc.sql("select count(*) from platform_integration.event_publication").query(Integer.class).single() > 0) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("event listeners still running after 20 s");
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError(interrupted);
            }
        }
    }

    private SyncResult syncWith(List<SourcePerson> people) {
        when(orgSource.readSnapshot()).thenReturn(source(List.of(unit(1, null), unit(2, 1L)), List.of(head(2, 1)), List.of(post(100)), people));
        try (WorkContext.Scope acting = WorkContext.enter(UUID.randomUUID(), null, "test.organization.workforce")) {
            return sync.execute(SyncTrigger.SCHEDULED, false);
        }
    }

    private List<SourcePerson> without(long... ids) {
        return everyone.stream().filter(p -> Arrays.stream(ids).noneMatch(id -> p.personId().equals(TestOrgIds.ownEmployee(id)))).toList();
    }

    private void sourceNowSays(long... leaving) {
        org.mockito.Mockito.doReturn(source(List.of(unit(1, null), unit(2, 1L)), List.of(head(2, 1)), List.of(post(100)), without(leaving))).when(orgSource).readSnapshot();
    }

    private String employment(long userId) {
        return jdbc.sql("select employment_status from organization_workforce.employees where id = :id").param("id", TestAccounts.employee(userId).value()).query(String.class).single();
    }

    private String status(UUID runId) {
        return jdbc.sql("select status from organization_workforce.sync_runs where id = :id").param("id", runId).query(String.class).single();
    }

    private MvcResult release(String session, UUID runId, String reason) throws Exception {
        return mvc.perform(post(RUNS + "/" + runId + ":release").with(xsrf()).cookie(new Cookie("SESSION", session))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"" + reason + "\"}")).andReturn();
    }

    private MvcResult getAs(String session, String path) throws Exception {
        return mvc.perform(get(RUNS + path).cookie(new Cookie("SESSION", session))).andReturn();
    }

    private String ordinarySession(long id) throws Exception {
        return loginWithWecom(id);
    }

    @Test
    @DisplayName("people who hold none of the two roles leave without any confirmation")
    void ordinaryLeaversGoThrough() {
        SyncResult result = syncWith(without(7));

        assertThat(result.status()).isEqualTo(SyncRunStatus.SUCCEEDED);
        assertThat(employment(7)).isEqualTo("SEPARATED");
    }

    @Test
    @DisplayName("a run that would separate a super_admin holder stays HELD with the kind and the list, separates nobody and says why")
    void aSuperAdministratorLeavingHoldsTheRun() {
        SyncResult result = syncWith(without(1, 7));

        assertThat(result.status()).isEqualTo(SyncRunStatus.HELD);
        assertThat(result.note()).contains("hold super_admin or security_auditor").contains("only 1 usable super_admin holder(s) would remain");
        assertThat(employment(1)).isEqualTo("EMPLOYED");
        assertThat(employment(7)).as("not even the ordinary leaver of the same run").isEqualTo("EMPLOYED");
        assertThat(jdbc.sql("select hold_kind_codes::text from organization_workforce.sync_runs where status = 'HELD'").query(String.class).single())
                .isEqualTo("{ADMINISTRATOR_SEPARATION}");
        assertThat(jdbc.sql("select employee_id from organization_workforce.sync_run_held_separations").query(UUID.class).list())
                .containsExactlyInAnyOrder(TestAccounts.employee(1).value(), TestAccounts.employee(7).value());
    }

    @Test
    @DisplayName("the next scheduled runs keep waiting while the source still says so, and add no row")
    void theHoldIsRefreshedNotRepeated() {
        syncWith(without(1));

        SyncResult again = syncWith(without(1));

        assertThat(again.status()).isEqualTo(SyncRunStatus.HELD);
        assertThat(jdbc.sql("select count(*) from organization_workforce.sync_runs").query(Long.class).single()).as("the first run and the one hold").isEqualTo(2);
        assertThat(employment(1)).isEqualTo("EMPLOYED");
    }

    @Test
    @DisplayName("a security auditor's departure waits too")
    void anAuditorLeavingHoldsTheRun() {
        assertThat(syncWith(without(3)).status()).isEqualTo(SyncRunStatus.HELD);
    }

    @Test
    @DisplayName("ADR-0028 rule 1: an organisation data administrator, who may run syncs, cannot lift an administrator-separation hold")
    void theOrgDataAdministratorCannotLiftTheSeparationHold() throws Exception {
        UUID held = syncWith(without(1)).syncRunId().value();

        MvcResult refused = release(ordinarySession(7), held, "please");

        assertThat(refused.getResponse().getStatus()).isEqualTo(403);
        assertThat(body(refused).path("errorCode").asString()).isEqualTo("ORGANIZATION_WORKFORCE_SEPARATION_HOLD_FORBIDDEN");
        assertThat(status(held)).isEqualTo("HELD");
        assertThat(employment(1)).isEqualTo("EMPLOYED");
    }

    @Test
    @DisplayName("ADR-0028 rule 1: a retained super_admin lifts it in the administrator mode; the release is audited with its reason and raises an alert")
    void aRetainedSuperAdministratorReleasesInTheAdministratorMode(CapturedOutput output) throws Exception {
        UUID held = syncWith(without(1)).syncRunId().value();

        MvcResult released = release(administratorSession(2), held, "leaver confirmed by HR");

        assertThat(released.getResponse().getStatus()).as(released.getResponse().getContentAsString()).isEqualTo(200);
        assertThat(body(released).path("outcome").asString()).isEqualTo("RELEASED");
        assertThat(status(held)).isEqualTo("RELEASED");
        assertThat(employment(1)).isEqualTo("SEPARATED");
        assertThat(recorded()).filteredOn(entry -> entry.action().equals("organization.workforce.sync-run.released")).singleElement().satisfies(entry -> {
            assertThat(entry.actorId()).isEqualTo(TestAccounts.account(2));
            assertThat(entry.detail()).contains("leaver confirmed by HR").contains("session mode: ADMINISTRATOR").contains("held: " + TestAccounts.employee(1).value())
                    .contains("applied: " + TestAccounts.employee(1).value()).contains("remaining super_admin: 1");
        });
        assertThat(output.getAll()).contains("ALERT organization.workforce sync run " + held + " released by user " + TestAccounts.account(2).value());
    }

    @Test
    @DisplayName("ADR-0028 rule 1: a retained security auditor lifts it in an ordinary session")
    void aRetainedAuditorReleasesInAnOrdinarySession() throws Exception {
        UUID held = syncWith(without(1)).syncRunId().value();

        MvcResult released = release(ordinarySession(3), held, "leaver confirmed by HR");

        assertThat(released.getResponse().getStatus()).as(released.getResponse().getContentAsString()).isEqualTo(200);
        assertThat(employment(1)).isEqualTo("SEPARATED");
        assertThat(recorded()).filteredOn(entry -> entry.action().equals("organization.workforce.sync-run.released")).singleElement()
                .satisfies(entry -> assertThat(entry.detail()).contains("session mode: ORDINARY"));
    }

    @Test
    @DisplayName("ADR-0028 rule 1: someone on the separation list cannot lift it, whichever role they hold")
    void thoseLeavingCannotRelease() throws Exception {
        UUID held = syncWith(without(1, 3)).syncRunId().value();

        MvcResult leavingSuperAdministrator = release(administratorSession(1), held, "me");
        MvcResult leavingAuditor = release(ordinarySession(3), held, "me");

        assertThat(leavingSuperAdministrator.getResponse().getStatus()).isEqualTo(403);
        assertThat(leavingAuditor.getResponse().getStatus()).isEqualTo(403);
        assertThat(status(held)).isEqualTo("HELD");
    }

    @Test
    @DisplayName("ADR-0028 rule 1: with both kinds of hold, each is lifted by its own entitled person and the run stays HELD until both are")
    void bothKindsAreLiftedSeparately() throws Exception {
        UUID held = syncWith(without(1, 8, 9)).syncRunId().value();
        assertThat(jdbc.sql("select hold_kind_codes::text from organization_workforce.sync_runs where id = :id").param("id", held).query(String.class).single())
                .contains("SHRINK_GUARD").contains("ADMINISTRATOR_SEPARATION");

        MvcResult shrinkOnly = release(ordinarySession(7), held, "headcount change is real");

        assertThat(body(shrinkOnly).path("outcome").asString()).isEqualTo("PARTLY_RELEASED");
        assertThat(status(held)).isEqualTo("HELD");
        assertThat(employment(1)).isEqualTo("EMPLOYED");
        assertThat(body(getAs(administratorSession(2), "?status=HELD")).path("items").isArray()).isTrue();
        assertThat(release(ordinarySession(7), held, "again").getResponse().getStatus()).as("nothing left for the executor to lift").isEqualTo(403);

        MvcResult separationToo = release(administratorSession(2), held, "leaver confirmed by HR");

        assertThat(separationToo.getResponse().getStatus()).as(separationToo.getResponse().getContentAsString()).isEqualTo(200);
        assertThat(status(held)).isEqualTo("RELEASED");
        assertThat(employment(1)).isEqualTo("SEPARATED");
    }

    @Test
    @DisplayName("ADR-0028 rule 2: when no usable super_admin would remain the screen refuses; only the operations command releases")
    void noSuperAdministratorLeftIsRefused() throws Exception {
        UUID held = syncWith(without(1, 2)).syncRunId().value();

        MvcResult refused = release(ordinarySession(3), held, "all gone");

        assertThat(refused.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(refused).path("errorCode").asString()).isEqualTo("ORGANIZATION_WORKFORCE_SEPARATION_HOLD_REFUSED");
        assertThat(status(held)).isEqualTo("HELD");
        assertThat(employment(1)).isEqualTo("EMPLOYED");
    }

    @Test
    @DisplayName("ADR-0028 rule 2: a new super_admin among the leavers when the run is re-run puts it back on hold")
    void aNewAdministratorInTheListHoldsAgain() throws Exception {
        UUID held = syncWith(without(1)).syncRunId().value();
        sourceNowSays(1, 2);

        MvcResult again = release(ordinarySession(3), held, "leaver confirmed by HR");

        assertThat(again.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(again).path("errorCode").asString()).isEqualTo("ORGANIZATION_WORKFORCE_SYNC_RUN_APPLY_FAILED");
        assertThat(status(held)).isEqualTo("HELD");
        assertThat(employment(1)).isEqualTo("EMPLOYED");
        assertThat(employment(2)).isEqualTo("EMPLOYED");
        assertThat(jdbc.sql("select released_kind_codes::text from organization_workforce.sync_runs where id = :id").param("id", held).query(String.class).single())
                .isEqualTo("{}");
        assertThat(jdbc.sql("select employee_id from organization_workforce.sync_run_held_separations where sync_run_id = :id").param("id", held).query(UUID.class).list())
                .containsExactlyInAnyOrder(TestAccounts.employee(1).value(), TestAccounts.employee(2).value());
    }

    @Test
    @DisplayName("ADR-0028 rule 3: an emergency session cannot release while the person is not in the organisation snapshot")
    void anEmergencySessionNeedsTheSnapshot() throws Exception {
        UUID held = syncWith(without(1)).syncRunId().value();

        MvcResult refused = release(emergencySession(8), held, "break glass");

        assertThat(refused.getResponse().getStatus()).isEqualTo(403);
        assertThat(status(held)).isEqualTo("HELD");
    }

    @Test
    @DisplayName("ADR-0028 rule 3: the held view needs sync-run:read or separation-hold:approve; holders of the second see no other run and no detail")
    void theHeldView() throws Exception {
        UUID held = syncWith(without(1)).syncRunId().value();

        assertThat(getAs(ordinarySession(9), "?status=HELD").getResponse().getStatus()).isEqualTo(403);
        MvcResult view = getAs(administratorSession(2), "?status=HELD");
        assertThat(view.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(view).path("items").get(0).path("heldSeparations").get(0).asString()).isEqualTo(TestAccounts.employee(1).value().toString());
        assertThat(body(view).path("items").get(0).path("holdKinds").get(0).asString()).isEqualTo("ADMINISTRATOR_SEPARATION");
        assertThat(getAs(administratorSession(10), "/" + held).getResponse().getStatus()).as("the detail carries the diffs").isEqualTo(403);
        assertThat(getAs(administratorSession(10), "").getResponse().getStatus()).as("the whole list is not theirs").isEqualTo(403);
    }

    @Test
    @DisplayName("a release without a reason is refused")
    void aReasonIsRequired() throws Exception {
        UUID held = syncWith(without(1)).syncRunId().value();

        MvcResult refused = release(administratorSession(2), held, " ");

        assertThat(refused.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(refused).path("errorCode").asString()).isEqualTo("ORGANIZATION_WORKFORCE_RELEASE_REASON_REQUIRED");
        assertThat(status(held)).isEqualTo("HELD");
    }

    @Test
    @DisplayName("the security auditor role is declared with the permission to lift a separation hold")
    void theAuditorRoleCarriesThePermission() {
        assertThat(jdbc.sql("""
                select r.code from platform_identity.role_versions v join platform_identity.roles r on r.role_id = v.role_id
                 where v.effective_to_at is null and 'organization.workforce:separation-hold:approve' = any (v.permission_codes)
                """).query(String.class).list()).containsExactly("security_auditor");
    }

    @Test
    @DisplayName("INT-ADR-H2 aFailedRerunTakesTheLiftsBack: when the source cannot be read at release time the held run keeps nothing half lifted, and the same person can release it again")
    void aFailedRerunTakesTheLiftsBack() throws Exception {
        UUID held = syncWith(without(1)).syncRunId().value();
        String administrator = administratorSession(2);

        when(orgSource.readSnapshot()).thenThrow(new IllegalStateException("source is down"));
        MvcResult failed = release(administrator, held, "leaver confirmed by HR");

        assertThat(failed.getResponse().getStatus()).isEqualTo(409);
        assertThat(status(held)).isEqualTo("HELD");
        assertThat(jdbc.sql("select released_kind_codes::text from organization_workforce.sync_runs where id = :id").param("id", held).query(String.class).single())
                .isEqualTo("{}");

        sourceNowSays(1);
        MvcResult again = release(administrator, held, "leaver confirmed by HR");

        assertThat(again.getResponse().getStatus()).as(again.getResponse().getContentAsString()).isEqualTo(200);
        assertThat(body(again).path("outcome").asString()).isEqualTo("RELEASED");
        assertThat(employment(1)).isEqualTo("SEPARATED");
    }
}
