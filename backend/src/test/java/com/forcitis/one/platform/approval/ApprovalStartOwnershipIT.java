package com.forcitis.one.platform.approval;

import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.cost.timesheet.api.TimesheetApprovalField;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalField;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalStartRequest;
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

class ApprovalStartOwnershipIT extends ApprovalIntegrationSupport {

    @Autowired
    DecideTasks decideTasks;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping handlerMapping;

    Map<String, Object> variablesOf(ApprovalInstanceId instance) {
        return new TreeMap<>(runtime.getVariables(instance.value().toString()));
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

        actAs(userA);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> approvals.start(startRequest(documentOfB, TestAccounts.employee(userA), Set.of("needs-seal"),
                List.of(new ApprovalField(TimesheetApprovalField.PROJECT_MANAGER.fieldName(), List.of(TestAccounts.employee(userA).toString())))))))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(variablesOf(instanceOfB)).isEqualTo(before);
        assertThat(instancesOf(documentOfB)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select initiator_id from platform_approval.approval_requests where document_id = cast(? as uuid)",
                java.util.UUID.class, documentOfB)).isEqualTo(TestAccounts.employee(userB).value());
        assertThat(jdbc.queryForObject("select count(*) from platform_approval.start_rounds where approval_request_id = (select id from platform_approval.approval_requests where document_id = cast(? as uuid))", Integer.class,
                documentOfB)).isEqualTo(1);
        assertThat(requestStatus(documentOfB)).isEqualTo("RUNNING");
        verify(audit, atLeastOnce()).recordIndependently(argThat(entry -> entry.action().equals("platform.approval.start.denied")
                && entry.actorId().equals(TestAccounts.account(userA))));
    }

    // RULE-497
    @Test
    @DisplayName("P2 recheck L-4 a refused start is audited with the signed-in caller, not with the initiator the request names")
    void theRefusalNamesTheCallerNotTheInitiatorInTheRequest() {
        long userA = newId();
        long userB = newId();
        long caller = newId();
        long manager = newId();
        organisation.person(userA, 1, null).person(userB, 1, null).person(caller, 1, null).person(manager, 1, null);
        String documentOfB = newKey("ownership");
        start(documentOfB, userB, manager);

        actAs(caller);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> approvals.start(startRequest(documentOfB, TestAccounts.employee(userA), Set.of(), List.of()))))
                .isInstanceOf(AccessDeniedException.class);

        verify(audit, atLeastOnce()).recordIndependently(argThat(entry -> entry.action().equals("platform.approval.start.denied")
                && entry.actorId().equals(TestAccounts.account(caller))));
        verify(audit, org.mockito.Mockito.never()).recordIndependently(argThat(entry -> entry.action().equals("platform.approval.start.denied")
                && TestAccounts.account(userA).equals(entry.actorId())));
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
        decideTasks.execute(TestAccounts.account(manager), List.of(new TaskDecision(openTaskId(round1), DecisionOutcome.REJECT, "fix it")), java.util.OptionalLong.empty());
        assertThat(requestStatus(documentOfB)).isEqualTo("RETURNED");
        assertThat(taskService.createTaskQuery().processInstanceId(round1.value().toString()).count()).as("nothing is parked at the initiator").isZero();
        actAs(userA);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> approvals.start(startRequest(documentOfB, TestAccounts.employee(userA), Set.of(), List.of()))))
                .isInstanceOf(AccessDeniedException.class);
        org.springframework.security.core.context.SecurityContextHolder.clearContext();

        assertThat(requestStatus(documentOfB)).isEqualTo("RETURNED");
        assertThat(jdbc.queryForObject("select count(*) from platform_approval.start_rounds where approval_request_id = (select id from platform_approval.approval_requests where document_id = cast(? as uuid))", Integer.class,
                documentOfB)).isEqualTo(1);
        verify(audit, atLeastOnce()).recordIndependently(argThat(entry -> entry.action().equals("platform.approval.start.denied")
                && entry.actorId().equals(TestAccounts.account(userA))));

        ApprovalInstanceId round2 = start(documentOfB, userB, manager);
        assertThat(round2).isNotEqualTo(round1);
        assertThat(requestStatus(documentOfB)).isEqualTo("RUNNING");
        assertThat(openTask(round2).getTaskDefinitionKey()).isEqualTo("projectManagerReview");
        assertThat(jdbc.queryForObject("select current_round from platform_approval.approval_requests where document_id = cast(? as uuid)",
                Integer.class, documentOfB)).isEqualTo(2);
        assertThat(instancesOf(documentOfB)).as("round 1 has ended, only round 2 runs").isEqualTo(1);
        assertThat(start(documentOfB, userB, manager)).isEqualTo(round2);
        assertThat(jdbc.queryForObject("select count(*) from platform_approval.start_rounds where approval_request_id = (select id from platform_approval.approval_requests where document_id = cast(? as uuid))", Integer.class,
                documentOfB)).isEqualTo(2);
    }

    @Test
    @DisplayName("I-5 approval exposes no HTTP start: its routes are the inbox, the detail and the task actions only")
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
                "/api/v1/platform/approval-tasks", "/api/v1/platform/approval-tasks/{taskId}",
                "/api/v1/platform/approval-tasks/{taskId}:claim", "/api/v1/platform/approval-tasks/{taskId}:decide",
                "/api/v1/platform/approval-tasks/{taskId}:return", "/api/v1/platform/approval-tasks/{taskId}:transfer",
                "/api/v1/platform/approval-tasks/{taskId}:release", "/api/v1/platform/approval-tasks/{taskId}:reassign",
                "/api/v1/platform/approval-task-records", "/api/v1/platform/approval-task-overviews");
    }

    int instancesOf(String businessKey) {
        return (int) runtime.createProcessInstanceQuery().processInstanceBusinessKey(businessKey).count();
    }
}
