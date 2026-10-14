package com.forcitis.one.platform.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalField;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalRequest;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.application.DecideTasks;
import com.forcitis.one.platform.approval.application.DecisionOutcome;
import com.forcitis.one.platform.approval.application.TaskDecision;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

/** RULE-264/266/278/314: starts are idempotent per (process type, business key) and rounds are unique, even under concurrency. */
class IdempotentStartIT extends ApprovalIntegrationSupport {

    @Autowired
    DecideTasks decideTasks;

    int instancesOf(String businessKey) {
        return (int) runtime.createProcessInstanceQuery().processInstanceBusinessKey(businessKey).count();
    }

    @Test
    @DisplayName("RULE-264/266/287 starting the same document twice returns the live instance and creates no second one")
    void repeatedStartReturnsTheLiveInstance() {
        long initiator = newId();
        long manager = newId();
        organisation.person(initiator, 1, null).person(manager, 1, null);
        String key = newKey("idempotent");
        ApprovalInstanceId first = start(key, initiator, manager);
        ApprovalInstanceId second = start(key, initiator, manager);
        assertThat(second).isEqualTo(first);
        assertThat(instancesOf(key)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from platform_approval.start_rounds where business_key = ?", Integer.class, key))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select current_round from platform_approval.approval_requests where business_key = ?",
                Integer.class, key)).isEqualTo(1);
    }

    @Test
    @DisplayName("RULE-264/278 many concurrent starts of one document produce exactly one instance and one round")
    void concurrentStartsProduceOneInstance() throws Exception {
        long initiator = newId();
        long manager = newId();
        organisation.person(initiator, 1, null).person(manager, 1, null);
        String key = newKey("concurrent");
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<ApprovalInstanceId>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Callable<ApprovalInstanceId> call = () -> {
                ready.countDown();
                go.await();
                return start(key, initiator, manager);
            };
            results.add(pool.submit(call));
        }
        ready.await();
        go.countDown();
        Set<ApprovalInstanceId> distinct = new java.util.HashSet<>();
        for (Future<ApprovalInstanceId> result : results) {
            distinct.add(result.get(60, TimeUnit.SECONDS));
        }
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(distinct).hasSize(1);
        assertThat(instancesOf(key)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from platform_approval.approval_requests where business_key = ?", Integer.class, key))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from platform_approval.start_rounds where business_key = ?", Integer.class, key))
                .isEqualTo(1);
        assertThat(taskService.createTaskQuery().processInstanceId(distinct.iterator().next().value()).count()).isEqualTo(1);
    }

    @Test
    @DisplayName("RULE-266/278/287 once the previous round has ended, a new start opens round 2 with a new instance")
    void newRoundAfterTheApprovalEnded() {
        long initiator = newId();
        long manager = newId();
        long head = newId();
        long unit = newId();
        organisation.unit(unit, null, head).person(head, unit, null).person(initiator, unit, null).person(manager, unit, null);
        String key = newKey("rounds");
        ApprovalInstanceId first = start(key, initiator, manager);
        decideTasks.execute(new UserId(manager), List.of(new TaskDecision(openTaskId(first), DecisionOutcome.REJECT, "no")));
        // rejected inside the sub-process = returned, still the same round; finish it properly
        decideTasks.execute(new UserId(initiator), List.of(new TaskDecision(openTaskId(first), DecisionOutcome.APPROVE, "fixed")));
        decideTasks.execute(new UserId(manager), List.of(new TaskDecision(openTaskId(first), DecisionOutcome.APPROVE, "ok")));
        decideTasks.execute(new UserId(head), List.of(new TaskDecision(openTaskId(first), DecisionOutcome.REJECT, "final no")));
        assertThat(requestStatus(key)).isEqualTo("REJECTED");

        ApprovalInstanceId second = start(key, initiator, manager);
        assertThat(second).isNotEqualTo(first);
        assertThat(jdbc.queryForObject("select current_round from platform_approval.approval_requests where business_key = ?",
                Integer.class, key)).isEqualTo(2);
        assertThat(jdbc.queryForList("select round_number from platform_approval.start_rounds where business_key = ? order by 1",
                Integer.class, key)).containsExactly(1, 2);
        assertThat(requestStatus(key)).isEqualTo("RUNNING");
        decideTasks.execute(new UserId(manager), List.of(new TaskDecision(openTaskId(second), DecisionOutcome.APPROVE, "ok")));
        decideTasks.execute(new UserId(head), List.of(new TaskDecision(openTaskId(second), DecisionOutcome.APPROVE, "ok")));
        eventually(() -> assertThat(events.completed()).anySatisfy(e -> {
            assertThat(e.businessKey()).isEqualTo(key);
            assertThat(e.round()).isEqualTo(2);
        }));
    }

    @Test
    @DisplayName("RULE-278 the database itself refuses a duplicate (process type, business key, round)")
    void roundsAreUniqueInTheDatabase() {
        String key = newKey("unique");
        jdbc.update("insert into platform_approval.start_rounds (process_type, business_key, round_number, initiator_id, instance_id)"
                + " values ('COST_TIMESHEET_APPROVAL', ?, 1, 5, 'a')", key);
        assertThatThrownBy(() -> jdbc.update("insert into platform_approval.start_rounds (process_type, business_key, round_number,"
                + " initiator_id, instance_id) values ('COST_TIMESHEET_APPROVAL', ?, 1, 5, 'b')", key))
                .isInstanceOf(DuplicateKeyException.class);
        // the same business key under another process type is another document (RULE-266 keyed by process type)
        jdbc.update("insert into platform_approval.start_rounds (process_type, business_key, round_number, initiator_id, instance_id)"
                + " values ('OTHER_TYPE', ?, 1, 5, 'c')", key);
    }

    @Test
    @DisplayName("RULE-264 the request table is unique per (process type, business key)")
    void requestsAreUniqueInTheDatabase() {
        String key = newKey("unique-request");
        jdbc.update("insert into platform_approval.approval_requests (process_type, business_key, initiator_id, current_round, status)"
                + " values ('COST_TIMESHEET_APPROVAL', ?, 5, 1, 'RUNNING')", key);
        assertThatThrownBy(() -> jdbc.update("insert into platform_approval.approval_requests (process_type, business_key, initiator_id,"
                + " current_round, status) values ('COST_TIMESHEET_APPROVAL', ?, 6, 1, 'RUNNING')", key))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @DisplayName("a rolled-back start leaves no request, no round and no instance behind (the whole start is one transaction)")
    void rolledBackStartLeavesNothing() {
        long initiator = newId();
        organisation.person(initiator, 1, null);
        String key = newKey("rollback");
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            approvals.start(new ApprovalRequest(ProcessKey.COST_TIMESHEET_APPROVAL, key, new UserId(initiator), Set.of(),
                    List.of(new ApprovalField("manager_id", List.of(String.valueOf(initiator))))));
            throw new IllegalStateException("business transaction fails after the start");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(instancesOf(key)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from platform_approval.approval_requests where business_key = ?", Integer.class, key))
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from platform_approval.start_rounds where business_key = ?", Integer.class, key))
                .isZero();
    }
}
