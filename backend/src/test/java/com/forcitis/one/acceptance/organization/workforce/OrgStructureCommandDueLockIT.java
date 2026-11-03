package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.OrgCountersignNames.KIND_WITHDRAWAL;
import static com.forcitis.one.acceptance.organization.workforce.OrgCountersignNames.LAPSED;
import static com.forcitis.one.acceptance.organization.workforce.OrgStructureCommandNames.EVENT_REPARENTED;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.EFFECTIVE;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #1146 done-when added by lead on 2026-11-03 (from #1161): the nightly writers of this area must not interleave on shared rows. The org due
 * job, the personnel due job (00:05) and the countersign deadline job (#896, 00:10) take different ShedLocks; if a catch-up run of one is still
 * going when another starts, a stale version write must not roll a command back for a day.
 * What these tests demand, exactly: OrgStructureCommandDueJob.run() and AttributionCountersignDeadlineJob.runOnce() are called directly on two
 * threads, so each must be safe when the other is running at the same moment (blocking or row-level locking reachable from both entry points,
 * in approval's order). A fixed sequence that exists only in scheduling, or a skip-type ShedLock on the scheduled method, does not pass; the
 * catch-up runs the card names are exactly such direct overlaps. The rounds repeat the overlap with fresh rows because concurrency is not
 * deterministic, so a red run proves more than a green one. The second test is a regression net: the optimistic version check already holds
 * a double run to one effect, so it does not prove the implementer's locking. The pair personnel due job against deadline job is not run here
 * (a countersigned personnel command with an open withdrawal set has no shared builder yet); lead decides whether to add it.
 */
class OrgStructureCommandDueLockIT extends OrgStructureCommandDueSupport {

    static final TestDatabase postgres = TestPostgres.database(OrgStructureCommandDueLockIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    private static final int ROUNDS = 5;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    /** A countersigned PENDING assignment with an open withdrawal request set (one row per head), due on the day. */
    private Countersigned withOpenWithdrawal() throws Exception {
        Countersigned signed = countersignedAssignment(DAY);
        orgCommands().withdraw(signed.command(), "synthetic: plan changed");
        assertThat(requests(signed.command(), KIND_WITHDRAWAL)).hasSize(2).allSatisfy(row -> assertThat(row.get("decision_kind")).isNull());
        return signed;
    }

    @Test
    @DisplayName("#1146 加入的完成标准（#1161）：到日命令带未决撤回请求，组织到日作业与 #896 截止作业在同一天同时开跑（多轮、每轮新行）：两边都不失败，命令在当天 EFFECTIVE 而不是被撤回或回滚，未决撤回行 LAPSED，每位依据员工的事件恰好一次")
    void theDueJobAndTheDeadlineJobOverlappingOnOneCommand() throws Exception {
        for (int round = 1; round <= ROUNDS; round++) {
            Countersigned signed = withOpenWithdrawal();
            setToday(DAY);

            overlap(this::runDueJob, this::runDeadlineJob);

            String where = "round " + round;
            assertThat(statusOf(signed.command())).as(where).isEqualTo(EFFECTIVE);
            assertThat(unitAttributions(signed.unit())).as(where).singleElement().satisfies(row -> assertThat(row.get("legal_entity_id")).isEqualTo(e2));
            assertThat(requests(signed.command(), KIND_WITHDRAWAL)).as(where).allSatisfy(row -> assertThat(row.get("decision_kind")).isEqualTo(LAPSED));
            assertThat(legalEntityChangesOf(signed.first())).as(where).isEqualTo(1);
            assertThat(legalEntityChangesOf(signed.second())).as(where).isEqualTo(1);
            setToday(TODAY);
        }
    }

    @Test
    @DisplayName("#1146 加入的完成标准（#1161）：组织到日作业的两次运行同时开跑在同一条到日命令上（补跑与新一轮重叠）：命令只生效一次、applied_at 只写一次、事件每位员工恰好一次，两次都不失败")
    void twoRunsOfTheDueJobOverlappingOnOneCommand() throws Exception {
        for (int round = 1; round <= ROUNDS; round++) {
            Countersigned signed = countersignedAssignment(DAY);
            UUID unit = seedUnit(topE1);
            UUID reparent = reparent(unit, DAY, seedUnit(topE1));
            setToday(DAY);

            overlap(this::runDueJob, this::runDueJob);

            String where = "round " + round;
            assertThat(statusOf(signed.command())).as(where).isEqualTo(EFFECTIVE);
            assertThat(statusOf(reparent)).as(where).isEqualTo(EFFECTIVE);
            assertThat(legalEntityChangesOf(signed.first())).as(where).isEqualTo(1);
            assertThat(legalEntityChangesOf(signed.second())).as(where).isEqualTo(1);
            assertThat(eventsOf(EVENT_REPARENTED, reparent)).as(where).isEqualTo(1);
            setToday(TODAY);
        }
    }
}
