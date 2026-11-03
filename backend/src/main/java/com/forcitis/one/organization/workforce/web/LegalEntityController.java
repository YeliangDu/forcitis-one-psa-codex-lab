package com.forcitis.one.organization.workforce.web;

import com.forcitis.one.kernel.CursorPage;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.ExpectedVersion;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.PageRequest;
import com.forcitis.one.organization.workforce.application.ChangeFinanceHead;
import com.forcitis.one.organization.workforce.application.CreateLegalEntity;
import com.forcitis.one.organization.workforce.application.GetLegalEntity;
import com.forcitis.one.organization.workforce.application.LegalEntityView;
import com.forcitis.one.organization.workforce.application.ListLegalEntities;
import com.forcitis.one.organization.workforce.application.UpdateLegalEntity;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organization/legal-entities")
class LegalEntityController {
    private final ListLegalEntities list;
    private final GetLegalEntity get;
    private final CreateLegalEntity create;
    private final UpdateLegalEntity update;
    private final ChangeFinanceHead changeFinanceHead;

    LegalEntityController(ListLegalEntities list, GetLegalEntity get, CreateLegalEntity create, UpdateLegalEntity update,
                          ChangeFinanceHead changeFinanceHead) {
        this.list = list;
        this.get = get;
        this.create = create;
        this.update = update;
        this.changeFinanceHead = changeFinanceHead;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.ORG_UNIT_READ + "')")
    CursorPage<LegalEntityResponse> list(@RequestParam(required = false) Integer limit, @RequestParam(required = false) String cursor) {
        CursorPage<LegalEntityView> page = list.execute(PageRequest.of(limit, cursor, "legal-entities"));
        return CursorPage.of(page.items().stream().map(LegalEntityResponse::of).toList(), page.nextCursor());
    }

    @GetMapping(path = "/{legalEntityId}")
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.ORG_UNIT_READ + "')")
    ResponseEntity<LegalEntityResponse> get(@PathVariable UUID legalEntityId) {
        LegalEntityView view = get.execute(new LegalEntityId(legalEntityId));
        return ResponseEntity.ok().eTag(ExpectedVersion.etag(view.version())).body(LegalEntityResponse.of(view));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.LEGAL_ENTITY_UPDATE + "')")
    ResponseEntity<LegalEntityResponse> create(@RequestBody CreateLegalEntityRequest request) {
        LegalEntityView view = create.execute(request.code(), request.name());
        return ResponseEntity.created(URI.create("/api/v1/organization/legal-entities/" + view.legalEntityId().value()))
                .eTag(ExpectedVersion.etag(view.version())).body(LegalEntityResponse.of(view));
    }

    @PutMapping(path = "/{legalEntityId}")
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.LEGAL_ENTITY_UPDATE + "')")
    ResponseEntity<LegalEntityResponse> update(@PathVariable UUID legalEntityId, @RequestBody UpdateLegalEntityRequest request,
            @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        LegalEntityView view = update.execute(new LegalEntityId(legalEntityId), ExpectedVersion.fromHeader(ifMatch), request.code(), request.name(), request.status());
        return ResponseEntity.ok().eTag(ExpectedVersion.etag(view.version())).body(LegalEntityResponse.of(view));
    }

    @PostMapping(path = "/{legalEntityId}:set-finance-head")
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.LEGAL_ENTITY_UPDATE + "')")
    ResponseEntity<LegalEntityResponse> changeFinanceHead(@PathVariable UUID legalEntityId, @RequestBody ChangeFinanceHeadRequest request,
            @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        UUID head = request.financeHeadEmployeeId();
        LegalEntityView view = changeFinanceHead.execute(new LegalEntityId(legalEntityId), ExpectedVersion.fromHeader(ifMatch), head == null ? null : new EmployeeId(head));
        return ResponseEntity.ok().eTag(ExpectedVersion.etag(view.version())).body(LegalEntityResponse.of(view));
    }
}
