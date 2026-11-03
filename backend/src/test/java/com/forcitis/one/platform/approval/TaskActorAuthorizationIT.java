package com.forcitis.one.platform.approval;

import java.util.UUID;
import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.cost.timesheet.api.TimesheetApprovalField;
import com.forcitis.one.cost.timesheet.api.TimesheetRoutingFlag;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static com.forcitis.one.platform.approval.OrdinarySessions.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.application.ClaimTask;
import com.forcitis.one.platform.approval.application.DecideTasks;
import com.forcitis.one.platform.approval.application.DecisionOutcome;
import com.forcitis.one.platform.approval.application.ReturnToInitiator;
import com.forcitis.one.platform.approval.application.TaskDecision;
import com.forcitis.one.platform.identity.api.Permission;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;

/** Flowable's own complete() checks nothing, so a non-handler must get 404, the same as for a task that is not there, with task, candidates, assignee, status and published results unchanged. */
class TaskActorAuthorizationIT extends ApprovalIntegrationSupport {

    @org.junit.jupiter.api.BeforeEach
    void candidateGroupNodes() {
        deployVariant("groupnode");
    }

    @Autowired
    ClaimTask claimTask;

    @Autowired
    DecideTasks decideTasks;

    @Autowired
    ReturnToInitiator returnToInitiator;

    static RequestPostProcessor approver(long id) {
        return user(TestAccounts.account(id).value().toString()).authorities(com.forcitis.one.platform.approval.OrdinarySessions.with(Permission.Codes.PLATFORM_APPROVAL_TASK_APPROVE));
    }

    static RequestPostProcessor reader(long id) {
        return user(TestAccounts.account(id).value().toString()).authorities(com.forcitis.one.platform.approval.OrdinarySessions.with(Permission.Codes.PLATFORM_APPROVAL_TASK_READ));
    }

    record Scene(String key, ApprovalInstanceId instance, ApprovalTaskId task, long initiator, long candidate, long otherCandidate,
                 long stranger) {
    }

    Scene scene() {
        long initiator = newId();
        long candidate = newId();
        long other = newId();
        long stranger = newId();
        organisation.person(initiator, 1, null).person(candidate, 1, null).person(other, 1, null).person(stranger, 1, null);
        String key = newKey("actor");
        ApprovalInstanceId instance = tx.execute(s -> approvals.start(startRequest(key, TestAccounts.employee(initiator), java.util.Set.of(TimesheetRoutingFlag.PROJECT_HOURS.flagName()),
                List.of(new com.forcitis.one.platform.approval.api.ApprovalField(TimesheetApprovalField.PROJECT_MANAGER.fieldName(),
                        List.of(TestAccounts.employee(candidate).toString(), TestAccounts.employee(other).toString()))))));
        return new Scene(key, instance, openTaskId(instance), initiator, candidate, other, stranger);
    }

    record Snapshot(String taskId, String assignee, List<String> candidates, String status, int results) {
    }

    Snapshot snapshot(Scene scene) {
        var task = taskService.createTaskQuery().processInstanceId(scene.instance().value().toString()).singleResult();
        return new Snapshot(task.getId(), task.getAssignee(),
                taskService.getIdentityLinksForTask(task.getId()).stream().map(l -> l.getUserId() + ":" + l.getType()).sorted().toList(),
                requestStatus(scene.key()), (int) events.resulted().stream().filter(e -> e.document().documentId().toString().equals(scene.key())).count());
    }

    void assertForbiddenAndUnchanged(Scene scene, Function<Scene, org.springframework.test.web.servlet.ResultActions> call) throws Exception {
        Snapshot before = snapshot(scene);
        call.apply(scene);
        assertThat(snapshot(scene)).isEqualTo(before);
    }

