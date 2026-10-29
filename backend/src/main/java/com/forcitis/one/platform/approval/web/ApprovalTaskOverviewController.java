package com.forcitis.one.platform.approval.web;

import com.forcitis.one.platform.approval.application.ListAllTasks;
import com.forcitis.one.platform.approval.application.ListPage;
import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.platform.identity.api.SessionRoles;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The process administrator's read-only view of every open task (D-1a-139); anybody else is refused by the use case. */
@RestController
@RequestMapping("/api/v1/platform/approval-task-overviews")
class ApprovalTaskOverviewController {

    private final ListAllTasks listAllTasks;
    private final CurrentUser currentUser;

    ApprovalTaskOverviewController(ListAllTasks listAllTasks, CurrentUser currentUser) {
        this.listAllTasks = listAllTasks;
        this.currentUser = currentUser;
    }

    @GetMapping
    @PreAuthorize("hasRole('" + SessionRoles.ORDINARY + "')")
    TaskOverviewPageResponse list(@RequestParam(defaultValue = "false") boolean overdueOnly, @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return TaskOverviewPageResponse.of(listAllTasks.execute(currentUser.userId(), overdueOnly, ListPage.of(cursor, limit)));
    }
}
