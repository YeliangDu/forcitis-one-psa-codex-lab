package com.forcitis.one.platform.approval.web;

import com.forcitis.one.platform.approval.application.ListAllTasks;
import com.forcitis.one.platform.identity.api.CurrentUser;
import java.util.List;
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
    @PreAuthorize("isAuthenticated()")
    List<TaskOverviewResponse> list(@RequestParam(defaultValue = "false") boolean overdueOnly) {
        return listAllTasks.execute(currentUser.userId(), overdueOnly).stream().map(TaskOverviewResponse::of).toList();
    }
}
