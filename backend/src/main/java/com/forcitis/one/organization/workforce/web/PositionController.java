package com.forcitis.one.organization.workforce.web;

import com.forcitis.one.kernel.CursorPage;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.PageRequest;
import com.forcitis.one.organization.workforce.api.PositionId;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.application.GetPosition;
import com.forcitis.one.organization.workforce.application.ListPositions;
import com.forcitis.one.organization.workforce.application.ListPositions.Status;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organization/positions")
class PositionController {
    private final ListPositions list;
    private final GetPosition get;

    PositionController(ListPositions list, GetPosition get) {
        this.list = list;
        this.get = get;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    CursorPage<PositionResponse> list(@RequestParam(required = false) @Nullable Status status,
                                     @RequestParam(required = false) @Nullable Integer limit, @RequestParam(required = false) @Nullable String cursor) {
        var page = list.execute(status, PageRequest.of(limit, cursor, "positions"));
        return CursorPage.of(page.items().stream().map(PositionResponse::of).toList(), page.nextCursor());
    }

    @GetMapping("/{positionId}")
    @PreAuthorize("isAuthenticated()")
    PositionResponse get(@PathVariable UUID positionId) {
        return get.execute(new PositionId(positionId)).map(PositionResponse::of).orElseThrow(() -> new DomainException(
                WorkforceError.ORGANIZATION_WORKFORCE_POSITION_NOT_FOUND, ErrorKind.NOT_FOUND, "no position " + positionId));
    }
}
