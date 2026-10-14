package com.forcitis.one.organization.workforce.web;

import com.forcitis.one.organization.workforce.application.GetSyncRun;
import com.forcitis.one.organization.workforce.application.ListHeldSyncRuns;
import com.forcitis.one.organization.workforce.application.ListSyncRuns;
import com.forcitis.one.organization.workforce.application.ReleaseResult;
import com.forcitis.one.organization.workforce.application.ReleaseSyncRun;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organization/sync-runs")
class SyncRunController {
    private final ListSyncRuns listSyncRuns;
    private final GetSyncRun getSyncRun;
    private final ReleaseSyncRun releaseSyncRun;
    private final ListHeldSyncRuns listHeldSyncRuns;

    SyncRunController(ListSyncRuns listSyncRuns, GetSyncRun getSyncRun, ReleaseSyncRun releaseSyncRun, ListHeldSyncRuns listHeldSyncRuns) {
        this.listHeldSyncRuns = listHeldSyncRuns;
        this.listSyncRuns = listSyncRuns;
        this.getSyncRun = getSyncRun;
        this.releaseSyncRun = releaseSyncRun;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('" + WorkforcePermissions.SYNC_RUN_READ + "', '" + WorkforcePermissions.SEPARATION_HOLD_APPROVE + "')")
    List<SyncRunResponse> list(@RequestParam(defaultValue = "50") int limit, @RequestParam(required = false) String status, Authentication caller) {
        if ("HELD".equals(status)) {
            return listHeldSyncRuns.execute().stream().map(SyncRunResponse::of).toList();
        }
        if (!holds(caller, WorkforcePermissions.SYNC_RUN_READ)) {
            throw new AccessDeniedException("only the held runs are open to the holder of " + WorkforcePermissions.SEPARATION_HOLD_APPROVE);
        }
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
    @PreAuthorize("hasAnyAuthority('" + WorkforcePermissions.SYNC_RUN_EXECUTE + "', '" + WorkforcePermissions.SEPARATION_HOLD_APPROVE + "')")
    ResponseEntity<ReleaseResponse> release(@PathVariable long syncRunId, @RequestBody ReleaseRequest request, Authentication caller) {
        ReleaseResult result = releaseSyncRun.execute(syncRunId, Long.parseLong(caller.getName()), request.reason(),
                holds(caller, WorkforcePermissions.SYNC_RUN_EXECUTE), holds(caller, WorkforcePermissions.SEPARATION_HOLD_APPROVE));
        return switch (result) {
            case ReleaseResult.PartlyReleased partly -> ResponseEntity.ok(
                    new ReleaseResponse("PARTLY_RELEASED", "a hold of another kind is still waiting", SyncRunResponse.of(partly.heldRun())));
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

    private static boolean holds(Authentication caller, String permission) {
        return caller.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(permission));
    }
}