    @Test
    @DisplayName("I-7 claim / decide / return by a stranger or by the initiator -> 404 over HTTP, task untouched")
    void nonHandlersAreForbiddenOverHttp() throws Exception {
        Scene scene = scene();
        String id = scene.task().value().toString();
        for (long outsider : new long[] {scene.stranger(), scene.initiator()}) {
            assertForbiddenAndUnchanged(scene, s -> perform(post("/api/v1/platform/approval-tasks/" + id + ":claim").with(csrf()).with(currentEtag())
                    .with(approver(outsider)), 404));
            assertForbiddenAndUnchanged(scene, s -> perform(post("/api/v1/platform/approval-tasks/" + id + ":decide").with(csrf()).with(currentEtag())
                    .with(approver(outsider)).contentType(MediaType.APPLICATION_JSON).content("{\"outcome\":\"APPROVE\",\"reason\":\"x\"}"), 404));
            assertForbiddenAndUnchanged(scene, s -> perform(post("/api/v1/platform/approval-tasks/" + id + ":return").with(csrf()).with(currentEtag())
                    .with(approver(outsider)).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}"), 404));
        }
        verify(audit, atLeastOnce()).recordIndependently(argThat(entry -> entry.action().equals("platform.approval.task.denied")
                && entry.actorId().equals(TestAccounts.account(scene.stranger())) && entry.subject().equals(AuditSubject.of(AuditSubjectKind.APPROVAL_TASK, UUID.fromString(id)))));
    }

    @Test
    @DisplayName("I-7 a refusal is still 404 when its audit entry cannot be written")
    void refusalSurvivesAFailingAudit() throws Exception {
        Scene scene = scene();
        String id = scene.task().value().toString();
        org.mockito.Mockito.doThrow(new IllegalStateException("audit store is down")).when(audit).recordIndependently(any());
        try {
            assertForbiddenAndUnchanged(scene, s -> perform(post("/api/v1/platform/approval-tasks/" + id + ":claim").with(csrf()).with(currentEtag())
                    .with(approver(scene.stranger())), 404));
        } finally {
            org.mockito.Mockito.reset(audit);
        }
    }

    @Test
    @DisplayName("I-7 after a claim only the claimer may act: the other candidate gets 404 for claim, decide and return")
    void afterAClaimOnlyTheClaimerActs() throws Exception {
        Scene scene = scene();
        String id = scene.task().value().toString();
        mvc.perform(post("/api/v1/platform/approval-tasks/" + id + ":claim").with(csrf()).with(currentEtag()).with(approver(scene.candidate())))
                .andExpect(status().isNoContent());
        assertThat(snapshot(scene).assignee()).isEqualTo(TestAccounts.account(scene.candidate()).value().toString());

        assertForbiddenAndUnchanged(scene, s -> perform(post("/api/v1/platform/approval-tasks/" + id + ":claim").with(csrf()).with(currentEtag())
                .with(approver(scene.otherCandidate())), 404));
        assertForbiddenAndUnchanged(scene, s -> perform(post("/api/v1/platform/approval-tasks/" + id + ":decide").with(csrf()).with(currentEtag())
                .with(approver(scene.otherCandidate())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"outcome\":\"APPROVE\",\"reason\":\"x\"}"), 404));
        assertForbiddenAndUnchanged(scene, s -> perform(post("/api/v1/platform/approval-tasks/" + id + ":return").with(csrf()).with(currentEtag())
                .with(approver(scene.otherCandidate())).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}"), 404));
        assertThat(snapshot(scene).assignee()).isEqualTo(TestAccounts.account(scene.candidate()).value().toString());
    }

    @Test
    @DisplayName("I-7 each operation also refuses the non-handler below the HTTP layer (use cases)")
    void useCasesRefuseNonHandlers() {
        Scene scene = scene();
        Snapshot before = snapshot(scene);
        UserId stranger = TestAccounts.account(scene.stranger());
        assertThatThrownBy(() -> claimTask.execute(scene.task(), stranger, java.util.OptionalLong.empty())).isInstanceOfSatisfying(com.forcitis.one.kernel.DomainException.class, e -> org.assertj.core.api.Assertions.assertThat(e.errorCode()).isEqualTo(com.forcitis.one.platform.approval.api.ApprovalError.PLATFORM_APPROVAL_TASK_NOT_FOUND));
        assertThatThrownBy(() -> decideTasks.execute(stranger, List.of(new TaskDecision(scene.task(), DecisionOutcome.APPROVE, "x")), java.util.OptionalLong.empty()))
                .isInstanceOfSatisfying(com.forcitis.one.kernel.DomainException.class, e -> org.assertj.core.api.Assertions.assertThat(e.errorCode()).isEqualTo(com.forcitis.one.platform.approval.api.ApprovalError.PLATFORM_APPROVAL_TASK_NOT_FOUND));
        assertThatThrownBy(() -> returnToInitiator.execute(scene.task(), stranger, "x", java.util.OptionalLong.empty())).isInstanceOfSatisfying(com.forcitis.one.kernel.DomainException.class, e -> org.assertj.core.api.Assertions.assertThat(e.errorCode()).isEqualTo(com.forcitis.one.platform.approval.api.ApprovalError.PLATFORM_APPROVAL_TASK_NOT_FOUND));
        assertThat(snapshot(scene)).isEqualTo(before);
    }

    @Test
    @DisplayName("I-7 a batch with one task that is not the actor's is refused as a whole; the actor's own task stays open")
    void oneForeignTaskRefusesTheWholeBatch() {
        Scene mine = scene();
        Scene foreign = scene();
        UserId actor = TestAccounts.account(mine.candidate());
        assertThat(foreign.candidate()).isNotEqualTo(mine.candidate());
        Snapshot beforeMine = snapshot(mine);
        Snapshot beforeForeign = snapshot(foreign);
        assertThatThrownBy(() -> decideTasks.execute(actor, List.of(
                new TaskDecision(mine.task(), DecisionOutcome.APPROVE, "ok"), new TaskDecision(foreign.task(), DecisionOutcome.APPROVE, "ok")), java.util.OptionalLong.empty()))
                .isInstanceOfSatisfying(com.forcitis.one.kernel.DomainException.class, e -> org.assertj.core.api.Assertions.assertThat(e.errorCode()).isEqualTo(com.forcitis.one.platform.approval.api.ApprovalError.PLATFORM_APPROVAL_TASK_NOT_FOUND));
        assertThat(snapshot(mine)).isEqualTo(beforeMine);
        assertThat(snapshot(foreign)).isEqualTo(beforeForeign);
    }

    @Test
    @DisplayName("I-7 the actor is the session user, never a field of the request: a forged userId in the body changes nothing")
    void bodyCannotNameTheActor() throws Exception {
        Scene scene = scene();
        String id = scene.task().value().toString();
        Snapshot before = snapshot(scene);
        mvc.perform(post("/api/v1/platform/approval-tasks/" + id + ":decide").with(csrf()).with(currentEtag()).with(approver(scene.stranger()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"APPROVE\",\"reason\":\"x\",\"userId\":" + scene.candidate() + ",\"approverId\":" + scene.candidate() + "}"))
                .andExpect(status().isNotFound());
        assertThat(snapshot(scene)).isEqualTo(before);
    }

    @org.junit.jupiter.api.RepeatedTest(8)
    @DisplayName("RULE-276/I-7 two candidates claiming at the same moment: exactly one wins, the other is refused (404 or 409), never a server error")
    void concurrentClaimsHaveOneWinner() throws Exception {
        Scene scene = scene();
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
        List<java.util.concurrent.Future<String>> outcomes = new java.util.ArrayList<>();
        for (long candidate : new long[] {scene.candidate(), scene.otherCandidate()}) {
            outcomes.add(pool.submit(() -> {
                go.await();
                try {
                    claimTask.execute(scene.task(), TestAccounts.account(candidate), java.util.OptionalLong.empty());
                    return "won";
                } catch (com.forcitis.one.kernel.DomainException e) {
                    return e.errorCode().name();
                }
            }));
        }
        go.countDown();
        List<String> results = new java.util.ArrayList<>();
        for (var outcome : outcomes) {
            results.add(outcome.get(30, java.util.concurrent.TimeUnit.SECONDS));
        }
        pool.shutdown();
        assertThat(results).containsOnlyOnce("won");
        assertThat(results.stream().filter(r -> !r.equals("won"))).allMatch(r -> r.equals("PLATFORM_APPROVAL_TASK_NOT_FOUND") || r.equals("PLATFORM_APPROVAL_TASK_ALREADY_CLAIMED"));
        assertThat(snapshot(scene).assignee()).isIn(TestAccounts.account(scene.candidate()).value().toString(), TestAccounts.account(scene.otherCandidate()).value().toString());
    }

    @org.junit.jupiter.api.RepeatedTest(5)
    @DisplayName("RULE-280/I-7 two candidates deciding the same task at the same moment: one decision is applied, the other is refused")
    void concurrentDecisionsHaveOneWinner() throws Exception {
        Scene scene = scene();
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
        List<java.util.concurrent.Future<String>> outcomes = new java.util.ArrayList<>();
        for (long candidate : new long[] {scene.candidate(), scene.otherCandidate()}) {
            outcomes.add(pool.submit(() -> {
                go.await();
                try {
                    decideTasks.execute(TestAccounts.account(candidate), List.of(new TaskDecision(scene.task(), DecisionOutcome.APPROVE, "ok")), java.util.OptionalLong.empty());
                    return "won";
                } catch (com.forcitis.one.kernel.DomainException e) {
                    return e.errorCode().name();
                }
            }));
        }
        go.countDown();
        List<String> results = new java.util.ArrayList<>();
        for (var outcome : outcomes) {
            results.add(outcome.get(30, java.util.concurrent.TimeUnit.SECONDS));
        }
        pool.shutdown();
        assertThat(results).containsOnlyOnce("won");
        assertThat(results.stream().filter(r -> !r.equals("won"))).allMatch(r -> r.equals("PLATFORM_APPROVAL_TASK_NOT_FOUND")
                || r.equals("PLATFORM_APPROVAL_TASK_ALREADY_CLAIMED"));
        assertThat(approvals.isFinished(scene.instance())).isTrue();
        assertThat(events.resulted().stream().filter(e -> e.document().documentId().toString().equals(scene.key())).count()).isLessThanOrEqualTo(1);
    }

    @Test
    @DisplayName("D-1a-179 acting on a task needs to be its handler or candidate and no permission code; the inbox needs a signed-in session only")
    void noPermissionCodeIsNeededToAct() throws Exception {
        Scene scene = scene();
        String id = scene.task().value().toString();
        mvc.perform(get("/api/v1/platform/approval-tasks").with(approver(scene.candidate()))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/platform/approval-tasks").with(reader(scene.candidate()))).andExpect(status().isOk());
        mvc.perform(post("/api/v1/platform/approval-tasks/" + id + ":claim").with(csrf()).with(currentEtag()).with(reader(scene.candidate())))
                .andExpect(status().isNoContent());
        assertThat(snapshot(scene).assignee()).isEqualTo(TestAccounts.account(scene.candidate()).value().toString());
    }

    @Test
    @DisplayName("RULE-310 task details are visible to participants only; a stranger is told the task does not exist")
    void detailsAreForParticipantsOnly() throws Exception {
        Scene scene = scene();
        String id = scene.task().value().toString();
        mvc.perform(get("/api/v1/platform/approval-tasks/" + id).with(reader(scene.stranger()))).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PLATFORM_APPROVAL_TASK_NOT_FOUND"));
        mvc.perform(get("/api/v1/platform/approval-tasks/" + id).with(reader(scene.candidate()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.canAct").value(true)).andExpect(jsonPath("$.initiator").value(TestAccounts.employee(scene.initiator()).toString()));
        mvc.perform(get("/api/v1/platform/approval-tasks/" + id).with(reader(scene.initiator()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.canAct").value(false));
    }

    @Test
    @DisplayName("an unknown task is 404 for every operation, not 403 and not a server error")
    void unknownTaskIsNotFound() throws Exception {
        mvc.perform(post("/api/v1/platform/approval-tasks/" + com.forcitis.one.TestIds.uuid(404) + ":claim").with(csrf()).with(currentEtag()).with(approver(newId())))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/platform/approval-tasks/" + com.forcitis.one.TestIds.uuid(404) + ":decide").with(csrf()).with(currentEtag()).with(approver(newId()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"outcome\":\"APPROVE\"}")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("M-12 somebody who may not touch a task and a task that does not exist get the very same answer: status, content type, error code, title and detail")
    void strangerAndUnknownTaskAreIndistinguishable() throws Exception {
        Scene scene = scene();
        String stranger = TestAccounts.account(scene.stranger()).value().toString();
        for (String action : List.of(":claim", ":release", ":decide", ":return", ":transfer")) {
            String body = "{\"outcome\":\"APPROVE\",\"reason\":\"x\",\"assigneeId\":\"" + TestAccounts.employee(scene.otherCandidate()) + "\"}";
            var foreign = mvc.perform(post("/api/v1/platform/approval-tasks/" + scene.task().value() + action).with(csrf()).with(currentEtag())
                    .with(approver(scene.stranger())).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse();
            var missing = mvc.perform(post("/api/v1/platform/approval-tasks/" + com.forcitis.one.TestIds.uuid(404) + "" + action).with(csrf()).with(currentEtag())
                    .with(approver(scene.stranger())).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse();
            assertThat(foreign.getStatus()).as(action).isEqualTo(404).isEqualTo(missing.getStatus());
            assertThat(foreign.getContentType()).as(action).isEqualTo(missing.getContentType());
            var f = com.jayway.jsonpath.JsonPath.parse(foreign.getContentAsString());
            var m = com.jayway.jsonpath.JsonPath.parse(missing.getContentAsString());
            for (String field : List.of("$.errorCode", "$.title", "$.detail", "$.status", "$.type")) {
                assertThat((Object) f.read(field)).as(action + " " + field).isEqualTo(m.read(field));
            }
        }
        mvc.perform(get("/api/v1/platform/approval-tasks/" + scene.task().value()).with(user(stranger))).andExpect(status().isNotFound());
    }

    private org.springframework.test.web.servlet.ResultActions perform(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            int expected) {
        try {
            return mvc.perform(request).andExpect(status().is(expected));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
