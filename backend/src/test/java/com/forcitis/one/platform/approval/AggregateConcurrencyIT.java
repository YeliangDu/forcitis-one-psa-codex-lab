package com.forcitis.one.platform.approval;

import com.forcitis.one.fixture.doubles.TestOrgIds;
import com.forcitis.one.fixture.doubles.TestAccounts;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.KernelError;
import com.forcitis.one.kernel.Reason;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalError;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalStatus;
import com.forcitis.one.platform.approval.api.ApprovalSummary;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.domain.ApprovalRequest;
import com.forcitis.one.platform.approval.domain.ApprovalRequestRepository;
import com.forcitis.one.platform.approval.domain.Moment;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** The repository writes the request and its tasks only if nobody changed the request row since it was loaded. */
class AggregateConcurrencyIT extends ApprovalIntegrationSupport {

    static final Moment NOW = new Moment(Instant.parse("2026-10-14T02:00:00Z"), LocalDate.of(2026, 10, 14));

    @Autowired
    ApprovalRequestRepository requests;

    @Test
    @DisplayName("DDD-4 an approval request saved from a stale copy is refused; the first writer's change stands and the version counts it")
    void staleApprovalRequestIsRefused() {
        long initiator = newId();
        long manager = newId();
        organisation.person(initiator, 1, null).person(manager, 1, null);
        String key = newKey("versioning");
        start(key, initiator, manager);

        tx.executeWithoutResult(s -> {
            ApprovalRequest request = requests.findForUpdate(ProcessKey.COST_TIMESHEET_APPROVAL, document(key)).orElseThrow();
            request.returnRound(new Reason("first writer"), NOW);
            requests.save(request);
        });
        assertThat(jdbc.queryForObject("select version from platform_approval.approval_requests where document_id = cast(? as uuid)", Long.class, key))
                .as("one save after the start's save").isEqualTo(2L);

        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            ApprovalRequest staleCopy = ApprovalRequest.restore(com.forcitis.one.TestApprovalIds.requestId(), com.forcitis.one.TestApprovalIds.generator(), ProcessKey.COST_TIMESHEET_APPROVAL, document(key), LEGAL_ENTITY,
                    TestAccounts.employee(initiator), 1, com.forcitis.one.TestApprovalIds.instance("irrelevant"), ApprovalStatus.RUNNING, null,
                    new ApprovalSummary("stale"), List.of(), 1, 0);
            staleCopy.approve(NOW);
            requests.save(staleCopy);
        })).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.errorCode()).isEqualTo(KernelError.KERNEL_CONCURRENT_CHANGE));
        assertThat(requestStatus(key)).isEqualTo("RETURNED");
    }

    @Test
    @DisplayName("DDD-3 the event sequence and the lock version are two counters: a save moves the version by one whatever the number of events")
    void versionAndEventSequenceAreSeparate() {
        long initiator = newId();
        long manager = newId();
        organisation.person(initiator, 1, null).person(manager, 1, null);
        String key = newKey("counters");
        ApprovalInstanceId instance = start(key, initiator, manager);

        assertThat(jdbc.queryForMap("select version, event_count from platform_approval.approval_requests where document_id = cast(? as uuid)", key))
                .as("the start recorded the round and one task in one save").containsEntry("version", 1L).containsEntry("event_count", 2L);
        tx.executeWithoutResult(s -> {
            ApprovalRequest request = requests.findForUpdate(ProcessKey.COST_TIMESHEET_APPROVAL, document(key)).orElseThrow();
            request.claimTask(new ApprovalTaskId(java.util.UUID.fromString(openTask(instance).getId())), TestAccounts.account(manager), NOW);
            requests.save(request);
        });
        assertThat(jdbc.queryForMap("select version, event_count from platform_approval.approval_requests where document_id = cast(? as uuid)", key))
                .containsEntry("version", 2L).containsEntry("event_count", 3L);
    }

    @Test
    @DisplayName("DDD-4 a status that changed under a copy with the right version still blocks the write (precondition on the previous status)")
    void previousStatusIsAPrecondition() {
        long initiator = newId();
        long manager = newId();
        organisation.person(initiator, 1, null).person(manager, 1, null);
        String key = newKey("precondition");
        start(key, initiator, manager);
        long version = jdbc.queryForObject("select version from platform_approval.approval_requests where document_id = cast(? as uuid)", Long.class, key);
        ApprovalInstanceId instance = new ApprovalInstanceId(jdbc.queryForObject(
                "select current_instance_id from platform_approval.approval_requests where document_id = cast(? as uuid)", java.util.UUID.class, key));
        jdbc.update("update platform_approval.approval_requests set status = 'CANCELLED', result_reason = 'synthetic: ended' where document_id = cast(? as uuid)", key);

        ApprovalRequest copy = ApprovalRequest.restore(com.forcitis.one.TestApprovalIds.requestId(), com.forcitis.one.TestApprovalIds.generator(), ProcessKey.COST_TIMESHEET_APPROVAL, document(key), LEGAL_ENTITY, TestAccounts.employee(initiator), 1,
                instance, ApprovalStatus.RUNNING, null, new ApprovalSummary("copy"), List.of(), version, 0);
        copy.approve(NOW);

        assertThatThrownBy(() -> tx.executeWithoutResult(s -> requests.save(copy))).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.errorCode()).isEqualTo(KernelError.KERNEL_CONCURRENT_CHANGE));
        assertThat(requestStatus(key)).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("DDD-4 a change of a task saved from a stale copy of its request is refused: tasks have no version of their own")
    void staleTaskChangeIsRefused() {
        long initiator = newId();
        long manager = newId();
        organisation.person(initiator, 1, null).person(manager, 1, null);
        ApprovalInstanceId instance = start(newKey("task-versioning"), initiator, manager);
        ApprovalTaskId task = openTaskId(instance);
        String key = jdbc.queryForObject("select cast(r.document_id as text) from platform_approval.approval_requests r join platform_approval.approval_tasks t on t.approval_request_id = r.id where t.task_id = ?", String.class, task.value());

        ApprovalRequest stale = tx.execute(s -> requests.find(ProcessKey.COST_TIMESHEET_APPROVAL, document(key)).orElseThrow());
        tx.executeWithoutResult(s -> {
            ApprovalRequest loaded = requests.findForUpdate(ProcessKey.COST_TIMESHEET_APPROVAL, document(key)).orElseThrow();
            loaded.claimTask(task, TestAccounts.account(manager), NOW);
            requests.save(loaded);
        });
        stale.claimTask(task, TestAccounts.account(initiator), NOW);

        assertThatThrownBy(() -> tx.executeWithoutResult(s -> requests.save(stale))).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.errorCode()).isEqualTo(KernelError.KERNEL_CONCURRENT_CHANGE));
        assertThat(jdbc.queryForObject("select assignee_id from platform_approval.approval_tasks where task_id = ?", java.util.UUID.class, task.value()))
                .isEqualTo(TestAccounts.account(manager).value());
    }

    @Test
    @DisplayName("DDD-5 the database refuses a request without a legal entity")
    void legalEntityIsMandatoryInTheDatabase() {
        assertThatThrownBy(() -> jdbc.update("insert into platform_approval.approval_requests (id, process_key, document_type, document_id, initiator_id,"
                + " current_round, current_instance_id, status, summary_text) values (uuidv7(), 'COST_TIMESHEET_APPROVAL', 'TIMESHEET', uuidv7(), " + TestOrgIds.sqlEmployee(1) + ", 1, uuidv7(), 'RUNNING', 's')"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("K6 a task cannot exist without its request: the database holds the reference")
    void aTaskNeedsItsRequest() {
        assertThatThrownBy(() -> jdbc.update("insert into platform_approval.approval_tasks (task_id, instance_id, approval_request_id, node_key,"
                + " node_name, status) values (uuidv7(), uuidv7(), uuidv7(), 'n', 'n', 'PENDING')"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
