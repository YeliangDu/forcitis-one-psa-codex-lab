package com.forcitis.one.platform.identity.web;

import com.forcitis.one.kernel.CursorPage;
import com.forcitis.one.kernel.PageRequest;
import com.forcitis.one.platform.identity.api.AuthorizationRequestId;
import com.forcitis.one.platform.identity.api.Permission;
import com.forcitis.one.platform.identity.application.AuthorizationRequestView;
import com.forcitis.one.platform.identity.application.ConfirmAuthorizationRequest;
import com.forcitis.one.platform.identity.application.ListAuthorizationRequests;
import com.forcitis.one.platform.identity.application.RejectAuthorizationRequest;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Granting a sensitive permission needs a second holder of account-role:update (ADR-0026 section 6, ADR-0027 section 3). */
@RestController
@RequestMapping("/api/v1/platform/authorization-requests")
class AuthorizationRequestController {

    private static final String AUTHORITY = "hasAuthority('" + Permission.Codes.PLATFORM_IDENTITY_ACCOUNT_ROLE_UPDATE + "')";

    private final ListAuthorizationRequests list;
    private final ConfirmAuthorizationRequest confirm;
    private final RejectAuthorizationRequest reject;

    AuthorizationRequestController(ListAuthorizationRequests list, ConfirmAuthorizationRequest confirm, RejectAuthorizationRequest reject) {
        this.list = list;
        this.confirm = confirm;
        this.reject = reject;
    }

    @GetMapping
    @PreAuthorize(AUTHORITY)
    CursorPage<AuthorizationRequestResponse> pending(@RequestParam(required = false) Integer limit, @RequestParam(required = false) String cursor) {
        CursorPage<AuthorizationRequestView> page = PageRequest.of(limit, cursor, "authorization-requests")
                .slice(list.execute(), view -> view.requestId().toString(), "authorization-requests");
        return CursorPage.of(page.items().stream().map(AuthorizationRequestResponse::of).toList(), page.nextCursor());
    }

    @PostMapping("/{requestId}:confirm")
    @PreAuthorize(AUTHORITY)
    ResponseEntity<Void> confirm(@PathVariable UUID requestId) {
        confirm.execute(new AuthorizationRequestId(requestId));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{requestId}:reject")
    @PreAuthorize(AUTHORITY)
    ResponseEntity<Void> reject(@PathVariable UUID requestId) {
        reject.execute(new AuthorizationRequestId(requestId));
        return ResponseEntity.noContent().build();
    }
}
