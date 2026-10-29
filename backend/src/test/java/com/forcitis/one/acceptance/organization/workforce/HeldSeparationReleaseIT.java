package com.forcitis.one.acceptance.organization.workforce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.AccountsAcceptanceSupport;
import com.jayway.jsonpath.JsonPath;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * R-1b P2 (ADR-0033 section 3 item 3 「本人」判定清单: HELD 放行人须留任、不在离职名单; ADR-0027 section 1 item 7; ADR-0028): a sync run
 * held because it would separate an administrator is lifted only by a retained holder. The releaser is the signed-in account; it is set
 * against the separating people as the employee behind it. The account id is a UUID and the employee id a number, so a check made on the
 * account's side would let the leaver lift their own hold.
 */
class HeldSeparationReleaseIT extends AccountsAcceptanceSupport {

    static final String SYNC_RUNS = "/api/v1/organization/sync-runs/";

    static final TestDatabase postgres = TestPostgres.database(HeldSeparationReleaseIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    /** A run held for an administrator's separation and for the shrink guard, so lifting the first leaves the run held. */
    private UUID heldRun(UUID separating) throws Exception {
        UUID run = UUID.randomUUID();
        try (Connection workforce = schemaConnection("organization_workforce")) {
            try (PreparedStatement insert = workforce.prepareStatement("""
                    insert into sync_runs (id, trigger_kind, status, started_at, finished_at, held_reason, hold_kind_codes)
                    values (?, 'SCHEDULED', 'HELD', ?, ?, 'synthetic: an administrator would be separated', ?)""")) {
                Array kinds = workforce.createArrayOf("text", new String[] {"ADMINISTRATOR_SEPARATION", "SHRINK_GUARD"});
                insert.setObject(1, run);
                insert.setTimestamp(2, Timestamp.from(NOW.minusSeconds(120)));
                insert.setTimestamp(3, Timestamp.from(NOW.minusSeconds(60)));
                insert.setArray(4, kinds);
                insert.executeUpdate();
            }
            try (PreparedStatement insert = workforce.prepareStatement("insert into sync_run_held_separations (sync_run_held_separation_id, sync_run_id, employee_id) values (?, ?, ?)")) {
                insert.setObject(1, UUID.randomUUID());
                insert.setObject(2, run);
                insert.setObject(3, stored(separating));
                insert.executeUpdate();
            }
        }
        return run;
    }

    private List<String> releasedKinds(UUID run) throws Exception {
        try (Connection workforce = schemaConnection("organization_workforce");
             PreparedStatement select = workforce.prepareStatement("select released_kind_codes from sync_runs where id = ?")) {
            select.setObject(1, run);
            try (ResultSet rs = select.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return List.of((String[]) rs.getArray(1).getArray());
            }
        }
    }

    /** The releaser acts in their own session; the permission to lift the hold comes from the security_auditor role (ADR-0028). */
    private MockHttpServletResponse release(UUID run, SignedIn releaser) throws Exception {
        return mvc.perform(post(SYNC_RUNS + run + ":release").with(csrf()).with(session(releaser))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"synthetic: checked with the HR department\"}")).andReturn().getResponse();
    }

    @Test
    @DisplayName("R-1b P2 ADR-0033 第 3 项第 3 条：HELD 放行人在离职名单中仍被拒（操作人即本人，按员工 ID 比较）；另一名留任的超级管理员可以放行这一类暂停，放行人记为其用户 ID")
    void aLeaverCannotLiftTheHoldOnTheirOwnSeparation() throws Exception {
        UUID unit = newOrgUnitId();
        orgUnit(unit, null, null);
        UUID leaver = newEmployeeId();
        UUID retained = newEmployeeId();
        UUID third = newEmployeeId();
        for (UUID person : List.of(leaver, retained, third)) {
            employee(person, unit, null);
            grantByOperations(person, "super_admin");
            grantByOperations(person, "security_auditor");
        }
        SignedIn leaverSession = signIn(leaver);
        SignedIn retainedSession = signIn(retained);
        signIn(third);
        UUID leaverAccount = leaverSession.userId();
        UUID retainedAccount = retainedSession.userId();
        assertThat(employeeOfAccount(leaverAccount)).isEqualTo(leaver);
        UUID run = heldRun(leaver);

        MockHttpServletResponse byTheLeaver = release(run, leaverSession);

        assertThat(byTheLeaver.getStatus()).as(byTheLeaver.getContentAsString()).isEqualTo(403);
        assertThat((String) JsonPath.read(byTheLeaver.getContentAsString(), "$.errorCode")).isEqualTo("ORGANIZATION_WORKFORCE_SEPARATION_HOLD_FORBIDDEN");
        assertThat(releasedKinds(run)).isEmpty();

        MockHttpServletResponse byAnother = release(run, retainedSession);

        assertThat(byAnother.getStatus()).as(byAnother.getContentAsString()).isEqualTo(200);
        assertThat((String) JsonPath.read(byAnother.getContentAsString(), "$.outcome")).isEqualTo("PARTLY_RELEASED");
        assertThat(releasedKinds(run)).containsExactly("ADMINISTRATOR_SEPARATION");
        List<Map<String, Object>> released = auditEntriesBy(retainedAccount, "organization.workforce.SyncRunHoldReleased");
        assertThat(released).as("the hold release is audited with the releasing account as the actor").isNotEmpty()
                .allSatisfy(entry -> assertThat(entry.get("actorId")).isEqualTo(retainedAccount.toString()));
    }
}
