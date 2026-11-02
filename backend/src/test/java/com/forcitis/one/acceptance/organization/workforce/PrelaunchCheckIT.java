package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.*;
import static com.forcitis.one.acceptance.organization.workforce.SeparationHoldOperationNames.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.Uuids;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #961 (user 2026-10-30 「先补前提再停同步」; ADR-0055 §2, §5; ADR-0068 §1, §2.5): before go-live, a pre-launch check refuses while
 * sync_run_held_separations still has rows whose run is HELD, with a message that names the runs and the count; otherwise it passes.
 * It is a workforce operations command run as operations run it (nobody signed in). The operation name {@value #PRELAUNCH_CHECK} is a
 * 暂名 agreed here (the card says only "pre-release check"); exit codes as the other workforce commands: 0 passes, 1 refuses.
 */
class PrelaunchCheckIT extends InitialVersionMigrationSupport {

    /** 暂名: the operation name of the check. */
    static final String PRELAUNCH_CHECK = "prelaunch-check";

    static final TestDatabase postgres = TestPostgres.database(PrelaunchCheckIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private Result check() {
        return run("--" + OPERATION_OPTION + "=" + PRELAUNCH_CHECK);
    }

    /** A sync run in the given status, as the stopped sync leaves it; answers its id. */
    private UUID syncRunIn(String status) throws SQLException {
        UUID id = Uuids.timeOrdered(NOW);
        OffsetDateTime at = OffsetDateTime.ofInstant(NOW.minusSeconds(3600), ZoneOffset.UTC);
        try (Connection workforce = schemaConnection("organization_workforce")) {
            update(workforce, """
                    insert into sync_runs (id, trigger_kind, status, started_at, finished_at, held_reason, hold_kind_codes)
                    values (?, 'SCHEDULED', ?, ?, ?, ?, ?::text[])""", id, status, at, at, "HELD".equals(status) || "RELEASED".equals(status) ? "synthetic hold" : null,
                    "HELD".equals(status) || "RELEASED".equals(status) ? "{ADMINISTRATOR_SEPARATION}" : "{}");
        }
        return id;
    }

    private void heldSeparation(UUID run, UUID employee) throws SQLException {
        try (Connection workforce = schemaConnection("organization_workforce")) {
            update(workforce, "insert into sync_run_held_separations (sync_run_held_separation_id, sync_run_id, employee_id) values (?, ?, ?)",
                    Uuids.timeOrdered(NOW), run, stored(employee));
        }
    }

    private List<Long> counts() throws Exception {
        try (Connection workforce = schemaConnection("organization_workforce")) {
            return List.of(((Number) query(workforce, "select count(*) as n from sync_runs").getFirst().get("n")).longValue(),
                    ((Number) query(workforce, "select count(*) as n from sync_run_held_separations").getFirst().get("n")).longValue(),
                    ((Number) query(workforce, "select count(*) as n from employees").getFirst().get("n")).longValue(),
                    (long) auditCount("organization.workforce"));
        }
    }

    @Test
    @DisplayName("#961 上线前检查：没有任何 HELD 同步运行的扣留离职行（只有成功的运行）时通过，退出码 0")
    void passesWithNoHeldRows() throws Exception {
        syncRunIn("SUCCEEDED");

        Result result = check();

        assertThat(result.exitCode()).as(result.text()).isZero();
    }

    @Test
    @DisplayName("#961 上线前检查：有 HELD 运行且其下有扣留离职行（2 行）时拒绝，退出码 1，提示写明该运行 ID、行数 2 与 HELD")
    void refusesWhileAHeldRunHoldsSeparations() throws Exception {
        UUID held = syncRunIn("HELD");
        heldSeparation(held, employed(REGULAR));
        heldSeparation(held, employed(REGULAR));

        Result result = check();

        assertThat(result.exitCode()).as(result.text()).isEqualTo(1);
        assertThat(result.out() + result.err()).as("a clear message: which run, how many rows, and that it is held").contains(held.toString(), "2", "HELD");
    }

    @Test
    @DisplayName("#961 边界：只算所属运行为 HELD 的行——已放行（RELEASED）运行下残留的行、和没有扣留行的 HELD 运行都不拦，退出码 0；同时有一个 HELD 运行带 1 行时拒绝且只报那一个运行")
    void onlyRowsOfHeldRunsCount() throws Exception {
        UUID released = syncRunIn("RELEASED");
        heldSeparation(released, employed(REGULAR));
        UUID emptyHeld = syncRunIn("HELD");

        Result passing = check();
        assertThat(passing.exitCode()).as(passing.text()).isZero();

        UUID held = syncRunIn("HELD");
        heldSeparation(held, employed(REGULAR));
        Result refusing = check();
        assertThat(refusing.exitCode()).as(refusing.text()).isEqualTo(1);
        assertThat(refusing.out() + refusing.err()).contains(held.toString()).doesNotContain(released.toString(), emptyHeld.toString());
    }

    @Test
    @DisplayName("#961 上线前检查只读：通过与拒绝两次运行都不改同步运行、扣留行、员工行，也不写 workforce 审计")
    void theCheckWritesNothing() throws Exception {
        UUID held = syncRunIn("HELD");
        heldSeparation(held, employed(REGULAR));
        List<Long> before = counts();

        check();

        assertThat(counts()).isEqualTo(before);
    }
}
