package com.forcitis.one.organization.workforce.web;

import com.forcitis.one.kernel.CursorPage;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.organization.workforce.api.SyncRunId;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.kernel.PageRequest;
import com.forcitis.one.organization.workforce.application.GetSyncRun;
import com.forcitis.one.organization.workforce.application.ListHeldSyncRuns;
import com.forcitis.one.organization.workforce.application.ListSyncRuns;
import com.forcitis.one.organization.workforce.application.ReleaseResult;
import com.forcitis.one.organization.workforce.application.ReleaseSyncRun;
import com.forcitis.one.organization.workforce.application.SyncRunView;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
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
    CursorPage<SyncRunResponse> list(@RequestParam(required = false) Integer limit, @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String status, Authentication caller) {
        if ("HELD".equals(status)) {
            return CursorPage.of(listHeldSyncRuns.execute().stream().map(SyncRunResponse::of).toList(), null);
        }
        if (!holds(caller, WorkforcePermissions.SYNC_RUN_READ)) {
            throw new AccessDeniedException("only the held runs are open to the holder of " + WorkforcePermissions.SEPARATION_HOLD_APPROVE);
        }
        CursorPage<SyncRunView> page = listSyncRuns.execute(PageRequest.of(limit, cursor, "sync-runs"));
        return CursorPage.of(page.items().stream().map(SyncRunResponse::of).toList(), page.nextCursor());
    }

    @GetMapping(path = "/{syncRunId}")
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.SYNC_RUN_READ + "')")
    SyncRunDetailResponse get(@PathVariable UUID syncRunId) {
        return getSyncRun.execute(new SyncRunId(syncRunId)).map(SyncRunDetailResponse::of).orElseThrow(() -> new DomainException(
                WorkforceError.ORGANIZATION_WORKFORCE_SYNC_RUN_NOT_FOUND, ErrorKind.NOT_FOUND, "no sync run " + syncRunId));
    }

    /** The actor is the authenticated session user, never a request field. */
    @PostMapping(path = "/{syncRunId}:release")
    @PreAuthorize("hasAnyAuthority('" + WorkforcePermissions.SYNC_RUN_EXECUTE + "', '" + WorkforcePermissions.SEPARATION_HOLD_APPROVE + "')")
    ReleaseResponse release(@PathVariable UUID syncRunId, @RequestBody ReleaseRequest request) {
        ReleaseResult result = releaseSyncRun.execute(new SyncRunId(syncRunId), request.reason());
        return switch (result) {
            case ReleaseResult.PartlyReleased partly ->
                    new ReleaseResponse("PARTLY_RELEASED", "a hold of another kind is still waiting", SyncRunResponse.of(partly.heldRun()));
            case ReleaseResult.Released released ->
                    new ReleaseResponse("RELEASED", null, SyncRunResponse.of(released.releasedRun()));
            case ReleaseResult.NotFound notFound -> throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_SYNC_RUN_NOT_FOUND,
                    ErrorKind.NOT_FOUND, "no sync run " + syncRunId);
            case ReleaseResult.NotHeld notHeld -> throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_SYNC_RUN_TRANSITION_ILLEGAL,
                    ErrorKind.CONFLICT, "the run is " + notHeld.currentStatus() + ", only a HELD run can be released");
            case ReleaseResult.NotApplied notApplied -> throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_SYNC_RUN_APPLY_FAILED,
                    ErrorKind.CONFLICT, notApplied.reason());
        };
    }

    private static boolean holds(Authentication caller, String permission) {
        return caller.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(permission));
    }
}
