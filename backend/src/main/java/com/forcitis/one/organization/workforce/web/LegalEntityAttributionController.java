package com.forcitis.one.organization.workforce.web;

import com.forcitis.one.kernel.ExpectedVersion;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.workforce.api.AttributionKind;
import com.forcitis.one.organization.workforce.application.AttributeLegalEntity;
import com.forcitis.one.organization.workforce.application.CorrectLegalEntityAttribution;
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
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organization")
class LegalEntityAttributionController {
    private final GetLegalEntityAttribution get;
    private final AttributeLegalEntity attribute;
    private final EndLegalEntityAttribution end;
    private final CorrectLegalEntityAttribution correct;

    LegalEntityAttributionController(GetLegalEntityAttribution get, AttributeLegalEntity attribute, EndLegalEntityAttribution end,
            CorrectLegalEntityAttribution correct) {
        this.correct = correct;
        this.get = get;
        this.attribute = attribute;
        this.end = end;
    }

    @GetMapping("/org-unit-legal-entities/{orgUnitId}")
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.ORG_UNIT_READ + "')")
    ResponseEntity<LegalEntityAttributionResponse> ofOrgUnit(@PathVariable UUID orgUnitId) {
        return answer(get.execute(AttributionKind.ORG_UNIT, orgUnitId));
    }

    @PutMapping("/org-unit-legal-entities/{orgUnitId}")
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.LEGAL_ENTITY_UPDATE + "')")
    ResponseEntity<LegalEntityAttributionResponse> attributeOrgUnit(@PathVariable UUID orgUnitId, @RequestBody AttributeLegalEntityRequest request,
            @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        return answer(attribute.execute(AttributionKind.ORG_UNIT, orgUnitId, request.legalEntityId() == null ? null : new LegalEntityId(request.legalEntityId()), request.effectiveFrom(),
                ExpectedVersion.fromHeader(ifMatch)));
    }

    @PostMapping("/org-unit-legal-entities/{orgUnitId}:end")
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.LEGAL_ENTITY_UPDATE + "')")
    ResponseEntity<LegalEntityAttributionResponse> endOrgUnit(@PathVariable UUID orgUnitId, @RequestBody EndLegalEntityAttributionRequest request,
            @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        return answer(end.execute(AttributionKind.ORG_UNIT, orgUnitId, request.effectiveTo(), ExpectedVersion.fromHeader(ifMatch)));
    }

    @GetMapping("/employee-legal-entities/{employeeId}")
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.ORG_UNIT_READ + "')")
    ResponseEntity<LegalEntityAttributionResponse> ofEmployee(@PathVariable UUID employeeId) {
        return answer(get.execute(AttributionKind.EMPLOYEE, employeeId));
    }

    @PutMapping("/employee-legal-entities/{employeeId}")
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.LEGAL_ENTITY_UPDATE + "')")
    ResponseEntity<LegalEntityAttributionResponse> attributeEmployee(@PathVariable UUID employeeId, @RequestBody AttributeLegalEntityRequest request,
            @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        return answer(attribute.execute(AttributionKind.EMPLOYEE, employeeId, request.legalEntityId() == null ? null : new LegalEntityId(request.legalEntityId()), request.effectiveFrom(),
                ExpectedVersion.fromHeader(ifMatch)));
    }

    @PostMapping("/employee-legal-entities/{employeeId}:end")
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.LEGAL_ENTITY_UPDATE + "')")
    ResponseEntity<LegalEntityAttributionResponse> endEmployee(@PathVariable UUID employeeId, @RequestBody EndLegalEntityAttributionRequest request,
            @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        return answer(end.execute(AttributionKind.EMPLOYEE, employeeId, request.effectiveTo(), ExpectedVersion.fromHeader(ifMatch)));
    }

    @PostMapping("/org-unit-legal-entities/{orgUnitId}:correct")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<LegalEntityAttributionResponse> correctOrgUnit(@PathVariable UUID orgUnitId, @RequestBody CorrectLegalEntityAttributionRequest request,
            @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        return answer(correct.execute(AttributionKind.ORG_UNIT, orgUnitId, request.periodFrom(), request.effectiveTo(), request.reason(),
                ExpectedVersion.fromHeader(ifMatch)));
    }

    @PostMapping("/employee-legal-entities/{employeeId}:correct")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<LegalEntityAttributionResponse> correctEmployee(@PathVariable UUID employeeId, @RequestBody CorrectLegalEntityAttributionRequest request,
            @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        return answer(correct.execute(AttributionKind.EMPLOYEE, employeeId, request.periodFrom(), request.effectiveTo(), request.reason(),
                ExpectedVersion.fromHeader(ifMatch)));
    }

    private static ResponseEntity<LegalEntityAttributionResponse> answer(LegalEntityAttributionView view) {
        ResponseEntity.BodyBuilder ok = ResponseEntity.ok();
        ok.eTag(ExpectedVersion.etag(view.version() == null ? 0 : view.version()));
        return ok.body(LegalEntityAttributionResponse.of(view));
    }
}
