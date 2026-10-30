package com.forcitis.one.organization.workforce.web;

import com.forcitis.one.organization.workforce.api.ReleaseSeparationHold;
import com.forcitis.one.organization.workforce.api.SeparationHoldId;
import com.forcitis.one.organization.workforce.api.SeparationHolds;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The single release of a held separation (ADR-0068 item 2.3); the releaser is the signed-in user, never a request field. */
@RestController
@RequestMapping("/api/v1/organization/separation-holds")
class SeparationHoldController {
    private final SeparationHolds holds;

    SeparationHoldController(SeparationHolds holds) {
        this.holds = holds;
    }

    @PostMapping("/{separationHoldId}:release")
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.SEPARATION_HOLD_APPROVE + "')")
    void release(@PathVariable UUID separationHoldId, @RequestBody ReasonRequest request) {
        holds.release(new ReleaseSeparationHold(new SeparationHoldId(separationHoldId), request.reason()));
    }
}
