package com.forcitis.one.platform.approval;

import com.forcitis.one.cost.timesheet.api.TimesheetApprovalField;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalField;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalRequest;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.application.DecideTasks;
import com.forcitis.one.platform.approval.application.DecisionOutcome;
import com.forcitis.one.platform.approval.application.TaskDecision;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * I-5 (SEC-117, the 1a part): an approval is started only by its owner's module with the current user as initiator; another
 * user can neither start it again nor resubmit it, and approval offers no HTTP start. The business-document half (a module
 * verifying that the caller owns the document) arrives with the first module that connects documents (1b quotation).
 */
class ApprovalStartOwnershipIT extends ApprovalIntegrationSupport {

    @Autowired
    DecideTasks decideTasks;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping handlerMapping;

    Map<String, Object> variablesOf(ApprovalInstanceId instance) {
        return new TreeMap<>(runtime.getVariables(instance.value()));
    }

    @Test
    @DisplayName("I-5 user A starting user B's document is refused with AccessDenied; B's instance, variables and rounds are unchanged")
    void anotherUserCannotStartSomeoneElsesApproval() {
        long userA = newId();
        long userB = newId();
        long manager = newId();
        organisation.person(userA, 1, null).person(userB, 1, null).person(manager, 1, null);
        String documentOfB = newKey("ownership");
        ApprovalInstanceId instanceOfB = start(documentOfB, userB, manager);
        Map<String, Object> before = variablesOf(instanceOfB);

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> approvals.start(new ApprovalRequest(
                ProcessKey.COST_TIMESHEET_APPROVAL, documentOfB, new UserId(userA), Set.of("needs-seal"),
                List.of(new ApprovalField(TimesheetApprovalField.PROJECT_MANAGER.fieldName(), List.of(String.valueOf(userA))))))))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(variablesOf(instanceOfB)).isEqualTo(before);
        assertThat(instancesOf(documentOfB)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select initiator_id from platform_approval.approval_requests where business_key = ?",
                Long.class, documentOfB)).isEqualTo(userB);
        assertThat(jdbc.queryForObject("select count(*) from platform_approval.start_rounds where business_key = ?", Integer.class,
                documentOfB)).isEqualTo(1);
        assertThat(requestStatus(documentOfB)).isEqualTo("RUNNING");
        verify(audit, atLeastOnce()).recordIndependently(argThat(entry -> entry.action().equals("platform.approval.start.denied")
                && entry.actorId().equals(new UserId(userA))));
    }

    @Test
    @DisplayName("I-5 and RULE-287 after B's round was returned, A cannot open the next round; B does, and it is round 2 with a new instance")
    void anotherUserCannotOpenTheNextRound() {
        long userA = newId();
        long userB = newId();
        long manager = newId();
        organisation.person(userA, 1, null).person(userB, 1, null).person(manager, 1, null);
        String documentOfB = newKey("ownership");
        ApprovalInstanceId round1 = start(documentOfB, userB, manager);
        decideTasks.execute(new UserId(manager), List.of(new TaskDecision(openTaskId(round1), DecisionOutcome.REJECT, "fix it")));
        assertThat(requestStatus(documentOfB)).isEqualTo("RETURNED");
        assertThat(taskService.createTaskQuery().processInstanceId(round1.value()).count()).as("nothing is parked at the initiator").isZero();
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> approvals.start(new ApprovalRequest(ProcessKey.COST_TIMESHEET_APPROVAL,
                documentOfB, new UserId(userA), Set.of(), List.of()))))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(requestStatus(documentOfB)).isEqualTo("RETURNED");
        assertThat(jdbc.queryForObject("select count(*) from platform_approval.start_rounds where business_key = ?", Integer.class,
                documentOfB)).isEqualTo(1);
        verify(audit, atLeastOnce()).recordIndependently(argThat(entry -> entry.action().equals("platform.approval.start.denied")
                && entry.actorId().equals(new UserId(userA))));

        // the owner's module starts again through start: round 2 with a NEW instance, the fresh values
        ApprovalInstanceId round2 = start(documentOfB, userB, manager);
        assertThat(round2).isNotEqualTo(round1);
        assertThat(requestStatus(documentOfB)).isEqualTo("RUNNING");
        assertThat(openTask(round2).getTaskDefinitionKey()).isEqualTo("projectManagerReview");
        assertThat(jdbc.queryForObject("select current_round from platform_approval.approval_requests where business_key = ?",
                Integer.class, documentOfB)).isEqualTo(2);
        assertThat(instancesOf(documentOfB)).as("round 1 has ended, only round 2 runs").isEqualTo(1);
        // a repeated call of the same start returns the running round instead of opening round 3
        assertThat(start(documentOfB, userB, manager)).isEqualTo(round2);
        assertThat(jdbc.queryForObject("select count(*) from platform_approval.start_rounds where business_key = ?", Integer.class,
                documentOfB)).isEqualTo(2);
    }

    @Test
    @DisplayName("I-5 approval exposes no HTTP start: its routes are the inbox, the detail and the three task actions only")
    void thereIsNoHttpStart() {
        Map<String, List<String>> approvalRoutes = new TreeMap<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
            HandlerMethod handler = entry.getValue();
            if (!handler.getBeanType().getPackageName().startsWith("com.forcitis.one.platform.approval")) {
                continue;
            }
            for (String pattern : entry.getKey().getPatternValues()) {
                approvalRoutes.computeIfAbsent(pattern, p -> new java.util.ArrayList<>())
                        .add(entry.getKey().getMethodsCondition().getMethods() + " " + handler.getMethod().getName());
            }
            // no request body may carry a document id, process type or initiator
            for (var parameter : handler.getMethodParameters()) {
                if (parameter.hasParameterAnnotation(org.springframework.web.bind.annotation.RequestBody.class)) {
                    for (var component : parameter.getParameterType().getRecordComponents()) {
                        assertThat(component.getName()).doesNotContainIgnoringCase("businessKey").doesNotContainIgnoringCase("processKey")
                                .doesNotContainIgnoringCase("initiator").doesNotContainIgnoringCase("userId")
                                .doesNotContainIgnoringCase("creator").doesNotContainIgnoringCase("documentId");
                    }
                }
            }
        }
        assertThat(approvalRoutes.keySet()).containsExactlyInAnyOrder(
                "/api/{version}/platform/approval-tasks", "/api/{version}/platform/approval-tasks/{taskId}",
                "/api/{version}/platform/approval-tasks/{taskId}:claim", "/api/{version}/platform/approval-tasks/{taskId}:decide",
                "/api/{version}/platform/approval-tasks/{taskId}:return");
    }

    int instancesOf(String businessKey) {
        return (int) runtime.createProcessInstanceQuery().processInstanceBusinessKey(businessKey).count();
    }
}
