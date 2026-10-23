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
        ApprovalStartRequest base = startRequest(key, TestAccounts.employee(newId()), flags,
                List.of(new ApprovalField(TimesheetApprovalField.PROJECT_MANAGER.fieldName(), List.of(TestAccounts.employee(newId()).toString()))), 1);
        return new ApprovalStartRequest(process, base.document(), base.legalEntityId(), base.initiator(), base.round(), UUID.randomUUID(), flags,
                base.formFields(), base.contentHash(), base.summary());
    }

    @Test
    @DisplayName("the name and the routing flag rule of a process come from its definition, and a demo process starts with no platform change")
    void aDemoProcessStartsFromItsFileAlone() {
        String bpmn = repository.createDeployment().addClasspathResource("newprocess/cost_demo_review.bpmn20.xml").deploy().getId();
        try {
            assertThat(engine.processNameOf(DEMO)).isEqualTo("演示复核");
            assertThat(engine.routingFlagRequiredOf(DEMO)).isFalse();
            assertThat(engine.processNameOf(TimesheetApprovalProcess.KEY)).isEqualTo("工时审批");
            assertThat(engine.routingFlagRequiredOf(TimesheetApprovalProcess.KEY)).isTrue();

            String key = newKey("demo");
            ApprovalInstanceId instance = tx.execute(status -> approvals.start(demoStart(key, DEMO, Set.of())));

            assertThat(approvals.isFinished(instance)).isFalse();
            assertThat(openTask(instance).getTaskDefinitionKey()).isEqualTo("review");
        } finally {
            repository.deleteDeployment(bpmn, true);
        }
    }

    @Test
    @DisplayName("a process whose definition says it routes by flags still refuses a start with none, as the platform no longer knows which process that is")
    void flagRuleIsReadFromTheDefinition() {
        assertThatThrownBy(() -> tx.execute(status -> approvals.start(demoStart(newKey("noflag"), TimesheetApprovalProcess.KEY, Set.of()))))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(ApprovalError.PLATFORM_APPROVAL_NO_ROUTE));
    }
}
