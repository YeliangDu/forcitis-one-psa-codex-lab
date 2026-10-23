package com.forcitis.one.platform.approval;

import com.forcitis.one.cost.timesheet.api.TimesheetApprovalProcess;
import com.forcitis.one.fixture.doubles.TestAccounts;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.ActorType;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.cost.timesheet.api.TimesheetApprovalField;
import com.forcitis.one.platform.approval.api.ApprovalField;
import com.forcitis.one.platform.approval.api.ApprovalOutcome;
import com.forcitis.one.platform.approval.api.ApprovalSummary;
import com.forcitis.one.platform.approval.api.ContentHash;
import com.forcitis.one.platform.approval.api.RoundNumber;
import com.forcitis.one.platform.approval.api.RoutingFlag;
import com.forcitis.one.platform.approval.events.ApprovalCompleted;
import com.forcitis.one.platform.approval.events.DocumentSubmittedForApproval;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.test.EnableScenarios;
import org.springframework.modulith.test.Scenario;

/** The approval module starts the rounds that document owners ask for; the request is the only way in (I-5). */
@EnableScenarios
class StartApprovalOnSubmissionIT extends ApprovalIntegrationSupport {

    @Autowired
    MeterRegistry meters;

    DocumentSubmittedForApproval request(String key, long initiator, long manager, int round, String flag) {
        EventEnvelope envelope = envelope("platform.approval.DocumentSubmittedForApproval", "Timesheet", UUID.fromString(key), round);
        return new DocumentSubmittedForApproval(envelope, TimesheetApprovalProcess.KEY, document(key), TestAccounts.employee(initiator), new RoundNumber(round),
                List.of(new RoutingFlag(flag)), List.of(new ApprovalField(TimesheetApprovalField.PROJECT_MANAGER.fieldName(), List.of(TestAccounts.employee(manager).toString()))),
                new ContentHash("hash"), new ApprovalSummary("summary of " + key));
    }

    @Test
    @DisplayName("D-1a-78 an approver the initiator can replace (named on the document but not a person) ends the round RETURNED by the system with the reason; the next round starts normally")
    void aFixableRefusalEndsAsReturnedBySystem(Scenario scenario) {
        long unit = newId();
        long initiator = newId();
        long gone = newId();
        long manager = newId();
        organisation.unit(unit, null, null).person(initiator, unit, null).person(manager, unit, null);
        String key = newKey("refused");

        scenario.publish(request(key, initiator, gone, 1, "projectHours")).andWaitForEventOfType(ApprovalCompleted.class)
                .matching(e -> e.document().equals(document(key))).toArriveAndVerify(e -> {
                    assertThat(e.outcome()).isEqualTo(ApprovalOutcome.RETURNED);
                    assertThat(e.returnSource()).isEqualTo(com.forcitis.one.platform.approval.api.ReturnSource.SYSTEM);
                    assertThat(e.round()).isEqualTo(new RoundNumber(1));
                    assertThat(e.initiator()).isEqualTo(TestAccounts.employee(initiator));
                    assertThat(e.reason().value()).contains("PLATFORM_APPROVAL_ASSIGNEE_INPUT_INVALID");
                });

        // the event is published inside the listener's transaction: it can arrive before the commit, so the committed state is awaited, not read once
        eventually(() -> assertThat(requestStatus(key)).isEqualTo("RETURNED"));
        assertThat(jdbc.queryForObject("select count(*) from platform_approval.approval_tasks where approval_request_id in (select id from platform_approval.approval_requests where document_id = cast(? as uuid))", Integer.class, key)).isZero();

        scenario.publish(request(key, initiator, manager, 2, "projectHours")).andWaitForEventOfType(com.forcitis.one.platform.approval.events.TaskAssigned.class)
                .matching(e -> e.document().equals(document(key))).toArrive();
        eventually(() -> assertThat(requestStatus(key)).isEqualTo("RUNNING"));
    }

    @Test
    @DisplayName("a fault of the set-up (a routing flag no node uses) is alerted and left for redelivery: nothing is returned to the initiator")
    void aSetUpFaultIsNotReturnedToTheInitiator(Scenario scenario) {
        long unit = newId();
        long initiator = newId();
        long manager = newId();
        organisation.unit(unit, null, null).person(initiator, unit, null).person(manager, unit, null);
        String key = newKey("setup");
        double before = meters.counter("forcitis.platform.approval.start.failed").count();

        scenario.publish(request(key, initiator, manager, 1, "noSuchFlag")).andWaitForStateChange(
                () -> meters.counter("forcitis.platform.approval.start.failed").count() > before ? Boolean.TRUE : null);

        assertThat(jdbc.queryForObject("select count(*) from platform_approval.approval_requests where document_id = cast(? as uuid)", Integer.class, key)).isZero();
    }

    @Test
    @DisplayName("a request for a round older than the current one is ignored and counted")
    void anOlderRoundIsIgnored(Scenario scenario) {
        long unit = newId();
        long initiator = newId();
        long manager = newId();
        organisation.unit(unit, null, null).person(initiator, unit, null).person(manager, unit, null);
        String key = newKey("older");
        start(key, initiator, manager);
        tx.executeWithoutResult(s -> approvals.withdraw(TimesheetApprovalProcess.KEY, document(key), TestAccounts.account(initiator), "again"));
        start(key, initiator, manager);
        double before = meters.counter("forcitis.platform.approval.start.ignored").count();

        scenario.publish(request(key, initiator, manager, 1, "projectHours")).andWaitForStateChange(
                () -> meters.counter("forcitis.platform.approval.start.ignored").count() > before ? Boolean.TRUE : null);

        assertThat(jdbc.queryForObject("select current_round from platform_approval.approval_requests where document_id = cast(? as uuid)", Integer.class, key))
                .isEqualTo(2);
    }
}
