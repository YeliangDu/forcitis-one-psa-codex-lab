package com.forcitis.one.platform.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.cost.timesheet.api.TimesheetApprovalField;
import com.forcitis.one.cost.timesheet.api.TimesheetApprovalProcess;
import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.platform.approval.api.ApprovalError;
import com.forcitis.one.platform.approval.api.ApprovalField;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalStartRequest;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.api.ProcessName;
import com.forcitis.one.platform.approval.api.RetractionResult;
import com.forcitis.one.platform.approval.domain.ApprovalEngine;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** REG-4 (ADR-0066 decision 4, item 6): a new process is a BPMN file and the owning context's ProcessKey constant; the platform module is not touched. */
class NewProcessWithoutPlatformChangeIT extends ApprovalIntegrationSupport {

    static final ProcessKey DEMO = ProcessKey.of("cost_demo_review");

    @Autowired
    ApprovalEngine engine;

    private ApprovalStartRequest demoStart(String key, ProcessKey process, Set<String> flags) {
        return demoStart(key, newId(), process, flags);
    }

    private ApprovalStartRequest demoStart(String key, long initiator, ProcessKey process, Set<String> flags) {
        ApprovalStartRequest base = startRequest(key, TestAccounts.employee(initiator), flags,
                List.of(new ApprovalField(TimesheetApprovalField.PROJECT_MANAGER.fieldName(), List.of(TestAccounts.employee(newId()).toString()))), 1);
        return new ApprovalStartRequest(process, base.document(), base.legalEntityId(), base.initiator(), base.round(), UUID.randomUUID(), flags,
                base.formFields(), base.contentHash(), base.summary());
    }

    @Test
    @DisplayName("the name and the routing flag rule of a process come from its definition, and a demo process starts with no platform change")
    void aDemoProcessStartsFromItsFileAlone() {
        String bpmn = repository.createDeployment().addClasspathResource("newprocess/cost_demo_review.bpmn20.xml").deploy().getId();
        String key = newKey("demo");
        long initiator = newId();
        ApprovalInstanceId instance = null;
        try {
            assertThat(engine.processNameOf(DEMO)).isEqualTo(new ProcessName("演示复核"));
            assertThat(engine.routingFlagRequiredOf(DEMO)).isFalse();
            assertThat(engine.processNameOf(TimesheetApprovalProcess.KEY)).isEqualTo(new ProcessName("工时审批"));
            assertThat(engine.routingFlagRequiredOf(TimesheetApprovalProcess.KEY)).isTrue();

            instance = tx.execute(status -> approvals.start(demoStart(key, initiator, DEMO, Set.of())));

            assertThat(approvals.isFinished(instance)).isFalse();
            assertThat(openTask(instance).getTaskDefinitionKey()).isEqualTo("review");
        } finally {
            if (instance != null) {
                endDemoApproval(key, initiator);
            }
            repository.deleteDeployment(bpmn, true);
        }
    }

    /**
     * Removing the demo deployment removes the engine's instance but not the platform's rows of the approval, and two of their tables are
     * append-only: left running, they were an open task of a process no longer deployed, seen by every later test of this JVM's database.
     * The initiator cancels it instead, and the deployment goes only once the listeners of the result, which read the definition, are done.
     */
    private void endDemoApproval(String key, long initiator) {
        RetractionResult cancelled = tx.execute(status -> approvals.cancel(DEMO, document(key), TestAccounts.account(initiator), "test cleanup"));
        assertThat(cancelled).isEqualTo(RetractionResult.DONE);
        eventually(() -> assertThat(jdbc.queryForObject("select count(*) from platform_integration.event_publication"
                + " where completion_date is null and serialized_event like ?", Integer.class, "%" + key + "%")).isZero());
    }

    @Test
    @DisplayName("a process whose definition says it routes by flags still refuses a start with none, as the platform no longer knows which process that is")
    void flagRuleIsReadFromTheDefinition() {
        assertThatThrownBy(() -> tx.execute(status -> approvals.start(demoStart(newKey("noflag"), TimesheetApprovalProcess.KEY, Set.of()))))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(ApprovalError.PLATFORM_APPROVAL_NO_ROUTE));
    }
}
