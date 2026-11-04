package com.forcitis.one.organization.workforce.web;

import com.forcitis.one.kernel.CursorPage;
import com.forcitis.one.kernel.PageRequest;
import com.forcitis.one.organization.workforce.api.SeparationHoldId;
import com.forcitis.one.organization.workforce.application.GetSeparationHold;
import com.forcitis.one.organization.workforce.application.ListSeparationHolds;
import com.forcitis.one.organization.workforce.web.CommandReadResponses.HoldReadResponse;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organization/separation-holds")
class SeparationHoldReadController {
    private final ListSeparationHolds list;
    private final GetSeparationHold get;

    SeparationHoldReadController(ListSeparationHolds list, GetSeparationHold get) {
        this.list = list;
        this.get = get;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.SEPARATION_HOLD_APPROVE + "')")
    CursorPage<HoldReadResponse> list(@RequestParam(required = false) String status, @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor) {
        var page = list.execute(status, PageRequest.of(limit, cursor, ListSeparationHolds.LIST));
        return CursorPage.of(page.items().stream().map(HoldReadResponse::of).toList(), page.nextCursor());
    }

    @GetMapping("/{separationHoldId}")
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.SEPARATION_HOLD_APPROVE + "')")
    HoldReadResponse get(@PathVariable UUID separationHoldId) {
        return HoldReadResponse.of(get.execute(new SeparationHoldId(separationHoldId)));
    }
}
