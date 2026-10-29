package com.forcitis.one.platform.integration.web;

import com.forcitis.one.kernel.CursorPage;
import com.forcitis.one.kernel.InvalidRequestException;
import com.forcitis.one.kernel.PageRequest;
import com.forcitis.one.platform.integration.api.ConfirmationStatus;
import com.forcitis.one.platform.integration.api.ExternalSource;
import com.forcitis.one.platform.integration.api.SourceObjectKind;
import com.forcitis.one.platform.integration.application.ConfirmSourceReference;
import com.forcitis.one.platform.integration.application.ListSourceReferences;
import com.forcitis.one.platform.integration.application.RegisterSourceReference;
import com.forcitis.one.platform.integration.application.RejectSourceReference;
import com.forcitis.one.platform.integration.application.SourceReferenceView;
import io.swagger.v3.oas.annotations.Parameter;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The accounting organization code of a legal entity is registered here and confirmed by a second person (ADR-0033 section 5). */
@RestController
@RequestMapping("/api/v1/platform/source-references")
class SourceReferenceController {

    private final RegisterSourceReference register;
    private final ConfirmSourceReference confirm;
    private final RejectSourceReference reject;
    private final ListSourceReferences list;

    SourceReferenceController(RegisterSourceReference register, ConfirmSourceReference confirm, RejectSourceReference reject, ListSourceReferences list) {
        this.register = register;
        this.confirm = confirm;
        this.reject = reject;
        this.list = list;
    }

    /** The maintainer sees what was registered, the approver what waits; only the category written by hand can be listed. */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('" + IntegrationPermissions.SOURCE_REFERENCE_UPDATE + "', '" + IntegrationPermissions.SOURCE_REFERENCE_APPROVE + "')")
    CursorPage<SourceReferenceResponse> list(@RequestParam ExternalSource sourceSystem, @RequestParam SourceObjectKind sourceObjectKind,
            @Parameter(description = "PENDING is the approver's to-do list") @RequestParam(required = false) ConfirmationStatus confirmationStatus,
            @Parameter(description = "Every row of one own object, confirmed, pending and rejected, closed periods included") @RequestParam(required = false) UUID ownId,
            @RequestParam(required = false) Integer limit, @RequestParam(required = false) String cursor) {
        CursorPage<SourceReferenceView> page = list.execute(sourceSystem, sourceObjectKind, confirmationStatus, ownId, PageRequest.of(limit, cursor, "source-references"));
        return CursorPage.of(page.items().stream().map(SourceReferenceResponse::of).toList(), page.nextCursor());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + IntegrationPermissions.SOURCE_REFERENCE_UPDATE + "')")
    ResponseEntity<SourceReferenceResponse> register(@RequestBody RegisterSourceReferenceRequest request) {
        if (request.sourceSystem() == null || request.sourceObjectKind() == null || request.sourceId() == null || request.ownId() == null) {
            throw new InvalidRequestException("sourceSystem, sourceObjectKind, sourceId and ownId are required");
        }
        SourceReferenceView view = register.execute(request.sourceSystem(), request.sourceObjectKind(), sourceId(request.sourceId()), request.ownId(),
                request.effectiveFrom());
        return ResponseEntity.status(HttpStatus.CREATED).body(SourceReferenceResponse.of(view));
    }

    @PostMapping("/{sourceReferenceId}:confirm")
    @PreAuthorize("hasAuthority('" + IntegrationPermissions.SOURCE_REFERENCE_APPROVE + "')")
    SourceReferenceResponse confirm(@PathVariable UUID sourceReferenceId) {
        return SourceReferenceResponse.of(confirm.execute(sourceReferenceId));
    }

    @PostMapping("/{sourceReferenceId}:reject")
    @PreAuthorize("hasAuthority('" + IntegrationPermissions.SOURCE_REFERENCE_APPROVE + "')")
    SourceReferenceResponse reject(@PathVariable UUID sourceReferenceId) {
        return SourceReferenceResponse.of(reject.execute(sourceReferenceId));
    }

    private static long sourceId(String text) {
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException notANumber) {
            throw new InvalidRequestException("sourceId is a whole number written as text");
        }
    }

}
