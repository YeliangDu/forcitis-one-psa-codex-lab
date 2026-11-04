package com.forcitis.one.organization.attendance.web;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.attendance.application.ListStandardHoursVersions;
import com.forcitis.one.organization.attendance.application.PublishStandardHoursVersion;
import com.forcitis.one.organization.attendance.application.PublishedStandardHours;
import com.forcitis.one.organization.attendance.application.VoidStandardHoursVersion;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organization/standard-hours-versions")
class StandardHoursVersionController {
    private final PublishStandardHoursVersion publish;
    private final VoidStandardHoursVersion voidVersion;
    private final ListStandardHoursVersions list;

    StandardHoursVersionController(PublishStandardHoursVersion publish, VoidStandardHoursVersion voidVersion, ListStandardHoursVersions list) {
        this.publish = publish;
        this.voidVersion = voidVersion;
        this.list = list;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + AttendancePermissions.STANDARD_HOURS_PUBLISH + "')")
    PublishedStandardHoursResponse publish(@RequestBody PublishStandardHoursRequest request) {
        PublishedStandardHours published = publish.execute(request.toCommand());
        return new PublishedStandardHoursResponse(published.standardHoursVersionId(), published.versionNumber());
    }

    @PostMapping("/{standardHoursVersionId}:void")
    @PreAuthorize("hasAuthority('" + AttendancePermissions.STANDARD_HOURS_PUBLISH + "')")
    VoidedStandardHoursResponse voidVersion(@PathVariable UUID standardHoursVersionId, @RequestBody VoidStandardHoursRequest request) {
        voidVersion.execute(standardHoursVersionId, request.requiredReason());
        return new VoidedStandardHoursResponse(standardHoursVersionId);
    }

    /** Without legalEntityId the DEFAULT scope is listed. */
    @GetMapping
    @PreAuthorize("hasAuthority('" + AttendancePermissions.STANDARD_HOURS_READ + "')")
    StandardHoursVersionsResponse versions(@RequestParam(required = false) UUID legalEntityId) {
        return new StandardHoursVersionsResponse(list.execute(legalEntityId == null ? null : new LegalEntityId(legalEntityId))
                .stream().map(StandardHoursVersionResponse::of).toList());
    }
}
