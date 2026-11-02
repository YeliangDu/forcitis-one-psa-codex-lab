package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.OrgCountersignNames.*;
import static com.forcitis.one.acceptance.organization.workforce.OrgStructureCommandNames.EVENT_MERGED;
import static com.forcitis.one.acceptance.organization.workforce.OrgStructureCommandNames.REVOKED;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.COMMAND_KIND;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.EFFECTIVE;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.EFFECTIVE_DATE;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.EMPLOYEE_ID;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.PENDING;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.REGULAR;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.TARGET_ORG_UNIT_ID;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.TRANSFER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.AccountsAcceptanceSupport;
import com.forcitis.one.kernel.DomainException;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #910 acceptance part (#909 F4a: "peak scenario, ≥ 100 transfers a day incl. one cross-entity, no deadlock, progress correct"): a
 * countersigned merge of 100 people, one of whom inherits E1 and moves to E2 (the cross-entity person, in the basis), is executed by two
 * runs at once while a 人事专员 enters 20 same-day transfers of other people; every call finishes within the bound, none fails with a
 * deadlock or serialization error, and the merge ends EFFECTIVE with 100 affected and 100 transferred, one transfer per person. Repeated,
 * because the interleaving differs per run (acceptance playbook step 6).
 */
class OrgMergePeakIT extends OrgCountersignSupport {

    static final TestDatabase postgres = TestPostgres.database(OrgMergePeakIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private static final int PEOPLE = 100;
    private static final int OTHER_TRANSFERS = 20;
    private static final Duration BOUND = Duration.ofMinutes(3);

    private SecurityContext contextOf(Staff who) throws Exception {
        actIn(who);
        restoreActing();
        return SecurityContextHolder.getContext();
    }

    private static <T> Callable<T> as(SecurityContext context, Callable<T> work) {
        return () -> {
            SecurityContextHolder.setContext(context);
            try {
                return work.call();
            } finally {
                SecurityContextHolder.clearContext();
            }
        };
    }

    /** A failure caused by a lock conflict the database resolved by aborting: deadlock (40P01) or serialization failure (40001). */
    private static boolean isLockAbort(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && sql.getSQLState() != null && sql.getSQLState().startsWith("40")) {
                return true;
            }
            if (cause.getClass().getSimpleName().contains("Deadlock") || cause.getClass().getSimpleName().contains("CannotAcquireLock")) {
                return true;
            }
        }
        return false;
    }

    @RepeatedTest(3)
    @DisplayName("#910 F4a 高峰场景（#909 完成标准）：100 人的已会签合并（含 1 名跨法人 E1 → E2 的人）由两次执行同时跑，同时人事专员录入 20 条同日调岗：都在时限内结束，没有死锁或序列化失败；合并 EFFECTIVE、受影响 = 已转 = 100、每人一条调岗；跨法人的人发一次「成本法人已变更」；20 条调岗都生效")
    void aHundredPersonMergeWithConcurrentTransfers() throws Exception {
        LocalDate day = TODAY.plusDays(2);
        UUID merged = seedUnit(topE1);
        UUID receiving = seedUnit(topE2);
        UUID crossEntity = inheritingIn(merged);
        List<UUID> people = new ArrayList<>(List.of(crossEntity));
        for (int i = 1; i < PEOPLE; i++) {
            people.add(employedIn(merged, REGULAR, true));
        }
        UUID otherFrom = seedUnit(topE1);
        UUID otherTo = seedUnit(topE1);
        List<UUID> others = new ArrayList<>();
        for (int i = 0; i < OTHER_TRANSFERS; i++) {
            others.add(employedIn(otherFrom, REGULAR, true));
        }
        actIn(orgAdmin);
        UUID merge = merge(merged, receiving, day);
        assertThat(basis(merge)).containsOnlyKeys(crossEntity);
        approve(headE1, merge, KIND_CHANGE, e1);
        approve(headE2, merge, KIND_CHANGE, e2);
        assertThat(statusOf(merge)).isEqualTo(PENDING);
        setToday(day);
        SecurityContext admin = contextOf(orgAdmin);
        actInSpecialist();
        SecurityContext specialist = contextOf(hr);
        OrgStructureCommandsProbe orgProbe = OrgStructureCommandsProbe.of(springContext, AccountsAcceptanceSupport::employeeIdOf);
        PersonnelCommandsProbe personnelProbe = PersonnelCommandsProbe.of(springContext, AccountsAcceptanceSupport::employeeIdOf);

        ExecutorService pool = Executors.newFixedThreadPool(3);
        List<Future<?>> runs = new ArrayList<>();
        runs.add(pool.submit(as(admin, () -> attempt(() -> orgProbe.executeMerge(merge)))));
        runs.add(pool.submit(as(admin, () -> attempt(() -> orgProbe.executeMerge(merge)))));
        runs.add(pool.submit(as(specialist, () -> {
            List<Throwable> failures = new ArrayList<>();
            for (UUID other : others) {
                failures.add(attempt(() -> personnelProbe.enter(Map.of(EMPLOYEE_ID, other, COMMAND_KIND, TRANSFER, EFFECTIVE_DATE, day, TARGET_ORG_UNIT_ID, otherTo))));
            }
            return failures;
        })));
        pool.shutdown();
        assertThat(pool.awaitTermination(BOUND.toSeconds(), TimeUnit.SECONDS)).as("no run hangs").isTrue();
        List<Throwable> failures = new ArrayList<>();
        for (Future<?> run : runs) {
            Object result = run.get();
            if (result instanceof List<?> list) {
                list.forEach(each -> failures.add((Throwable) each));
            } else {
                failures.add((Throwable) result);
            }
        }
        assertThat(failures).noneMatch(OrgMergePeakIT::isLockAbort);
        assertThat(failures).as("the only acceptable refusal is a business one (e.g. the other run holds the merge)")
                .allMatch(failure -> failure == null || failure instanceof DomainException);

        actIn(orgAdmin);
        // F2a leaves open whether rerunning a finished merge is refused (OrgUnitMergeIT): only the outcome counts.
        catchThrowable(() -> orgCommands().executeMerge(merge));
        Map<String, Object> row = orgCommand(merge);
        assertThat(row.get("status")).isEqualTo(EFFECTIVE);
        assertThat(row.get("affected_employee_count")).isEqualTo(PEOPLE);
        assertThat(row.get("transferred_employee_count")).isEqualTo(PEOPLE);
        assertThat(generatedTransfers(merge)).hasSize(PEOPLE).containsOnlyKeys(people);
        assertThat(rows("select count(*) as n from personnel_commands where org_structure_command_id = ?", merge).get(0).get("n")).isEqualTo((long) PEOPLE);
        assertThat(unitOn(merged, day).get("status")).isEqualTo(REVOKED);
        assertThat(eventsOf(EVENT_MERGED, merge)).isEqualTo(1);
        assertThat(legalEntityChangesOf(crossEntity)).isEqualTo(1);
        for (UUID other : others) {
            assertThat(unitOf(other, day)).isEqualTo(otherTo);
        }
    }

    private static Throwable attempt(Runnable call) {
        try {
            call.run();
            return null;
        } catch (RuntimeException failure) {
            return failure;
        }
    }
}
