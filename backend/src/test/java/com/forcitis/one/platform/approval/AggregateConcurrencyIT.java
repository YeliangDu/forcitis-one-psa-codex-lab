package com.forcitis.one.platform.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.cost.timesheet.api.TimesheetId;
import com.forcitis.one.cost.timesheet.domain.StaleTimesheetException;
import com.forcitis.one.cost.timesheet.domain.Timesheet;
import com.forcitis.one.cost.timesheet.domain.TimesheetRepository;
import com.forcitis.one.delivery.execution.api.WorkPackageId;
import com.forcitis.one.kernel.Hours;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.domain.ApprovalRequest;
import com.forcitis.one.platform.approval.domain.ApprovalRequestRepository;
import com.forcitis.one.platform.approval.domain.ApprovalTask;
import com.forcitis.one.platform.approval.domain.ApprovalTaskRepository;
import com.forcitis.one.platform.approval.domain.StaleAggregateException;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Repositories write only what the aggregate holds, and only if nobody changed the row since it was loaded. */
class AggregateConcurrencyIT extends ApprovalIntegrationSupport {

    @Autowired
    ApprovalRequestRepository requests;

    @Autowired
    ApprovalTaskRepository tasks;

    @Autowired
    TimesheetRepository timesheets;

    @Test
    @DisplayName("DDD-4 an approval request saved from a stale copy is refused; the first writer's change stands and the version counts it")
    void staleApprovalRequestIsRefused() {
        long initiator = newId();
        long manager = newId();
        organisation.person(initiator, 1, null).person(manager, 1, null);
        String key = newKey("versioning");
        start(key, initiator, manager);

        tx.executeWithoutResult(s -> {
            ApprovalRequest request = requests.find(com.forcitis.one.platform.approval.api.ProcessKey.COST_TIMESHEET_APPROVAL, document(key)).orElseThrow();
            request.returnRound("first writer", LocalDate.of(2026, 10, 14));
            requests.save(request);
        });
        assertThat(jdbc.queryForObject("select version from platform_approval.approval_requests where document_key = ?", Long.class, key)).isEqualTo(1L);

        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            ApprovalRequest staleCopy = ApprovalRequest.restore(com.forcitis.one.platform.approval.api.ProcessKey.COST_TIMESHEET_APPROVAL, document(key),
                    LEGAL_ENTITY, new UserId(initiator), 1, new ApprovalInstanceId("irrelevant"),
                    com.forcitis.one.platform.approval.api.ApprovalStatus.RUNNING, null, 0, 0);
            staleCopy.approve(LocalDate.of(2026, 10, 14));
            requests.save(staleCopy);
        })).isInstanceOf(StaleAggregateException.class);
        assertThat(requestStatus(key)).isEqualTo("RETURNED");
        assertThat(jdbc.queryForObject("select event_count from platform_approval.approval_requests where document_key = ?", Long.class, key)).isEqualTo(1L);
    }

    @Test
    @DisplayName("DDD-4 a status that changed under a copy with the right version still blocks the write (precondition on the previous status)")
    void previousStatusIsAPrecondition() {
        long initiator = newId();
        long manager = newId();
        organisation.person(initiator, 1, null).person(manager, 1, null);
        String key = newKey("precondition");
        start(key, initiator, manager);
        long version = jdbc.queryForObject("select version from platform_approval.approval_requests where document_key = ?", Long.class, key);
        ApprovalInstanceId instance = new ApprovalInstanceId(jdbc.queryForObject(
                "select current_instance_id from platform_approval.approval_requests where document_key = ?", String.class, key));
        jdbc.update("update platform_approval.approval_requests set status = 'CANCELLED' where document_key = ?", key);

        ApprovalRequest copy = ApprovalRequest.restore(com.forcitis.one.platform.approval.api.ProcessKey.COST_TIMESHEET_APPROVAL, document(key),
                LEGAL_ENTITY, new UserId(initiator), 1, instance, com.forcitis.one.platform.approval.api.ApprovalStatus.RUNNING, null, 0, version);
        copy.approve(LocalDate.of(2026, 10, 14));

        assertThatThrownBy(() -> tx.executeWithoutResult(s -> requests.save(copy))).isInstanceOf(StaleAggregateException.class);
        assertThat(requestStatus(key)).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("DDD-4 an approval task saved from a stale copy is refused")
    void staleApprovalTaskIsRefused() {
        long initiator = newId();
        long manager = newId();
        organisation.person(initiator, 1, null).person(manager, 1, null);
        ApprovalInstanceId instance = start(newKey("task-versioning"), initiator, manager);
        ApprovalTaskId task = openTaskId(instance);

        tx.executeWithoutResult(s -> {
            ApprovalTask loaded = tasks.find(task).orElseThrow();
            loaded.claim(new UserId(manager), LocalDate.of(2026, 10, 14));
            tasks.save(loaded);
        });

        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            ApprovalTask stale = ApprovalTask.restore(task, instance, com.forcitis.one.platform.approval.api.ProcessKey.COST_TIMESHEET_APPROVAL,
                    document("x"), LEGAL_ENTITY, "projectManagerReview", com.forcitis.one.platform.approval.domain.ApprovalTaskStatus.PENDING, null,
                    null, null, null, null, null, 1, 0);
            stale.claim(new UserId(initiator), LocalDate.of(2026, 10, 14));
            tasks.save(stale);
        })).isInstanceOf(StaleAggregateException.class);
        assertThat(jdbc.queryForObject("select assignee_id from platform_approval.approval_tasks where task_id = ?", Long.class, task.value()))
                .isEqualTo(manager);
    }

    @Test
    @DisplayName("DDD-4 a timesheet saved from a stale copy is refused")
    void staleTimesheetIsRefused() {
        TimesheetId id = new TimesheetId(80_000 + newId() % 10_000);
        jdbc.update("insert into delivery_execution.work_packages (id, planned_hours, actual_hours, status) values (7003, 40, 0, 'IN_PROGRESS')"
                + " on conflict (id) do nothing");
        Timesheet draft = Timesheet.draft(id, new UserId(5), LEGAL_ENTITY, new WorkPackageId(7003), LocalDate.of(2026, 10, 13), Hours.of("8"), new UserId(6));
        tx.executeWithoutResult(s -> timesheets.save(draft));

        tx.executeWithoutResult(s -> {
            Timesheet loaded = timesheets.find(id).orElseThrow();
            loaded.revise(new UserId(5), new WorkPackageId(7003), LocalDate.of(2026, 10, 13), Hours.of("7"), new UserId(6));
            timesheets.save(loaded);
        });
        Timesheet stale = Timesheet.restore(id, new UserId(5), LEGAL_ENTITY, new WorkPackageId(7003), LocalDate.of(2026, 10, 13), Hours.of("8"),
                new UserId(6), com.forcitis.one.cost.timesheet.api.TimesheetStatus.DRAFT, 0, null, 0, 0);
        stale.revise(new UserId(5), new WorkPackageId(7003), LocalDate.of(2026, 10, 13), Hours.of("1"), new UserId(6));

        assertThatThrownBy(() -> tx.executeWithoutResult(s -> timesheets.save(stale))).isInstanceOf(StaleTimesheetException.class);
        assertThat(jdbc.queryForObject("select submitted_hours from cost_timesheet.timesheets where id = ?", java.math.BigDecimal.class, id.value()))
                .isEqualByComparingTo("7");
    }

    @Test
    @DisplayName("DDD-5 the database refuses a request or a timesheet without a legal entity")
    void legalEntityIsMandatoryInTheDatabase() {
        assertThatThrownBy(() -> jdbc.update("insert into platform_approval.approval_requests (process_key, document_type, document_key, initiator_id,"
                + " current_round, status) values ('COST_TIMESHEET_APPROVAL', 'TIMESHEET', 'no-le', 1, 1, 'RUNNING')"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into cost_timesheet.timesheets (id, employee_id, work_package_id, work_date, submitted_hours,"
                + " project_manager_id, status) values (90001, 1, 1, '2026-10-13', 1, 1, 'DRAFT')"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
