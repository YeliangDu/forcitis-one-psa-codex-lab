package com.forcitis.one.platform.identity.web;

import com.forcitis.one.kernel.CursorPage;
import com.forcitis.one.kernel.ExpectedVersion;
import com.forcitis.one.kernel.InvalidRequestException;
import com.forcitis.one.kernel.PageRequest;
import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentId;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentKind;
import com.forcitis.one.platform.identity.application.GetPermissionAdjustment;
import com.forcitis.one.platform.identity.application.ListPermissionAdjustments;
import com.forcitis.one.platform.identity.application.MarkPermissionAdjustmentAdjusted;
import com.forcitis.one.platform.identity.application.PermissionAdjustmentView;
import com.forcitis.one.platform.identity.application.UpholdPermissionAdjustment;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentStatus;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Who may read or handle an item is decided in the use case, by relation (ADR-0026 section 6 item 5); the endpoint only needs a signed-in caller. */
@RestController
@RequestMapping("/api/v1/platform/permission-adjustments")
class PermissionAdjustmentController {

    private final ListPermissionAdjustments list;
    private final GetPermissionAdjustment get;
    private final UpholdPermissionAdjustment uphold;
    private final MarkPermissionAdjustmentAdjusted markAdjusted;

    PermissionAdjustmentController(ListPermissionAdjustments list, GetPermissionAdjustment get, UpholdPermissionAdjustment uphold,
            MarkPermissionAdjustmentAdjusted markAdjusted) {
        this.list = list;
        this.get = get;
        this.uphold = uphold;
        this.markAdjusted = markAdjusted;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    CursorPage<PermissionAdjustmentResponse> list(@RequestParam(required = false) Integer limit, @RequestParam(required = false) String cursor,
            @RequestParam(required = false) @Nullable PermissionAdjustmentStatus status, @RequestParam(required = false) @Nullable PermissionAdjustmentKind kind) {
        CursorPage<PermissionAdjustmentView> page = list.execute(PageRequest.of(limit, cursor, "permission-adjustments"), status, kind);
        return CursorPage.of(page.items().stream().map(PermissionAdjustmentResponse::of).toList(), page.nextCursor());
    }

    @GetMapping("/{permissionAdjustmentId}")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<PermissionAdjustmentResponse> get(@PathVariable UUID permissionAdjustmentId) {
        return answer(get.execute(new PermissionAdjustmentId(permissionAdjustmentId)));
    }

    @PostMapping("/{permissionAdjustmentId}:uphold")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<PermissionAdjustmentResponse> uphold(@PathVariable UUID permissionAdjustmentId, @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestBody UpholdPermissionAdjustmentRequest request) {
        ExpectedVersion expected = ExpectedVersion.fromHeader(ifMatch);
        UUID owner = request.businessOwnerEmployeeId() == null ? null : uuid(request.businessOwnerEmployeeId(), "businessOwnerEmployeeId");
        return answer(uphold.execute(new PermissionAdjustmentId(permissionAdjustmentId), expected, request.reason(), owner));
    }

    @PostMapping("/{permissionAdjustmentId}:mark-adjusted")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<PermissionAdjustmentResponse> markAdjusted(@PathVariable UUID permissionAdjustmentId, @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestBody MarkPermissionAdjustmentAdjustedRequest request) {
        ExpectedVersion expected = ExpectedVersion.fromHeader(ifMatch);
        UUID authorizationRequest = request.authorizationRequestId() == null ? null : uuid(request.authorizationRequestId(), "authorizationRequestId");
        UUID auditEntry = request.auditEntryId() == null ? null : uuid(request.auditEntryId(), "auditEntryId");
        return answer(markAdjusted.execute(new PermissionAdjustmentId(permissionAdjustmentId), expected, authorizationRequest, auditEntry));
    }

    private static ResponseEntity<PermissionAdjustmentResponse> answer(PermissionAdjustmentView view) {
        return ResponseEntity.ok().eTag(ExpectedVersion.etag(view.version())).body(PermissionAdjustmentResponse.of(view));
    }

    private static UUID uuid(String value, String field) {
        try {
            return Uuids.parseCanonical(value);
        } catch (IllegalArgumentException malformed) {
            throw new InvalidRequestException(field + " is not an id");
        }
    }
}
