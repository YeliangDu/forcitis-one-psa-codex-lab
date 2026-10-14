package com.forcitis.one.platform.approval.web;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.application.ClaimTask;
import com.forcitis.one.platform.approval.application.DecideTasks;
import com.forcitis.one.platform.approval.application.GetTaskDetail;
import com.forcitis.one.platform.approval.application.ListInbox;
import com.forcitis.one.platform.approval.application.ReassignTask;
import com.forcitis.one.platform.approval.application.ReleaseTask;
import com.forcitis.one.platform.approval.application.ReturnToInitiator;
import com.forcitis.one.platform.approval.application.TaskDecision;
import com.forcitis.one.platform.approval.application.TaskDetail;
import com.forcitis.one.platform.approval.application.TransferTask;
import com.forcitis.one.platform.approval.application.VersionMismatchException;
import com.forcitis.one.platform.identity.api.CurrentUser;
import java.util.List;
import java.util.OptionalLong;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The caller is always the session user, no request carries an identity (G33); there is no start endpoint (I-5). Acting on a task
 * needs to be its handler or candidate, nothing else (D-1a-179): the use cases decide that, so a signed-in session is enough here.
 */
@RestController
@RequestMapping("/api/v1/platform/approval-tasks")
class ApprovalTaskController {

    private static final String SIGNED_IN = "isAuthenticated()";
    private static final String VERSION_REQUIRED = "PLATFORM_WEB_VERSION_REQUIRED";
    private static final String VERSION_MALFORMED = "PLATFORM_WEB_VERSION_MALFORMED";
    private static final String VERSION_MISMATCH = "PLATFORM_WEB_VERSION_MISMATCH";

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
    InboxResponse list() {
        return InboxResponse.of(listInbox.execute(currentUser.userId()));
    }

    @GetMapping(path = "/{taskId}")
    @PreAuthorize(SIGNED_IN)
    ResponseEntity<TaskDetailResponse> get(@PathVariable String taskId) {
        TaskDetail detail = getTaskDetail.execute(new ApprovalTaskId(taskId), currentUser.userId());
        return ResponseEntity.ok().eTag("\"" + detail.version() + "\"").body(TaskDetailResponse.of(detail));
    }

    @PostMapping(path = "/{taskId}:claim")
    @PreAuthorize(SIGNED_IN)
    ResponseEntity<Void> claim(@PathVariable String taskId, @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        claimTask.execute(new ApprovalTaskId(taskId), currentUser.userId(), expectedVersion(ifMatch));
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "/{taskId}:release")
    @PreAuthorize(SIGNED_IN)
    ResponseEntity<Void> release(@PathVariable String taskId, @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        releaseTask.execute(new ApprovalTaskId(taskId), currentUser.userId(), expectedVersion(ifMatch));
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "/{taskId}:decide")
    @PreAuthorize(SIGNED_IN)
    DecisionResponse decide(@PathVariable String taskId, @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @RequestBody DecideTaskRequest request) {
        return DecisionResponse.of(decideTasks.execute(currentUser.userId(),
                List.of(new TaskDecision(new ApprovalTaskId(taskId), request.outcome(), request.reason())), expectedVersion(ifMatch)));
    }

    @PostMapping(path = "/{taskId}:return")
    @PreAuthorize(SIGNED_IN)
    DecisionResponse returnTask(@PathVariable String taskId, @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @RequestBody ReturnTaskRequest request) {
        return DecisionResponse.of(returnToInitiator.execute(new ApprovalTaskId(taskId), currentUser.userId(), request.reason(),
                expectedVersion(ifMatch)));
    }

    @PostMapping("/{taskId}:transfer")
    @PreAuthorize(SIGNED_IN)
    ResponseEntity<Void> transfer(@PathVariable String taskId, @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @RequestBody TransferTaskRequest request) {
        transferTask.execute(new ApprovalTaskId(taskId), currentUser.userId(), new UserId(request.assigneeId()), request.reason(),
                expectedVersion(ifMatch));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{taskId}:reassign")
    @PreAuthorize(SIGNED_IN)
    ResponseEntity<Void> reassign(@PathVariable String taskId, @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @RequestBody ReassignTaskRequest request) {
        reassignTask.execute(new ApprovalTaskId(taskId), currentUser.userId(), new UserId(request.assigneeId()), request.reason(),
                expectedVersion(ifMatch));
        return ResponseEntity.noContent().build();
    }

    /** Only a strong entity tag is accepted: the version is compared exactly, not weakly. */
    private static OptionalLong expectedVersion(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new PreconditionFailure(HttpStatus.PRECONDITION_REQUIRED, VERSION_REQUIRED, "Send the ETag of the approval as If-Match");
        }
        String tag = ifMatch.trim();
        if (tag.length() < 3 || tag.charAt(0) != '"' || tag.charAt(tag.length() - 1) != '"') {
            throw new PreconditionFailure(HttpStatus.BAD_REQUEST, VERSION_MALFORMED, "If-Match must be one strong ETag as read");
        }
        try {
            return OptionalLong.of(Long.parseLong(tag.substring(1, tag.length() - 1)));
        } catch (NumberFormatException malformed) {
            throw new PreconditionFailure(HttpStatus.BAD_REQUEST, VERSION_MALFORMED, "If-Match must be one strong ETag as read");
        }
    }

    @ExceptionHandler(VersionMismatchException.class)
    ResponseEntity<ProblemDetail> versionMismatch(VersionMismatchException mismatch) {
        return preconditionFailed(new PreconditionFailure(HttpStatus.PRECONDITION_FAILED, VERSION_MISMATCH, mismatch.getMessage()));
    }

    @ExceptionHandler(PreconditionFailure.class)
    ResponseEntity<ProblemDetail> preconditionFailed(PreconditionFailure failure) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(failure.status, failure.getMessage());
        body.setProperty("errorCode", failure.errorCode);
        return ResponseEntity.status(failure.status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
    }

    /** No ETag (428), a malformed one (400) or an old one (412): a refusal of its own, with the error code in the body. */
    private static final class PreconditionFailure extends RuntimeException {
        private final transient HttpStatus status;
        private final String errorCode;

        PreconditionFailure(HttpStatus status, String errorCode, String detail) {
            super(detail);
            this.status = status;
            this.errorCode = errorCode;
        }
    }
}
