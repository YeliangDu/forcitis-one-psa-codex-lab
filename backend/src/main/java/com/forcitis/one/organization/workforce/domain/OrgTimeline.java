package com.forcitis.one.organization.workforce.domain;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The version rows of one org unit or position, superseded ones included, with the org-structure commands that wrote them (by command id): what
 * {@link VersionPlanner} needs to plan a change or its withdrawal.
 */
public record OrgTimeline(List<VersionRow> rows, Map<UUID, OrgStructureCommand> lookup) {

    public List<VersionRow> current() {
        return rows.stream().filter(row -> !row.superseded()).toList();
    }

    public VersionRow row(UUID id) {
        return rows.stream().filter(r -> r.id().equals(id)).findFirst().orElseThrow();
    }
}
