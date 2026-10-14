package com.forcitis.one.organization.workforce.web;

import com.forcitis.one.organization.workforce.application.GetSyncRun;
import com.forcitis.one.organization.workforce.application.ListSyncRuns;
import com.forcitis.one.organization.workforce.application.ReleaseResult;
import com.forcitis.one.organization.workforce.application.ReleaseSyncRun;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
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
    List<SyncRunResponse> list(@RequestParam(defaultValue = "50") int limit) {
        return listSyncRuns.execute(limit).stream().map(SyncRunResponse::of).toList();
    }

    @GetMapping(path = "/{syncRunId}")
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.SYNC_RUN_READ + "')")
    ResponseEntity<SyncRunDetailResponse> get(@PathVariable long syncRunId) {
        return getSyncRun.execute(syncRunId).map(SyncRunDetailResponse::of)
                .map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** The actor is the authenticated session user, never a request field. */
    @PostMapping(path = "/{syncRunId}:release")
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.SYNC_RUN_EXECUTE + "')")
    ResponseEntity<ReleaseResponse> release(@PathVariable long syncRunId, Principal caller) {
        ReleaseResult result = releaseSyncRun.execute(syncRunId, Long.parseLong(caller.getName()));
        return switch (result) {
            case ReleaseResult.Released released -> ResponseEntity.ok(
                    new ReleaseResponse("RELEASED", null, SyncRunResponse.of(released.releasedRun())));
            case ReleaseResult.NotFound notFound -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ReleaseResponse("NOT_FOUND", null, null));
            case ReleaseResult.NotHeld notHeld -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ReleaseResponse("NOT_HELD", "the run is " + notHeld.currentStatus() + ", only a HELD run can be released", null));
            case ReleaseResult.NotApplied notApplied -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ReleaseResponse("NOT_APPLIED", notApplied.reason(), null));
        };
    }
}
