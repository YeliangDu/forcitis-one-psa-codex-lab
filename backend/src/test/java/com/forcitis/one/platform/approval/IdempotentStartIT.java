package com.forcitis.one.platform.approval;

import com.forcitis.one.cost.timesheet.api.TimesheetApprovalField;
import com.forcitis.one.cost.timesheet.api.TimesheetRoutingFlag;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalField;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalStartRequest;
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

class IdempotentStartIT extends ApprovalIntegrationSupport {

    @Autowired
    DecideTasks decideTasks;

    int instancesOf(String businessKey) {
        return (int) runtime.createProcessInstanceQuery().processInstanceBusinessKey("TIMESHEET:" + businessKey).count();
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
        assertThat(jdbc.queryForObject("select count(*) from platform_approval.start_rounds where document_key = ?", Integer.class, key))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select current_round from platform_approval.approval_requests where document_key = ?",
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
        assertThat(jdbc.queryForObject("select count(*) from platform_approval.approval_requests where document_key = ?", Integer.class, key))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from platform_approval.start_rounds where document_key = ?", Integer.class, key))
                .isEqualTo(1);
        assertThat(taskService.createTaskQuery().processInstanceId(distinct.iterator().next().value()).count()).isEqualTo(1);
    }

    @Test
    @DisplayName("RULE-266/278/287 once the previous round has ended (returned twice), each new start opens the next round with a new instance")
    void newRoundAfterTheApprovalEnded() {
        long initiator = newId();
        long manager = newId();
        long head = newId();
        long unit = newId();
        organisation.unit(unit, null, head).person(head, unit, null).person(initiator, unit, head).person(manager, unit, null);
        String key = newKey("rounds");
        ApprovalInstanceId first = start(key, initiator, manager);
        decideTasks.execute(new UserId(manager), List.of(new TaskDecision(openTaskId(first), DecisionOutcome.REJECT, "no")));
        // rejected inside the sub-process = returned: round 1 is over (M3)
        assertThat(requestStatus(key)).isEqualTo("RETURNED");

        ApprovalInstanceId second = startWithSupervisor(key, initiator, manager);
        assertThat(second).isNotEqualTo(first);
        decideTasks.execute(new UserId(manager), List.of(new TaskDecision(openTaskId(second, "projectManagerReview"), DecisionOutcome.APPROVE, "ok")));
        decideTasks.execute(new UserId(head), List.of(new TaskDecision(openTaskId(second, "supervisorReview"), DecisionOutcome.REJECT, "final no")));
        assertThat(requestStatus(key)).isEqualTo("RETURNED");

        ApprovalInstanceId third = startWithSupervisor(key, initiator, manager);
        assertThat(third).isNotIn(first, second);
        assertThat(jdbc.queryForObject("select current_round from platform_approval.approval_requests where document_key = ?",
                Integer.class, key)).isEqualTo(3);
        assertThat(jdbc.queryForList("select round_number from platform_approval.start_rounds where document_key = ? order by 1",
                Integer.class, key)).containsExactly(1, 2, 3);
        assertThat(requestStatus(key)).isEqualTo("RUNNING");
        decideTasks.execute(new UserId(manager), List.of(new TaskDecision(openTaskId(third, "projectManagerReview"), DecisionOutcome.APPROVE, "ok")));
        decideTasks.execute(new UserId(head), List.of(new TaskDecision(openTaskId(third, "supervisorReview"), DecisionOutcome.APPROVE, "ok")));
        eventually(() -> assertThat(events.completed()).anySatisfy(e -> {
            assertThat(e.document().documentId().value()).isEqualTo(key);
            assertThat(e.round()).isEqualTo(3);
            assertThat(e.outcome()).isEqualTo(com.forcitis.one.platform.approval.api.ApprovalOutcome.APPROVED);
        }));
    }

    @Test
    @DisplayName("RULE-278 the database itself refuses a duplicate (process type, business key, round)")
    void roundsAreUniqueInTheDatabase() {
        String key = newKey("unique");
        jdbc.update("insert into platform_approval.start_rounds (process_key, document_key, round_number, initiator_id, instance_id)"
                + " values ('COST_TIMESHEET_APPROVAL', ?, 1, 5, 'a')", key);
        assertThatThrownBy(() -> jdbc.update("insert into platform_approval.start_rounds (process_key, document_key, round_number,"
                + " initiator_id, instance_id) values ('COST_TIMESHEET_APPROVAL', ?, 1, 5, 'b')", key))
                .isInstanceOf(DuplicateKeyException.class);
        // the same business key under another process type is another document
        jdbc.update("insert into platform_approval.start_rounds (process_key, document_key, round_number, initiator_id, instance_id)"
                + " values ('OTHER_TYPE', ?, 1, 5, 'c')", key);
    }

    @Test
    @DisplayName("RULE-264 the request table is unique per (process type, document)")
    void requestsAreUniqueInTheDatabase() {
        String key = newKey("unique-request");
        jdbc.update("insert into platform_approval.approval_requests (process_key, document_type, document_key, legal_entity_id, initiator_id,"
                + " current_round, status) values ('COST_TIMESHEET_APPROVAL', 'TIMESHEET', ?, 1, 5, 1, 'RUNNING')", key);
        assertThatThrownBy(() -> jdbc.update("insert into platform_approval.approval_requests (process_key, document_type, document_key,"
                + " legal_entity_id, initiator_id, current_round, status) values ('COST_TIMESHEET_APPROVAL', 'TIMESHEET', ?, 1, 6, 1, 'RUNNING')", key))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @DisplayName("a rolled-back start leaves no request, no round and no instance behind (the whole start is one transaction)")
    void rolledBackStartLeavesNothing() {
        long initiator = newId();
        organisation.person(initiator, 1, null);
        String key = newKey("rollback");
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            approvals.start(new ApprovalStartRequest(ProcessKey.COST_TIMESHEET_APPROVAL, document(key), LEGAL_ENTITY, new UserId(initiator),
                    Set.of(TimesheetRoutingFlag.PROJECT_HOURS.flagName()),
                    List.of(new ApprovalField(TimesheetApprovalField.PROJECT_MANAGER.fieldName(), List.of(String.valueOf(initiator)))), "digest"));
            throw new IllegalStateException("business transaction fails after the start");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(instancesOf(key)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from platform_approval.approval_requests where document_key = ?", Integer.class, key))
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from platform_approval.start_rounds where document_key = ?", Integer.class, key))
                .isZero();
    }
}
