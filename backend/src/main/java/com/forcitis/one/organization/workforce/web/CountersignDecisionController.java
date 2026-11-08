package com.forcitis.one.organization.workforce.web;

import com.forcitis.one.organization.workforce.api.WorkforcePermissions;

import com.forcitis.one.organization.workforce.api.ApproveAttributionCountersign;
import com.forcitis.one.organization.workforce.api.AttributionCountersignId;
import com.forcitis.one.organization.workforce.api.AttributionCountersigns;
import com.forcitis.one.organization.workforce.api.RejectAttributionCountersign;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The finance head's decision on a legal-entity countersign request (#1170); the decider is the signed-in caller and the use case judges who may decide and the reason. */
@RestController
@RequestMapping("/api/v1/organization/attribution-countersigns")
class CountersignDecisionController {
    private final AttributionCountersigns countersigns;

    CountersignDecisionController(AttributionCountersigns countersigns) {
        this.countersigns = countersigns;
    }

    @PostMapping("/{attributionCountersignId}:approve")
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.LEGAL_ENTITY_ATTRIBUTION_APPROVE + "')")
    void approve(@PathVariable UUID attributionCountersignId, @RequestBody(required = false) ReasonRequest request) {
        countersigns.approve(new ApproveAttributionCountersign(new AttributionCountersignId(attributionCountersignId), request == null ? null : request.reason()));
    }

    @PostMapping("/{attributionCountersignId}:reject")
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.LEGAL_ENTITY_ATTRIBUTION_APPROVE + "')")
    void reject(@PathVariable UUID attributionCountersignId, @RequestBody ReasonRequest request) {
        countersigns.reject(new RejectAttributionCountersign(new AttributionCountersignId(attributionCountersignId), request.reason()));
    }
}
