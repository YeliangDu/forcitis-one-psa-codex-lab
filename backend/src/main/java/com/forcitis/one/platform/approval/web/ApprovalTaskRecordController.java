package com.forcitis.one.platform.approval.web;

import com.forcitis.one.platform.approval.application.ListHandledTasks;
import com.forcitis.one.platform.approval.application.ListPage;
import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.platform.identity.api.SessionRoles;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** What the signed-in person decided, with the opinion given; the caller is always the session user (G33). */
@RestController
@RequestMapping("/api/v1/platform/approval-task-records")
class ApprovalTaskRecordController {

    private final ListHandledTasks listHandledTasks;
    private final CurrentUser currentUser;

    ApprovalTaskRecordController(ListHandledTasks listHandledTasks, CurrentUser currentUser) {
        this.listHandledTasks = listHandledTasks;
        this.currentUser = currentUser;
    }

    @GetMapping
    @PreAuthorize("hasRole('" + SessionRoles.ORDINARY + "')")
    HandledTaskPageResponse list(@RequestParam(required = false) String cursor, @RequestParam(required = false) Integer limit) {
        return HandledTaskPageResponse.of(listHandledTasks.execute(currentUser.userId(), ListPage.of(cursor, limit)));
    }
}
