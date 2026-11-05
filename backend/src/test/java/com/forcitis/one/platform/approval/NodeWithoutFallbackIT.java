package com.forcitis.one.platform.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.cost.timesheet.api.TimesheetApprovalField;
import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.platform.approval.api.ApprovalError;
import com.forcitis.one.platform.approval.api.ApprovalField;
import com.forcitis.one.platform.approval.api.ApprovalStartRequest;
import com.forcitis.one.platform.approval.api.ProcessKey;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** #1436: a node marked noFallback is not handed to a supervisor or an administrator when the person it names is only the initiator. */
class NodeWithoutFallbackIT extends ApprovalIntegrationSupport {

    static final ProcessKey DEMO = ProcessKey.of("cost_demo_review");

    @Test
    @DisplayName("#1436 a start whose only node names the initiator and takes no fallback is refused with NO_ASSIGNEE_FOUND and leaves no instance")
    void nobodyLeftRefusesTheStep() {
        String bpmn = repository.createDeployment().addClasspathResource("nofallback/cost_demo_review.bpmn20.xml").deploy().getId();
        try {
            long initiator = newId();
            ApprovalStartRequest base = startRequest(newKey("nofallback"), TestAccounts.employee(initiator), Set.of(),
                    List.of(new ApprovalField(TimesheetApprovalField.PROJECT_MANAGER.fieldName(), List.of(TestAccounts.employee(initiator).toString()))), 1);
            ApprovalStartRequest request = new ApprovalStartRequest(DEMO, base.document(), base.legalEntityId(), base.initiator(), base.round(), UUID.randomUUID(),
                    Set.of(), base.formFields(), base.contentHash(), base.summary());

            assertThatThrownBy(() -> tx.execute(status -> approvals.start(request))).isInstanceOfSatisfying(DomainException.class,
                    e -> assertThat(e.errorCode()).isEqualTo(ApprovalError.PLATFORM_APPROVAL_NO_ASSIGNEE_FOUND));
            assertThat(runtime.createProcessInstanceQuery().processDefinitionKey(DEMO.key()).count()).isZero();
        } finally {
            repository.deleteDeployment(bpmn, true);
        }
    }
}
