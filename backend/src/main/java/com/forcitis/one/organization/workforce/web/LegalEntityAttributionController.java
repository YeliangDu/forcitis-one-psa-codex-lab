package com.forcitis.one.organization.workforce.web;

import com.forcitis.one.kernel.ExpectedVersion;
import com.forcitis.one.organization.workforce.api.AttributionKind;
import com.forcitis.one.organization.workforce.application.AttributeLegalEntity;
import com.forcitis.one.organization.workforce.application.EndLegalEntityAttribution;
import com.forcitis.one.organization.workforce.application.GetLegalEntityAttribution;
import com.forcitis.one.organization.workforce.application.LegalEntityAttributionView;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organization")
class LegalEntityAttributionController {
    private final GetLegalEntityAttribution get;
    private final AttributeLegalEntity attribute;
    private final EndLegalEntityAttribution end;

    LegalEntityAttributionController(GetLegalEntityAttribution get, AttributeLegalEntity attribute, EndLegalEntityAttribution end) {
        this.get = get;
        this.attribute = attribute;
        this.end = end;
    }

    @GetMapping("/org-unit-legal-entities/{orgUnitId}")
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.ORG_UNIT_READ + "')")
    ResponseEntity<LegalEntityAttributionResponse> ofOrgUnit(@PathVariable long orgUnitId) {
        return answer(get.execute(AttributionKind.ORG_UNIT, orgUnitId));
    }

    @PutMapping("/org-unit-legal-entities/{orgUnitId}")
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.LEGAL_ENTITY_UPDATE + "')")
    ResponseEntity<LegalEntityAttributionResponse> attributeOrgUnit(@PathVariable long orgUnitId, @RequestBody AttributeLegalEntityRequest request,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
        return answer(attribute.execute(AttributionKind.ORG_UNIT, orgUnitId, request.legalEntityId(), request.effectiveFrom(),
                ExpectedVersion.fromHeaders(ifMatch, ifNoneMatch)));
    }

    @PostMapping("/org-unit-legal-entities/{orgUnitId}:end")
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.LEGAL_ENTITY_UPDATE + "')")
    ResponseEntity<LegalEntityAttributionResponse> endOrgUnit(@PathVariable long orgUnitId, @RequestBody EndLegalEntityAttributionRequest request,
            @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        return answer(end.execute(AttributionKind.ORG_UNIT, orgUnitId, request.effectiveTo(), ExpectedVersion.fromHeaders(ifMatch, null)));
    }

    @GetMapping("/employee-legal-entities/{employeeId}")
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.ORG_UNIT_READ + "')")
    ResponseEntity<LegalEntityAttributionResponse> ofEmployee(@PathVariable long employeeId) {
        return answer(get.execute(AttributionKind.EMPLOYEE, employeeId));
    }

    @PutMapping("/employee-legal-entities/{employeeId}")
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.LEGAL_ENTITY_UPDATE + "')")
    ResponseEntity<LegalEntityAttributionResponse> attributeEmployee(@PathVariable long employeeId, @RequestBody AttributeLegalEntityRequest request,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
        return answer(attribute.execute(AttributionKind.EMPLOYEE, employeeId, request.legalEntityId(), request.effectiveFrom(),
                ExpectedVersion.fromHeaders(ifMatch, ifNoneMatch)));
    }

    @PostMapping("/employee-legal-entities/{employeeId}:end")
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.LEGAL_ENTITY_UPDATE + "')")
    ResponseEntity<LegalEntityAttributionResponse> endEmployee(@PathVariable long employeeId, @RequestBody EndLegalEntityAttributionRequest request,
            @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        return answer(end.execute(AttributionKind.EMPLOYEE, employeeId, request.effectiveTo(), ExpectedVersion.fromHeaders(ifMatch, null)));
    }

    private static ResponseEntity<LegalEntityAttributionResponse> answer(LegalEntityAttributionView view) {
        ResponseEntity.BodyBuilder ok = ResponseEntity.ok();
        if (view.version() != null) {
            ok.eTag(ExpectedVersion.etag(view.version()));
        }
        return ok.body(LegalEntityAttributionResponse.of(view));
    }
}
