package com.forcitis.one.organization.workforce.web;

import com.forcitis.one.kernel.CursorPage;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.kernel.PageRequest;
import com.forcitis.one.organization.workforce.application.GetSyncRun;
import com.forcitis.one.organization.workforce.application.ListSyncRuns;
import com.forcitis.one.organization.workforce.application.ReleaseSyncRun;
import com.forcitis.one.organization.workforce.application.SyncRunView;
import java.security.Principal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organization/sync-runs")
class SyncRunController {
    private final ListSyncRuns listSyncRuns;
    private final GetSyncRun getSyncRun;
    private final ReleaseSyncRun releaseSyncRun;

    SyncRunController(ListSyncRuns listSyncRuns, GetSyncRun getSyncRun, ReleaseSyncRun releaseSyncRun) {
        this.listSyncRuns = listSyncRuns;
        this.getSyncRun = getSyncRun;
        this.releaseSyncRun = releaseSyncRun;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.SYNC_RUN_READ + "')")
    CursorPage<SyncRunResponse> list(@RequestParam(required = false) Integer limit, @RequestParam(required = false) String cursor) {
        CursorPage<SyncRunView> page = listSyncRuns.execute(PageRequest.of(limit, cursor, "sync-runs"));
        return CursorPage.of(page.items().stream().map(SyncRunResponse::of).toList(), page.nextCursor());
    }

    @GetMapping(path = "/{syncRunId}")
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.SYNC_RUN_READ + "')")
    SyncRunDetailResponse get(@PathVariable long syncRunId) {
        return getSyncRun.execute(syncRunId).map(SyncRunDetailResponse::of).orElseThrow(() -> new DomainException(
                WorkforceError.ORGANIZATION_WORKFORCE_SYNC_RUN_NOT_FOUND, ErrorKind.NOT_FOUND, "no sync run " + syncRunId));
    }

    /** The actor is the authenticated session user, never a request field. */
    @PostMapping(path = "/{syncRunId}:release")
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.SYNC_RUN_EXECUTE + "')")
    SyncRunResponse release(@PathVariable long syncRunId, Principal caller) {
        return SyncRunResponse.of(releaseSyncRun.execute(syncRunId, Long.parseLong(caller.getName())));
    }
}
