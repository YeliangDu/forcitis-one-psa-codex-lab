package com.forcitis.one.organization.workforce.web;

import com.forcitis.one.kernel.OrgUnitAccess;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.AttributionKind;
import com.forcitis.one.organization.workforce.application.AttributeLegalEntity;
import com.forcitis.one.organization.workforce.application.EndLegalEntityAttribution;
import com.forcitis.one.organization.workforce.application.GetLegalEntityAttribution;
import java.security.Principal;
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
    private final OrgUnitAccess access;

    LegalEntityAttributionController(GetLegalEntityAttribution get, AttributeLegalEntity attribute, EndLegalEntityAttribution end,
            OrgUnitAccess access) {
        this.access = access;
        this.get = get;
        this.attribute = attribute;
        this.end = end;
    }

    @GetMapping("/org-unit-legal-entities/{orgUnitId}")
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.ORG_UNIT_READ + "')")
    LegalEntityAttributionResponse ofOrgUnit(@PathVariable long orgUnitId, Principal caller) {
        return LegalEntityAttributionResponse.of(get.execute(AttributionKind.ORG_UNIT, orgUnitId,
                access.visibleOrgUnits(WorkforcePermissions.ORG_UNIT_READ), actor(caller)));
    }

    @PutMapping("/org-unit-legal-entities/{orgUnitId}")
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.LEGAL_ENTITY_UPDATE + "')")
    LegalEntityAttributionResponse attributeOrgUnit(@PathVariable long orgUnitId, @RequestBody AttributeLegalEntityRequest request, Principal caller) {
        return LegalEntityAttributionResponse.of(attribute.execute(AttributionKind.ORG_UNIT, orgUnitId, request.legalEntityId(),
                request.effectiveFrom(), actor(caller)));
    }

    @PostMapping("/org-unit-legal-entities/{orgUnitId}:end")
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.LEGAL_ENTITY_UPDATE + "')")
    LegalEntityAttributionResponse endOrgUnit(@PathVariable long orgUnitId, @RequestBody EndLegalEntityAttributionRequest request, Principal caller) {
        return LegalEntityAttributionResponse.of(end.execute(AttributionKind.ORG_UNIT, orgUnitId, request.effectiveTo(), actor(caller)));
    }

    @GetMapping("/employee-legal-entities/{employeeId}")
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.ORG_UNIT_READ + "')")
    LegalEntityAttributionResponse ofEmployee(@PathVariable long employeeId, Principal caller) {
        return LegalEntityAttributionResponse.of(get.execute(AttributionKind.EMPLOYEE, employeeId,
                access.visibleOrgUnits(WorkforcePermissions.ORG_UNIT_READ), actor(caller)));
    }

    @PutMapping("/employee-legal-entities/{employeeId}")
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.LEGAL_ENTITY_UPDATE + "')")
    LegalEntityAttributionResponse attributeEmployee(@PathVariable long employeeId, @RequestBody AttributeLegalEntityRequest request, Principal caller) {
        return LegalEntityAttributionResponse.of(attribute.execute(AttributionKind.EMPLOYEE, employeeId, request.legalEntityId(),
                request.effectiveFrom(), actor(caller)));
    }

    @PostMapping("/employee-legal-entities/{employeeId}:end")
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.LEGAL_ENTITY_UPDATE + "')")
    LegalEntityAttributionResponse endEmployee(@PathVariable long employeeId, @RequestBody EndLegalEntityAttributionRequest request, Principal caller) {
        return LegalEntityAttributionResponse.of(end.execute(AttributionKind.EMPLOYEE, employeeId, request.effectiveTo(), actor(caller)));
    }

    private static UserId actor(Principal caller) {
        return new UserId(Long.parseLong(caller.getName()));
    }
}
