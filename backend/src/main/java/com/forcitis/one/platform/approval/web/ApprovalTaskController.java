package com.forcitis.one.platform.approval.web;

import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.application.ClaimTask;
import com.forcitis.one.platform.approval.application.DecideTasks;
import com.forcitis.one.platform.approval.application.DecisionResult;
import com.forcitis.one.platform.approval.application.GetTaskDetail;
import com.forcitis.one.platform.approval.application.ListInbox;
import com.forcitis.one.platform.approval.application.MergedInbox;
import com.forcitis.one.platform.approval.application.ReturnToInitiator;
import com.forcitis.one.platform.approval.application.TaskDecision;
import com.forcitis.one.platform.approval.application.TaskDetail;
import com.forcitis.one.platform.identity.api.CurrentUser;
import com.forcitis.one.platform.identity.api.Permission;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The caller is always the session user, no request carries an identity (G33); there is no start endpoint (I-5). */
@RestController
@RequestMapping("/api/{version}/platform/approval-tasks")
class ApprovalTaskController {

    private final ListInbox listInbox;
    private final GetTaskDetail getTaskDetail;
    private final ClaimTask claimTask;
    private final DecideTasks decideTasks;
    private final ReturnToInitiator returnToInitiator;
    private final CurrentUser currentUser;

    ApprovalTaskController(ListInbox listInbox, GetTaskDetail getTaskDetail, ClaimTask claimTask, DecideTasks decideTasks,
            ReturnToInitiator returnToInitiator, CurrentUser currentUser) {
        this.listInbox = listInbox;
        this.getTaskDetail = getTaskDetail;
        this.claimTask = claimTask;
        this.decideTasks = decideTasks;
        this.returnToInitiator = returnToInitiator;
        this.currentUser = currentUser;
    }

    @GetMapping(path = "", version = "1")
    @PreAuthorize("hasAuthority('" + Permission.Codes.PLATFORM_APPROVAL_TASK_READ + "')")
    MergedInbox list() {
        return listInbox.execute(currentUser.userId());
    }

    @GetMapping(path = "/{taskId}", version = "1")
    @PreAuthorize("hasAuthority('" + Permission.Codes.PLATFORM_APPROVAL_TASK_READ + "')")
    TaskDetail get(@PathVariable String taskId) {
        return getTaskDetail.execute(new ApprovalTaskId(taskId), currentUser.userId());
    }

    @PostMapping(path = "/{taskId}:claim", version = "1")
    @PreAuthorize("hasAuthority('" + Permission.Codes.PLATFORM_APPROVAL_TASK_APPROVE + "')")
    ResponseEntity<Void> claim(@PathVariable String taskId) {
        claimTask.execute(new ApprovalTaskId(taskId), currentUser.userId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "/{taskId}:decide", version = "1")
    @PreAuthorize("hasAuthority('" + Permission.Codes.PLATFORM_APPROVAL_TASK_APPROVE + "')")
    DecisionResult decide(@PathVariable String taskId, @RequestBody DecideTaskRequest request) {
        return decideTasks.execute(currentUser.userId(),
                List.of(new TaskDecision(new ApprovalTaskId(taskId), request.outcome(), request.reason())));
    }

    @PostMapping(path = "/{taskId}:return", version = "1")
    @PreAuthorize("hasAuthority('" + Permission.Codes.PLATFORM_APPROVAL_TASK_APPROVE + "')")
    DecisionResult returnTask(@PathVariable String taskId, @RequestBody ReturnTaskRequest request) {
        return returnToInitiator.execute(new ApprovalTaskId(taskId), currentUser.userId(), request.reason());
    }
}
