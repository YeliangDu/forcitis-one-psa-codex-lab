package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.CountersignNames.*;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #894 IMP-1030-FC-C, design note of #878 section 10 items 7 and 12 (ADR-0073 item 3.6 line 73): an approval locks the employee, re-checks
 * the rules and, when one fails, records that request as REJECTED with the reason and withdraws the command; concurrent decisions on one
 * command end in one consistent state. Closed periods (item 8) are not covered here, see the card report.
 */
class CountersignRecheckIT extends CountersignSupport {

    static final TestDatabase postgres = TestPostgres.database(CountersignRecheckIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    private static final LocalDate DAY = TODAY.plusDays(10);

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    /** Approves the E1 and then the E2 request while the command still awaits; whichever approval fails the re-check ends it. */
    private void approveUntilEnded(UUID command) throws Exception {
        approve(headE1, command, KIND_CHANGE, e1);
        if (AWAITING.equals(commandStatus(command))) {
            approve(headE2, command, KIND_CHANGE, e2);
        }
    }

    private void assertEndedByTheRecheck(UUID command) throws Exception {
        assertThat(commandStatus(command)).isEqualTo(WITHDRAWN);
        List<Map<String, Object>> requests = requests(command, KIND_CHANGE);
        assertThat(requests).filteredOn(row -> REJECTED.equals(row.get("decision_kind"))).hasSize(1).allSatisfy(row -> {
            assertThat(row.get("decision_reason")).isNotNull();
            assertThat(String.valueOf(row.get("decision_reason")).strip()).isNotEmpty();
        });
        assertThat(requests).extracting(row -> row.get("decision_kind")).allMatch(kind -> List.of(APPROVED, REJECTED, LAPSED).contains(kind));
        assertThat(versionRowsWrittenBy(command)).isZero();
        assertThat(eventsOf(EVENT_LEGAL_ENTITY_CHANGED, command)).isZero();
        assertThat(openRequests(command)).isZero();
    }

    @Test
    @DisplayName("#894 note #878 第 10 条第 7 项、ADR-0073 第 3 条第 6 项（行 73）：待会签期间录入了更早生效的离职（离职之后规则）：批准时重校验不通过，该请求记为已拒签并写明原因，命令转已撤回，不写版本、不发事件")
    void aSeparationEnteredMeanwhileEndsTheCommand() throws Exception {
        Awaiting waiting = crossEntityTransfer(DAY);
        actIn(hr);
        UUID separation = separate(waiting.employee(), TODAY.minusDays(1));
        assertThat(commandStatus(separation)).isEqualTo(EFFECTIVE);

        approveUntilEnded(waiting.command());

        assertEndedByTheRecheck(waiting.command());
    }

    @Test
    @DisplayName("#894 note #878 第 10 条第 7 项、第 4 节：待会签期间另一条更早生效的调岗改变了成本法人（会签依据已过时）：批准时重算成本法人与依据不符，该请求记为已拒签并写明原因，命令转已撤回；更早的那条会签通过后正常生效")
    void aCostEntityChangedMeanwhileEndsTheCommand() throws Exception {
        Awaiting waiting = crossEntityTransfer(DAY);
        actIn(hr);
        UUID earlier = transfer(waiting.employee(), TODAY.plusDays(5), unitE3);
        assertThat(commandStatus(earlier)).isEqualTo(AWAITING);
        approve(headE1, earlier, KIND_CHANGE, e1);
        approve(headE3, earlier, KIND_CHANGE, e3);
        assertThat(commandStatus(earlier)).isEqualTo(PENDING);

        approveUntilEnded(waiting.command());

        assertEndedByTheRecheck(waiting.command());
        assertThat(currentVersions(ASSIGNMENT_VERSIONS, waiting.employee())).anyMatch(row -> earlier.equals(row.get("change_command_id")));
    }

    @RepeatedTest(3)
    @DisplayName("#894 note #878 第 10 条第 7、12 项（并发，两线程）、第 4 节：两位负责人同时批准各自的请求：恰有一方在锁下完成写入，命令待生效，两行都已批准，任职版本只写一份，状态转换为待会签到待生效各一次，两个调用都不失败")
    void concurrentApprovalsWriteOnce() throws Exception {
        Awaiting waiting = crossEntityTransfer(DAY);
        UUID first = requestId(waiting.command(), KIND_CHANGE, e1);
        UUID second = requestId(waiting.command(), KIND_CHANGE, e2);
        SecurityContext firstContext = contextOf(headE1);
        SecurityContext secondContext = contextOf(headE2);
        CountersignProbe probe = probeWithoutContext();

        List<Throwable> failures = race(
                () -> decide(probe, firstContext, first, true),
                () -> decide(probe, secondContext, second, true));

        assertThat(failures).as("both decisions are accepted").containsOnlyNulls();
        assertThat(commandStatus(waiting.command())).isEqualTo(PENDING);
        assertThat(requests(waiting.command(), KIND_CHANGE)).allMatch(row -> APPROVED.equals(row.get("decision_kind")));
        assertThat(allVersions(ASSIGNMENT_VERSIONS, waiting.employee())).filteredOn(row -> waiting.command().equals(row.get("change_command_id"))).hasSize(1);
        assertThat(statusPath(AGGREGATE_PERSONNEL_COMMAND, waiting.command())).containsExactly(AWAITING, PENDING);
        assertContiguous(currentVersions(ASSIGNMENT_VERSIONS, waiting.employee()));
    }

    @RepeatedTest(3)
    @DisplayName("#894 note #878 第 10 条第 7 项（并发，两线程）：一位批准的同时另一位拒签：不论谁先到，命令都转已撤回、无版本、无事件、没有未决请求；拒签的那行为 REJECTED")
    void concurrentApprovalAndRejectionEndConsistently() throws Exception {
        Awaiting waiting = crossEntityTransfer(DAY);
        UUID approving = requestId(waiting.command(), KIND_CHANGE, e1);
        UUID rejecting = requestId(waiting.command(), KIND_CHANGE, e2);
        SecurityContext approverContext = contextOf(headE1);
        SecurityContext rejecterContext = contextOf(headE2);
        CountersignProbe probe = probeWithoutContext();

        race(() -> decide(probe, approverContext, approving, true), () -> decide(probe, rejecterContext, rejecting, false));

        assertThat(commandStatus(waiting.command())).isEqualTo(WITHDRAWN);
        assertThat(decision(waiting.command(), KIND_CHANGE, e2)).isEqualTo(REJECTED);
        assertThat(decision(waiting.command(), KIND_CHANGE, e1)).isIn(APPROVED, LAPSED);
        assertThat(openRequests(waiting.command())).isZero();
        assertThat(versionRowsWrittenBy(waiting.command())).isZero();
        assertThat(allCommandEventsOf(waiting.command())).isZero();
    }

    private void decide(CountersignProbe probe, SecurityContext caller, UUID request, boolean approve) {
        SecurityContextHolder.setContext(caller);
        try {
            if (approve) {
                probe.approve(request, null);
            } else {
                probe.reject(request, "synthetic: rejected at the same time");
            }
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /** Runs both calls at the same moment on two threads; answers each call's failure, or null. */
    private static List<Throwable> race(Runnable one, Runnable two) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Throwable>> futures = new ArrayList<>();
            for (Runnable call : List.of(one, two)) {
                Callable<Throwable> task = () -> {
                    ready.countDown();
                    go.await();
                    try {
                        call.run();
                        return null;
                    } catch (Throwable failure) {
                        return failure;
                    }
                };
                futures.add(pool.submit(task));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            List<Throwable> failures = new ArrayList<>();
            for (Future<Throwable> future : futures) {
                failures.add(future.get(60, TimeUnit.SECONDS));
            }
            return failures;
        } finally {
            pool.shutdownNow();
        }
    }
}
