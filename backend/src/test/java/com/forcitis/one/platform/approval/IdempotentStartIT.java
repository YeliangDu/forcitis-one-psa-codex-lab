package com.forcitis.one.platform.approval;

import com.forcitis.one.fixture.doubles.TestOrgIds;
import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.platform.approval.api.RoundNumber;
import com.forcitis.one.cost.timesheet.api.TimesheetApprovalField;
import com.forcitis.one.cost.timesheet.api.TimesheetRoutingFlag;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.kernel.KernelError;
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
        assertThat(runtime.getVariables(first.value().toString())).doesNotContainKey("businessKey");
        assertThat(jdbc.queryForObject("select count(*) from platform_approval.start_rounds where approval_request_id = (select id from platform_approval.approval_requests where document_id = cast(? as uuid))", Integer.class, key))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select current_round from platform_approval.approval_requests where document_id = cast(? as uuid)",
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
        assertThat(jdbc.queryForObject("select count(*) from platform_approval.approval_requests where document_id = cast(? as uuid)", Integer.class, key))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from platform_approval.start_rounds where approval_request_id = (select id from platform_approval.approval_requests where document_id = cast(? as uuid))", Integer.class, key))
                .isEqualTo(1);
        assertThat(taskService.createTaskQuery().processInstanceId(distinct.iterator().next().value().toString()).count()).isEqualTo(1);
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
        decideTasks.execute(TestAccounts.account(manager), List.of(new TaskDecision(openTaskId(first), DecisionOutcome.REJECT, "no")), java.util.OptionalLong.empty());
        // rejected inside the sub-process = returned: round 1 is over (M3)
        assertThat(requestStatus(key)).isEqualTo("RETURNED");

        ApprovalInstanceId second = startWithSupervisor(key, initiator, manager);
        assertThat(second).isNotEqualTo(first);
        decideTasks.execute(TestAccounts.account(manager), List.of(new TaskDecision(openTaskId(second, "projectManagerReview"), DecisionOutcome.APPROVE, "ok")), java.util.OptionalLong.empty());
        decideTasks.execute(TestAccounts.account(head), List.of(new TaskDecision(openTaskId(second, "supervisorReview"), DecisionOutcome.REJECT, "final no")), java.util.OptionalLong.empty());
        assertThat(requestStatus(key)).isEqualTo("RETURNED");

        ApprovalInstanceId third = startWithSupervisor(key, initiator, manager);
        assertThat(third).isNotIn(first, second);
        assertThat(jdbc.queryForObject("select current_round from platform_approval.approval_requests where document_id = cast(? as uuid)",
                Integer.class, key)).isEqualTo(3);
        assertThat(jdbc.queryForList("select round_number from platform_approval.start_rounds where approval_request_id = (select id from platform_approval.approval_requests where document_id = cast(? as uuid)) order by 1",
                Integer.class, key)).containsExactly(1, 2, 3);
        assertThat(requestStatus(key)).isEqualTo("RUNNING");
        decideTasks.execute(TestAccounts.account(manager), List.of(new TaskDecision(openTaskId(third, "projectManagerReview"), DecisionOutcome.APPROVE, "ok")), java.util.OptionalLong.empty());
        decideTasks.execute(TestAccounts.account(head), List.of(new TaskDecision(openTaskId(third, "supervisorReview"), DecisionOutcome.APPROVE, "ok")), java.util.OptionalLong.empty());
        eventually(() -> assertThat(events.completed()).anySatisfy(e -> {
            assertThat(e.document().documentId().toString()).isEqualTo(key);
            assertThat(e.round()).isEqualTo(new RoundNumber(3));
            assertThat(e.outcome()).isEqualTo(com.forcitis.one.platform.approval.api.ApprovalOutcome.APPROVED);
        }));
    }

    @Test
    @DisplayName("RULE-278 the database itself refuses a duplicate (process type, document, round) and a second round started by the same event")
    void roundsAreUniqueInTheDatabase() {
        String key = newKey("unique");
        String event = java.util.UUID.randomUUID().toString();
        String instance = java.util.UUID.randomUUID().toString();
        insertRequestWithFirstRound(key, instance, event);
        assertThatThrownBy(() -> jdbc.update("insert into platform_approval.start_rounds (id, approval_request_id, round_number,"
                + " initiator_id, instance_id, trigger_event_id) values (uuidv7(), (select id from platform_approval.approval_requests where document_id = cast(? as uuid)), 1, " + TestOrgIds.sqlEmployee(5) + ", uuidv7(), gen_random_uuid())", key))
                .isInstanceOf(DuplicateKeyException.class);
        assertThatThrownBy(() -> jdbc.update("insert into platform_approval.start_rounds (id, approval_request_id, round_number,"
                + " initiator_id, instance_id, trigger_event_id) values (uuidv7(), (select id from platform_approval.approval_requests where document_id = cast(? as uuid)), 2, " + TestOrgIds.sqlEmployee(5) + ", uuidv7(), ?::uuid)", key, event))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @DisplayName("RULE-264 the request table is unique per (process type, document)")
    void requestsAreUniqueInTheDatabase() {
        String key = newKey("unique-request");
        insertRequestWithFirstRound(key, java.util.UUID.randomUUID().toString(), java.util.UUID.randomUUID().toString());
        assertThatThrownBy(() -> jdbc.update("insert into platform_approval.approval_requests (id, process_key, document_type, document_id,"
                + " legal_entity_id, initiator_id, current_round, current_instance_id, status, summary_text) values (uuidv7(), 'cost_timesheet_approval', 'TIMESHEET', cast(? as uuid), '" + LEGAL_ENTITY.value() + "', " + TestOrgIds.sqlEmployee(6) + ", 1,"
                + " uuidv7(), 'RUNNING', 's')", key)).isInstanceOf(DuplicateKeyException.class);
    }

    private void insertRequestWithFirstRound(String key, String instance, String event) {
        jdbc.update("with r as (insert into platform_approval.approval_requests (id, process_key, document_type, document_id, legal_entity_id, initiator_id,"
                + " current_round, current_instance_id, status, summary_text) values (uuidv7(), 'cost_timesheet_approval', 'TIMESHEET', cast(? as uuid), '" + LEGAL_ENTITY.value() + "', " + TestOrgIds.sqlEmployee(5)
                + ", 1, cast(? as uuid), 'RUNNING', 's') returning id) insert into platform_approval.start_rounds (id, approval_request_id, round_number, initiator_id, instance_id,"
                + " trigger_event_id) select uuidv7(), r.id, 1, " + TestOrgIds.sqlEmployee(5) + ", cast(? as uuid), cast(? as uuid) from r", key, instance, instance, event);
    }

    @Test
    @DisplayName("D-1a-78 asking for the round that exists is a retry and returns its instance; skipping a round, or starting round 2 while round 1 runs, is refused")
    void roundsAreAskedForInOrder() {
        long initiator = newId();
        long manager = newId();
        organisation.person(initiator, 1, null).person(manager, 1, null);
        String key = newKey("round-order");
        ApprovalInstanceId first = start(key, initiator, manager);
        java.util.function.Function<Integer, ApprovalInstanceId> ask = round -> tx.execute(s -> approvals.start(startRequest(key, TestAccounts.employee(initiator),
                Set.of(TimesheetRoutingFlag.PROJECT_HOURS.flagName()),
                List.of(new ApprovalField(TimesheetApprovalField.PROJECT_MANAGER.fieldName(), List.of(TestAccounts.employee(manager).toString()))), round)));

        assertThat(ask.apply(1)).isEqualTo(first);
        assertThatThrownBy(() -> ask.apply(2)).isInstanceOfSatisfying(com.forcitis.one.kernel.DomainException.class,
                e -> assertThat(e.errorCode()).isEqualTo(KernelError.KERNEL_TRANSITION_ILLEGAL));
        assertThatThrownBy(() -> ask.apply(3)).isInstanceOfSatisfying(com.forcitis.one.kernel.DomainException.class,
                e -> assertThat(e.errorCode()).isEqualTo(com.forcitis.one.platform.approval.api.ApprovalError.PLATFORM_APPROVAL_ROUND_MISMATCH));
        assertThat(requestStatus(key)).isEqualTo("RUNNING");
    }

    @Test
    @DisplayName("D-1a-78 a returned, a rejected and a cancelled approval can start the next round; an approved one cannot be started again")
    void whichEndedApprovalsMayStartAgain() {
        long initiator = newId();
        long manager = newId();
        organisation.person(initiator, 1, null).person(manager, 1, null);
        for (String ended : List.of("RETURNED", "REJECTED", "CANCELLED")) {
            String key = newKey("again-" + ended.toLowerCase());
            start(key, initiator, manager);
            jdbc.update("update platform_approval.approval_requests set status = ?, result_reason = 'synthetic: ended' where document_id = cast(? as uuid)", ended, key);
            assertThat(start(key, initiator, manager)).as(ended).isNotNull();
            assertThat(requestStatus(key)).as(ended).isEqualTo("RUNNING");
            assertThat(jdbc.queryForObject("select current_round from platform_approval.approval_requests where document_id = cast(? as uuid)", Integer.class, key))
                    .isEqualTo(2);
        }
        String approved = newKey("again-approved");
        start(approved, initiator, manager);
        jdbc.update("update platform_approval.approval_requests set status = 'APPROVED' where document_id = cast(? as uuid)", approved);
        assertThatThrownBy(() -> start(approved, initiator, manager)).isInstanceOfSatisfying(com.forcitis.one.kernel.DomainException.class,
                e -> assertThat(e.errorCode()).isEqualTo(com.forcitis.one.platform.approval.api.ApprovalError.PLATFORM_APPROVAL_ALREADY_APPROVED));
    }

    @Test
    @DisplayName("a rolled-back start leaves no request, no round and no instance behind (the whole start is one transaction)")
    void rolledBackStartLeavesNothing() {
        long initiator = newId();
        organisation.person(initiator, 1, null);
        String key = newKey("rollback");
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            approvals.start(startRequest(key, TestAccounts.employee(initiator), Set.of(TimesheetRoutingFlag.PROJECT_HOURS.flagName()),
                    List.of(new ApprovalField(TimesheetApprovalField.PROJECT_MANAGER.fieldName(), List.of(TestAccounts.employee(initiator).toString())))));
            throw new IllegalStateException("business transaction fails after the start");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(instancesOf(key)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from platform_approval.approval_requests where document_id = cast(? as uuid)", Integer.class, key))
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from platform_approval.start_rounds where approval_request_id = (select id from platform_approval.approval_requests where document_id = cast(? as uuid))", Integer.class, key))
                .isZero();
    }
}
