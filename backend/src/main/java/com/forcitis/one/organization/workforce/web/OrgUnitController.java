package com.forcitis.one.organization.workforce.web;

import com.forcitis.one.organization.workforce.application.GetOrgUnit;
import com.forcitis.one.organization.workforce.application.ListOrgUnits;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only organisation browser: the org tree and one org unit. Org data is owned by the source; there is nothing to edit here. */
@RestController
@RequestMapping("/api/{version}/organization/org-units")
class OrgUnitController {

    private final ListOrgUnits listOrgUnits;
    private final GetOrgUnit getOrgUnit;

    OrgUnitController(ListOrgUnits listOrgUnits, GetOrgUnit getOrgUnit) {
        this.listOrgUnits = listOrgUnits;
        this.getOrgUnit = getOrgUnit;
    }

    @GetMapping(version = "1")
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.ORG_UNIT_READ + "')")
    List<OrgUnitResponse> list(@RequestParam(defaultValue = "false") boolean includeRevoked) {
        return listOrgUnits.execute(includeRevoked).stream().map(OrgUnitResponse::of).toList();
    }

    @GetMapping(path = "/{orgUnitId}", version = "1")
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.ORG_UNIT_READ + "')")
    ResponseEntity<OrgUnitDetailResponse> get(@PathVariable long orgUnitId) {
        return getOrgUnit.execute(orgUnitId).map(OrgUnitDetailResponse::of)
                .map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }
}
