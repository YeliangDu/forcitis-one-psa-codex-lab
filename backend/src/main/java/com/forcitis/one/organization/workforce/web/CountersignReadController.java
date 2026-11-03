package com.forcitis.one.organization.workforce.web;

import com.forcitis.one.kernel.CursorPage;
import com.forcitis.one.organization.workforce.api.AttributionCountersignId;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.application.CountersignReadViews.BasisLine;
import com.forcitis.one.organization.workforce.application.GetAttributionCountersign;
import com.forcitis.one.organization.workforce.application.ListAttributionCountersignBases;
import com.forcitis.one.organization.workforce.application.PreviewMergeAttribution;
import com.forcitis.one.organization.workforce.web.CountersignReadResponses.BasisLineResponse;
import com.forcitis.one.organization.workforce.web.CountersignReadResponses.MergeLineResponse;
import com.forcitis.one.organization.workforce.web.CountersignReadResponses.MergePreviewResponse;
import com.forcitis.one.organization.workforce.web.CountersignReadResponses.RowResponse;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The decider's reads of a legal-entity countersign and the merge rehearsal (#910 amendment B; paths ruled by one-design on #910, comment 6588705997; the basis path is flat by lead ruling, comment 6611676984, because G41 allows no sub-resource). */
@RestController
@RequestMapping("/api/v1/organization")
class CountersignReadController {
    private final GetAttributionCountersign get;
    private final ListAttributionCountersignBases listBases;
    private final PreviewMergeAttribution previewMerge;

    CountersignReadController(GetAttributionCountersign get, ListAttributionCountersignBases listBases, PreviewMergeAttribution previewMerge) {
        this.get = get;
        this.listBases = listBases;
        this.previewMerge = previewMerge;
    }

    @GetMapping("/attribution-countersigns/{attributionCountersignId}")
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.LEGAL_ENTITY_ATTRIBUTION_APPROVE + "')")
    RowResponse row(@PathVariable UUID attributionCountersignId) {
        return RowResponse.of(get.execute(new AttributionCountersignId(attributionCountersignId)));
    }

    @GetMapping("/attribution-countersign-bases/{orgStructureCommandId}")
    @PreAuthorize("hasAnyAuthority('" + WorkforcePermissions.LEGAL_ENTITY_ATTRIBUTION_APPROVE + "', '" + WorkforcePermissions.ORG_STRUCTURE_COMMAND_CREATE + "')")
    CursorPage<BasisLineResponse> bases(@PathVariable UUID orgStructureCommandId, @RequestParam(required = false) Integer limit, @RequestParam(required = false) String cursor) {
        CursorPage<BasisLine> page = listBases.execute(new OrgStructureCommandId(orgStructureCommandId), limit, cursor);
        return CursorPage.of(page.items().stream().map(BasisLineResponse::of).toList(), page.nextCursor());
    }

    @GetMapping("/org-units/{orgUnitId}:preview-merge")
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.ORG_STRUCTURE_COMMAND_CREATE + "')")
    MergePreviewResponse previewMerge(@PathVariable UUID orgUnitId, @RequestParam UUID mergeIntoOrgUnitId, @RequestParam LocalDate effectiveDate) {
        return new MergePreviewResponse(previewMerge.execute(new OrgUnitId(orgUnitId), new OrgUnitId(mergeIntoOrgUnitId), effectiveDate).stream().map(MergeLineResponse::of).toList());
    }
}
