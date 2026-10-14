package com.forcitis.one.platform.approval;

import com.forcitis.one.cost.timesheet.api.TimesheetApprovalField;
import com.forcitis.one.cost.timesheet.api.TimesheetRoutingFlag;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Flowable's own complete() checks nothing, so a non-handler must get 403 with task, candidates, assignee, status and published results unchanged. */
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
        return user(String.valueOf(id)).authorities(() -> Permission.Codes.PLATFORM_APPROVAL_TASK_APPROVE);
    }

    static RequestPostProcessor reader(long id) {
        return user(String.valueOf(id)).authorities(() -> Permission.Codes.PLATFORM_APPROVAL_TASK_READ);
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
        ApprovalInstanceId instance = tx.execute(s -> approvals.start(startRequest(key, new UserId(initiator), java.util.Set.of(TimesheetRoutingFlag.PROJECT_HOURS.flagName()),
                List.of(new com.forcitis.one.platform.approval.api.ApprovalField(TimesheetApprovalField.PROJECT_MANAGER.fieldName(),
                        List.of(String.valueOf(candidate), String.valueOf(other)))))));
        return new Scene(key, instance, openTaskId(instance), initiator, candidate, other, stranger);
    }

    record Snapshot(String taskId, String assignee, List<String> candidates, String status, int results) {
    }

    Snapshot snapshot(Scene scene) {
        var task = taskService.createTaskQuery().processInstanceId(scene.instance().value()).singleResult();
        return new Snapshot(task.getId(), task.getAssignee(),
                taskService.getIdentityLinksForTask(task.getId()).stream().map(l -> l.getUserId() + ":" + l.getType()).sorted().toList(),
                requestStatus(scene.key()), (int) events.resulted().stream().filter(e -> e.document().documentId().value().equals(scene.key())).count());
    }

    void assertForbiddenAndUnchanged(Scene scene, Function<Scene, org.springframework.test.web.servlet.ResultActions> call) throws Exception {
        Snapshot before = snapshot(scene);
        call.apply(scene);
        assertThat(snapshot(scene)).isEqualTo(before);
    }

    @Test
    @DisplayName("I-7 claim / decide / return by a stranger or by the initiator -> 403 over HTTP, task untouched")
    void nonHandlersAreForbiddenOverHttp() throws Exception {
        Scene scene = scene();
        String id = scene.task().value();
        for (long outsider : new long[] {scene.stranger(), scene.initiator()}) {
            assertForbiddenAndUnchanged(scene, s -> perform(post("/api/v1/platform/approval-tasks/" + id + ":claim").with(csrf()).with(currentEtag())
                    .with(approver(outsider)), 403));
            assertForbiddenAndUnchanged(scene, s -> perform(post("/api/v1/platform/approval-tasks/" + id + ":decide").with(csrf()).with(currentEtag())
                    .with(approver(outsider)).contentType(MediaType.APPLICATION_JSON).content("{\"outcome\":\"APPROVE\",\"reason\":\"x\"}"), 403));
            assertForbiddenAndUnchanged(scene, s -> perform(post("/api/v1/platform/approval-tasks/" + id + ":return").with(csrf()).with(currentEtag())
                    .with(approver(outsider)).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}"), 403));
        }
        verify(audit, atLeastOnce()).recordIndependently(argThat(entry -> entry.action().equals("platform.approval.task.denied")
                && entry.actorId().equals(new UserId(scene.stranger())) && entry.subjectKey().equals("approval-task:" + id)));
    }

    @Test
    @DisplayName("I-7 a refusal is still 403 when its audit entry cannot be written")
    void refusalSurvivesAFailingAudit() throws Exception {
        Scene scene = scene();
        String id = scene.task().value();
        org.mockito.Mockito.doThrow(new IllegalStateException("audit store is down")).when(audit).recordIndependently(any());
        try {
            assertForbiddenAndUnchanged(scene, s -> perform(post("/api/v1/platform/approval-tasks/" + id + ":claim").with(csrf()).with(currentEtag())
                    .with(approver(scene.stranger())), 403));
        } finally {
            org.mockito.Mockito.reset(audit);
        }
    }

    @Test
    @DisplayName("I-7 after a claim only the claimer may act: the other candidate gets 403 for claim, decide and return")
    void afterAClaimOnlyTheClaimerActs() throws Exception {
        Scene scene = scene();
        String id = scene.task().value();
        mvc.perform(post("/api/v1/platform/approval-tasks/" + id + ":claim").with(csrf()).with(currentEtag()).with(approver(scene.candidate())))
                .andExpect(status().isNoContent());
        assertThat(snapshot(scene).assignee()).isEqualTo(String.valueOf(scene.candidate()));

        assertForbiddenAndUnchanged(scene, s -> perform(post("/api/v1/platform/approval-tasks/" + id + ":claim").with(csrf()).with(currentEtag())
                .with(approver(scene.otherCandidate())), 403));
        assertForbiddenAndUnchanged(scene, s -> perform(post("/api/v1/platform/approval-tasks/" + id + ":decide").with(csrf()).with(currentEtag())
                .with(approver(scene.otherCandidate())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"outcome\":\"APPROVE\",\"reason\":\"x\"}"), 403));
        assertForbiddenAndUnchanged(scene, s -> perform(post("/api/v1/platform/approval-tasks/" + id + ":return").with(csrf()).with(currentEtag())
                .with(approver(scene.otherCandidate())).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}"), 403));
        assertThat(snapshot(scene).assignee()).isEqualTo(String.valueOf(scene.candidate()));
    }

    @Test
    @DisplayName("I-7 each operation also refuses the non-handler below the HTTP layer (use cases)")
    void useCasesRefuseNonHandlers() {
        Scene scene = scene();
        Snapshot before = snapshot(scene);
        UserId stranger = new UserId(scene.stranger());
        assertThatThrownBy(() -> claimTask.execute(scene.task(), stranger, java.util.OptionalLong.empty())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> decideTasks.execute(stranger, List.of(new TaskDecision(scene.task(), DecisionOutcome.APPROVE, "x")), java.util.OptionalLong.empty()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> returnToInitiator.execute(scene.task(), stranger, "x", java.util.OptionalLong.empty())).isInstanceOf(AccessDeniedException.class);
        assertThat(snapshot(scene)).isEqualTo(before);
    }

    @Test
    @DisplayName("I-7 a batch with one task that is not the actor's is refused as a whole; the actor's own task stays open")
    void oneForeignTaskRefusesTheWholeBatch() {
        Scene mine = scene();
        Scene foreign = scene();
        UserId actor = new UserId(mine.candidate());
        assertThat(foreign.candidate()).isNotEqualTo(mine.candidate());
        Snapshot beforeMine = snapshot(mine);
        Snapshot beforeForeign = snapshot(foreign);
        assertThatThrownBy(() -> decideTasks.execute(actor, List.of(
                new TaskDecision(mine.task(), DecisionOutcome.APPROVE, "ok"), new TaskDecision(foreign.task(), DecisionOutcome.APPROVE, "ok")), java.util.OptionalLong.empty()))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(snapshot(mine)).isEqualTo(beforeMine);
        assertThat(snapshot(foreign)).isEqualTo(beforeForeign);
    }

    @Test
    @DisplayName("I-7 the actor is the session user, never a field of the request: a forged userId in the body changes nothing")
    void bodyCannotNameTheActor() throws Exception {
        Scene scene = scene();
        String id = scene.task().value();
        Snapshot before = snapshot(scene);
        mvc.perform(post("/api/v1/platform/approval-tasks/" + id + ":decide").with(csrf()).with(currentEtag()).with(approver(scene.stranger()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"APPROVE\",\"reason\":\"x\",\"userId\":" + scene.candidate() + ",\"approverId\":" + scene.candidate() + "}"))
                .andExpect(status().isForbidden());
        assertThat(snapshot(scene)).isEqualTo(before);
    }

    @org.junit.jupiter.api.RepeatedTest(8)
    @DisplayName("RULE-276/I-7 two candidates claiming at the same moment: exactly one wins, the other is refused (403 or 409), never a server error")
    void concurrentClaimsHaveOneWinner() throws Exception {
        Scene scene = scene();
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
        List<java.util.concurrent.Future<String>> outcomes = new java.util.ArrayList<>();
        for (long candidate : new long[] {scene.candidate(), scene.otherCandidate()}) {
            outcomes.add(pool.submit(() -> {
                go.await();
                try {
                    claimTask.execute(scene.task(), new UserId(candidate), java.util.OptionalLong.empty());
                    return "won";
                } catch (AccessDeniedException e) {
                    return "denied";
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
        assertThat(results.stream().filter(r -> !r.equals("won"))).allMatch(r -> r.equals("denied") || r.equals("PLATFORM_APPROVAL_TASK_ALREADY_CLAIMED"));
        assertThat(snapshot(scene).assignee()).isIn(String.valueOf(scene.candidate()), String.valueOf(scene.otherCandidate()));
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
                    decideTasks.execute(new UserId(candidate), List.of(new TaskDecision(scene.task(), DecisionOutcome.APPROVE, "ok")), java.util.OptionalLong.empty());
                    return "won";
                } catch (AccessDeniedException e) {
                    return "denied";
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
        assertThat(results.stream().filter(r -> !r.equals("won"))).allMatch(r -> r.equals("denied")
                || r.equals("PLATFORM_APPROVAL_TASK_NOT_FOUND") || r.equals("PLATFORM_APPROVAL_TASK_ALREADY_CLAIMED"));
        assertThat(approvals.isFinished(scene.instance())).isTrue();
        assertThat(events.resulted().stream().filter(e -> e.document().documentId().value().equals(scene.key())).count()).isLessThanOrEqualTo(1);
    }

    @Test
    @DisplayName("D-1a-179 acting on a task needs to be its handler or candidate and no permission code; the inbox needs a signed-in session only")
    void noPermissionCodeIsNeededToAct() throws Exception {
        Scene scene = scene();
        String id = scene.task().value();
        mvc.perform(get("/api/v1/platform/approval-tasks").with(approver(scene.candidate()))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/platform/approval-tasks").with(reader(scene.candidate()))).andExpect(status().isOk());
        mvc.perform(post("/api/v1/platform/approval-tasks/" + id + ":claim").with(csrf()).with(currentEtag()).with(reader(scene.candidate())))
                .andExpect(status().isNoContent());
        assertThat(snapshot(scene).assignee()).isEqualTo(String.valueOf(scene.candidate()));
    }

    @Test
    @DisplayName("RULE-310 task details are visible to participants only; a stranger is told the task does not exist")
    void detailsAreForParticipantsOnly() throws Exception {
        Scene scene = scene();
        String id = scene.task().value();
        mvc.perform(get("/api/v1/platform/approval-tasks/" + id).with(reader(scene.stranger()))).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PLATFORM_APPROVAL_TASK_NOT_FOUND"));
        mvc.perform(get("/api/v1/platform/approval-tasks/" + id).with(reader(scene.candidate()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.canAct").value(true)).andExpect(jsonPath("$.initiator").value(scene.initiator()));
        mvc.perform(get("/api/v1/platform/approval-tasks/" + id).with(reader(scene.initiator()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.canAct").value(false));
    }

    @Test
    @DisplayName("an unknown task is 404 for every operation, not 403 and not a server error")
    void unknownTaskIsNotFound() throws Exception {
        mvc.perform(post("/api/v1/platform/approval-tasks/no-such-task:claim").with(csrf()).with(currentEtag()).with(approver(newId())))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/platform/approval-tasks/no-such-task:decide").with(csrf()).with(currentEtag()).with(approver(newId()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"outcome\":\"APPROVE\"}")).andExpect(status().isNotFound());
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
