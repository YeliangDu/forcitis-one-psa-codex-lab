package com.forcitis.one.organization.workforce.web;

import com.forcitis.one.organization.workforce.application.GetOrgUnit;
import com.forcitis.one.organization.workforce.application.ListOrgUnits;
import com.forcitis.one.kernel.OrgUnitAccess;
import com.forcitis.one.kernel.UserId;
import java.security.Principal;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organization/org-units")
class OrgUnitController {
    private final ListOrgUnits listOrgUnits;
    private final GetOrgUnit getOrgUnit;
    private final OrgUnitAccess access;

    OrgUnitController(ListOrgUnits listOrgUnits, GetOrgUnit getOrgUnit, OrgUnitAccess access) {
        this.access = access;
        this.listOrgUnits = listOrgUnits;
        this.getOrgUnit = getOrgUnit;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.ORG_UNIT_READ + "')")
    List<OrgUnitResponse> list(@RequestParam(defaultValue = "false") boolean includeRevoked) {
        return listOrgUnits.execute(includeRevoked, access.visibleOrgUnits(WorkforcePermissions.ORG_UNIT_READ)).stream().map(OrgUnitResponse::of).toList();
    }

    @GetMapping(path = "/{orgUnitId}")
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.ORG_UNIT_READ + "')")
    ResponseEntity<OrgUnitDetailResponse> get(@PathVariable long orgUnitId, Principal caller) {
        return getOrgUnit.execute(orgUnitId, access.visibleOrgUnits(WorkforcePermissions.ORG_UNIT_READ), new UserId(Long.parseLong(caller.getName()))).map(OrgUnitDetailResponse::of)
                .map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }
}
