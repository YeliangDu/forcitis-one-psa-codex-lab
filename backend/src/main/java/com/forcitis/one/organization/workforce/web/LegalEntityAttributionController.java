package com.forcitis.one.organization.workforce.web;

import com.forcitis.one.organization.workforce.api.AttributionKind;
import com.forcitis.one.organization.workforce.application.AttributeLegalEntity;
import com.forcitis.one.organization.workforce.application.EndLegalEntityAttribution;
import com.forcitis.one.organization.workforce.application.GetLegalEntityAttribution;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    LegalEntityAttributionResponse ofOrgUnit(@PathVariable long orgUnitId) {
        return LegalEntityAttributionResponse.of(get.execute(AttributionKind.ORG_UNIT, orgUnitId));
    }

    @PutMapping("/org-unit-legal-entities/{orgUnitId}")
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.LEGAL_ENTITY_UPDATE + "')")
    LegalEntityAttributionResponse attributeOrgUnit(@PathVariable long orgUnitId, @RequestBody AttributeLegalEntityRequest request) {
        return LegalEntityAttributionResponse.of(attribute.execute(AttributionKind.ORG_UNIT, orgUnitId, request.legalEntityId(),
                request.effectiveFrom()));
    }

    @PostMapping("/org-unit-legal-entities/{orgUnitId}:end")
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.LEGAL_ENTITY_UPDATE + "')")
    LegalEntityAttributionResponse endOrgUnit(@PathVariable long orgUnitId, @RequestBody EndLegalEntityAttributionRequest request) {
        return LegalEntityAttributionResponse.of(end.execute(AttributionKind.ORG_UNIT, orgUnitId, request.effectiveTo()));
    }

    @GetMapping("/employee-legal-entities/{employeeId}")
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.ORG_UNIT_READ + "')")
    LegalEntityAttributionResponse ofEmployee(@PathVariable long employeeId) {
        return LegalEntityAttributionResponse.of(get.execute(AttributionKind.EMPLOYEE, employeeId));
    }

    @PutMapping("/employee-legal-entities/{employeeId}")
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.LEGAL_ENTITY_UPDATE + "')")
    LegalEntityAttributionResponse attributeEmployee(@PathVariable long employeeId, @RequestBody AttributeLegalEntityRequest request) {
        return LegalEntityAttributionResponse.of(attribute.execute(AttributionKind.EMPLOYEE, employeeId, request.legalEntityId(),
                request.effectiveFrom()));
    }

    @PostMapping("/employee-legal-entities/{employeeId}:end")
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.LEGAL_ENTITY_UPDATE + "')")
    LegalEntityAttributionResponse endEmployee(@PathVariable long employeeId, @RequestBody EndLegalEntityAttributionRequest request) {
        return LegalEntityAttributionResponse.of(end.execute(AttributionKind.EMPLOYEE, employeeId, request.effectiveTo()));
    }
}
