package com.forcitis.one.organization.workforce.web;

import com.forcitis.one.organization.workforce.api.WorkforcePermissions;

import com.forcitis.one.kernel.CursorPage;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.ExpectedVersion;
import com.forcitis.one.kernel.InvalidRequestException;
import com.forcitis.one.kernel.PageRequest;
import com.forcitis.one.organization.workforce.api.ProfileChangeRequestId;
import com.forcitis.one.organization.workforce.api.ProfileChangeRequestView;
import com.forcitis.one.organization.workforce.api.ProfileChangeRequests;
import com.forcitis.one.organization.workforce.api.ProfileChangeStatus;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import java.util.UUID;
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

/** The second person of a profile change that moves or clears a set WeCom user id on a privileged account; the decider is the signed-in user, never a request field. */
@RestController
@RequestMapping("/api/v1/organization/employee-profile-change-requests")
class ProfileChangeRequestController {
    private static final String AUTHORITY = "hasAuthority('" + WorkforcePermissions.PERSONNEL_COMMAND_CREATE + "')";
    private static final int MAX_REASON_LENGTH = 500;

    private final ProfileChangeRequests requests;

    ProfileChangeRequestController(ProfileChangeRequests requests) {
        this.requests = requests;
    }

    /** Only the requests that wait are listed. */
    @GetMapping
    @PreAuthorize(AUTHORITY)
    CursorPage<ProfileChangeRequestResponse> list(@RequestParam(required = false) ProfileChangeStatus status, @RequestParam(required = false) Integer limit,
                                                   @RequestParam(required = false) String cursor) {
        if (status != ProfileChangeStatus.PENDING) {
            throw new InvalidRequestException("only status=PENDING is listed");
        }
        CursorPage<ProfileChangeRequestView> page = requests.pending(PageRequest.of(limit, cursor, "profile-change-requests"));
        return CursorPage.of(page.items().stream().map(ProfileChangeRequestResponse::of).toList(), page.nextCursor());
    }

    /** 200 with the request after the command; a confirmation that found the request void answers 409 (the status and its audit entry were committed). */
    @PostMapping("/{requestId}:confirm")
    @PreAuthorize(AUTHORITY)
    ResponseEntity<ProfileChangeRequestResponse> confirm(@PathVariable UUID requestId, @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        ProfileChangeRequestView view = requests.confirm(new ProfileChangeRequestId(requestId), ExpectedVersion.fromHeader(ifMatch));
        if (view.status() == ProfileChangeStatus.VOIDED) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_PROFILE_CHANGE_VOIDED, ErrorKind.CONFLICT,
                    "the request can no longer be applied (" + view.voidReason() + "); ask again from the current profile");
        }
        return ResponseEntity.ok().eTag(ExpectedVersion.etag(view.version())).body(ProfileChangeRequestResponse.of(view));
    }

    @PostMapping("/{requestId}:reject")
    @PreAuthorize(AUTHORITY)
    ResponseEntity<ProfileChangeRequestResponse> reject(@PathVariable UUID requestId, @RequestBody RejectProfileChangeRequest body,
                                                        @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        ExpectedVersion expected = ExpectedVersion.fromHeader(ifMatch);
        String reason = body.reason();
        if (reason == null || reason.isBlank() || reason.length() > MAX_REASON_LENGTH || reason.indexOf('\u0000') >= 0) {
            throw new InvalidRequestException("reason must be 1 to " + MAX_REASON_LENGTH + " characters, not blank and without NUL");
        }
        ProfileChangeRequestView view = requests.reject(new ProfileChangeRequestId(requestId), reason, expected);
        return ResponseEntity.ok().eTag(ExpectedVersion.etag(view.version())).body(ProfileChangeRequestResponse.of(view));
    }
}
