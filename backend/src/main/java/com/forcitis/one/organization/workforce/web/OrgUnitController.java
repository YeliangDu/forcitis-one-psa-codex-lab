package com.forcitis.one.organization.workforce.web;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.application.GetOrgUnit;
import com.forcitis.one.organization.workforce.application.ListOrgUnits;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organization/org-units")
class OrgUnitController {
    private final ListOrgUnits listOrgUnits;
    private final GetOrgUnit getOrgUnit;

    OrgUnitController(ListOrgUnits listOrgUnits, GetOrgUnit getOrgUnit) {
        this.listOrgUnits = listOrgUnits;
        this.getOrgUnit = getOrgUnit;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.ORG_UNIT_READ + "')")
    List<OrgUnitResponse> list(@RequestParam(defaultValue = "false") boolean includeRevoked) {
        return listOrgUnits.execute(includeRevoked).stream().map(OrgUnitResponse::of).toList();
    }

    @GetMapping(path = "/{orgUnitId}")
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.ORG_UNIT_READ + "')")
    OrgUnitDetailResponse get(@PathVariable UUID orgUnitId) {
        return getOrgUnit.execute(orgUnitId)
                .map(OrgUnitDetailResponse::of).orElseThrow(() -> new DomainException(
                        WorkforceError.ORGANIZATION_WORKFORCE_ORG_UNIT_NOT_FOUND, ErrorKind.NOT_FOUND, "no org unit " + orgUnitId));
    }
}
