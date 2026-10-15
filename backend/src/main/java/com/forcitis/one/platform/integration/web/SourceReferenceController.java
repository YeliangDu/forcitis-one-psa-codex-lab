package com.forcitis.one.platform.integration.web;

import com.forcitis.one.kernel.InvalidRequestException;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.integration.application.ConfirmSourceReference;
import com.forcitis.one.platform.integration.application.RegisterSourceReference;
import com.forcitis.one.platform.integration.application.RejectSourceReference;
import com.forcitis.one.platform.integration.application.SourceReferenceView;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The accounting organization code of a legal entity is registered here and confirmed by a second person (ADR-0033 section 5). */
@RestController
@RequestMapping("/api/v1/platform/source-references")
class SourceReferenceController {

    private final RegisterSourceReference register;
    private final ConfirmSourceReference confirm;
    private final RejectSourceReference reject;

    SourceReferenceController(RegisterSourceReference register, ConfirmSourceReference confirm, RejectSourceReference reject) {
        this.register = register;
        this.confirm = confirm;
        this.reject = reject;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + IntegrationPermissions.SOURCE_REFERENCE_UPDATE + "')")
    ResponseEntity<SourceReferenceResponse> register(@RequestBody RegisterSourceReferenceRequest request, Principal caller) {
        if (request.sourceSystem() == null || request.sourceObjectKind() == null || request.sourceId() == null || request.ownId() == null) {
            throw new InvalidRequestException("sourceSystem, sourceObjectKind, sourceId and ownId are required");
        }
        SourceReferenceView view = register.execute(request.sourceSystem(), request.sourceObjectKind(), sourceId(request.sourceId()), request.ownId(),
                request.effectiveFrom(), user(caller));
        return ResponseEntity.status(HttpStatus.CREATED).body(SourceReferenceResponse.of(view));
    }

    @PostMapping("/{sourceReferenceId}:confirm")
    @PreAuthorize("hasAuthority('" + IntegrationPermissions.SOURCE_REFERENCE_APPROVE + "')")
    SourceReferenceResponse confirm(@PathVariable UUID sourceReferenceId, Principal caller) {
        return SourceReferenceResponse.of(confirm.execute(sourceReferenceId, user(caller)));
    }

    @PostMapping("/{sourceReferenceId}:reject")
    @PreAuthorize("hasAuthority('" + IntegrationPermissions.SOURCE_REFERENCE_APPROVE + "')")
    SourceReferenceResponse reject(@PathVariable UUID sourceReferenceId, Principal caller) {
        return SourceReferenceResponse.of(reject.execute(sourceReferenceId, user(caller)));
    }

    private static long sourceId(String text) {
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException notANumber) {
            throw new InvalidRequestException("sourceId is a whole number written as text");
        }
    }

    private static UserId user(Principal caller) {
        return new UserId(Long.parseLong(caller.getName()));
    }
}
