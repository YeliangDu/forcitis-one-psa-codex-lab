package com.forcitis.one.platform.approval.web;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.application.ClaimTask;
import com.forcitis.one.platform.approval.application.DecideTasks;
import com.forcitis.one.platform.approval.application.GetTaskDetail;
import com.forcitis.one.platform.approval.application.ListInbox;
import com.forcitis.one.platform.approval.application.ListPage;
import com.forcitis.one.platform.approval.application.ReassignTask;
import com.forcitis.one.platform.approval.application.ReleaseTask;
import com.forcitis.one.platform.approval.application.ReturnToInitiator;
import com.forcitis.one.platform.approval.application.TaskDecision;
import com.forcitis.one.platform.approval.application.TaskDetail;
import com.forcitis.one.platform.approval.application.TransferTask;
import com.forcitis.one.kernel.ExpectedVersion;
import com.forcitis.one.kernel.InvalidRequestException;
import com.forcitis.one.kernel.CurrentUser;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import com.forcitis.one.platform.identity.api.SessionRoles;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The caller is always the session user, no request carries an identity (G33); there is no start endpoint (I-5). Acting on a task
 * needs to be its handler or candidate, nothing else (D-1a-179): the use cases decide that, so an ordinary session is enough here: the administrator mode and an emergency session handle no approvals (I-11).
 */
@RestController
@RequestMapping("/api/v1/platform/approval-tasks")
class ApprovalTaskController {

    private static final String SIGNED_IN = "hasRole('" + SessionRoles.ORDINARY + "')";

    private final ListInbox listInbox;
    private final GetTaskDetail getTaskDetail;
    private final ClaimTask claimTask;
    private final ReleaseTask releaseTask;
    private final DecideTasks decideTasks;
    private final ReturnToInitiator returnToInitiator;
    private final TransferTask transferTask;
    private final ReassignTask reassignTask;
    private final CurrentUser currentUser;

    ApprovalTaskController(ListInbox listInbox, GetTaskDetail getTaskDetail,
            ClaimTask claimTask, ReleaseTask releaseTask, DecideTasks decideTasks, ReturnToInitiator returnToInitiator,
            TransferTask transferTask, ReassignTask reassignTask, CurrentUser currentUser) {
        this.listInbox = listInbox;
        this.getTaskDetail = getTaskDetail;
        this.claimTask = claimTask;
        this.releaseTask = releaseTask;
        this.decideTasks = decideTasks;
        this.returnToInitiator = returnToInitiator;
        this.transferTask = transferTask;
        this.reassignTask = reassignTask;
        this.currentUser = currentUser;
    }

    @GetMapping(path = "")
    @PreAuthorize(SIGNED_IN)
    InboxResponse list(@RequestParam(required = false) String cursor, @RequestParam(required = false) Integer limit) {
        return InboxResponse.of(listInbox.execute(currentUser.userId(), ListPage.of(cursor, limit)));
    }

    @GetMapping(path = "/{taskId}")
    @PreAuthorize(SIGNED_IN)
    ResponseEntity<TaskDetailResponse> get(@PathVariable UUID taskId) {
        TaskDetail detail = getTaskDetail.execute(new ApprovalTaskId(taskId), currentUser.userId());
        return ResponseEntity.ok().eTag("\"" + detail.version() + "\"").body(TaskDetailResponse.of(detail));
    }

    @PostMapping(path = "/{taskId}:claim")
    @PreAuthorize(SIGNED_IN)
    ResponseEntity<Void> claim(@PathVariable UUID taskId, @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        claimTask.execute(new ApprovalTaskId(taskId), currentUser.userId(), expectedVersion(ifMatch));
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "/{taskId}:release")
    @PreAuthorize(SIGNED_IN)
    ResponseEntity<Void> release(@PathVariable UUID taskId, @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        releaseTask.execute(new ApprovalTaskId(taskId), currentUser.userId(), expectedVersion(ifMatch));
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "/{taskId}:decide")
    @PreAuthorize(SIGNED_IN)
    DecisionResponse decide(@PathVariable UUID taskId, @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @RequestBody DecideTaskRequest request) {
        return DecisionResponse.of(decideTasks.execute(currentUser.userId(),
                List.of(new TaskDecision(new ApprovalTaskId(taskId), request.outcome(), request.reason())), expectedVersion(ifMatch)));
    }

    @PostMapping(path = "/{taskId}:return")
    @PreAuthorize(SIGNED_IN)
    DecisionResponse returnTask(@PathVariable UUID taskId, @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @RequestBody ReturnTaskRequest request) {
        return DecisionResponse.of(returnToInitiator.execute(new ApprovalTaskId(taskId), currentUser.userId(), request.reason(),
                expectedVersion(ifMatch)));
    }

    @PostMapping("/{taskId}:transfer")
    @PreAuthorize(SIGNED_IN)
    ResponseEntity<Void> transfer(@PathVariable UUID taskId, @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @RequestBody TransferTaskRequest request) {
        transferTask.execute(new ApprovalTaskId(taskId), currentUser.userId(), assignee(request.assigneeId()), request.reason(),
                expectedVersion(ifMatch));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{taskId}:reassign")
    @PreAuthorize(SIGNED_IN)
    ResponseEntity<Void> reassign(@PathVariable UUID taskId, @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @RequestBody ReassignTaskRequest request) {
        reassignTask.execute(new ApprovalTaskId(taskId), currentUser.userId(), assignee(request.assigneeId()), request.reason(),
                expectedVersion(ifMatch));
        return ResponseEntity.noContent().build();
    }

    /** The version the caller read: the same If-Match rules as every other write (428 without, 400 for a weak or unreadable tag). */
    private static OptionalLong expectedVersion(String ifMatch) {
        return OptionalLong.of(ExpectedVersion.fromHeader(ifMatch).value());
    }

    private static EmployeeId assignee(UUID assigneeId) {
        if (assigneeId == null) {
            throw new InvalidRequestException("assigneeId is required");
        }
        return new EmployeeId(assigneeId);
    }
}
