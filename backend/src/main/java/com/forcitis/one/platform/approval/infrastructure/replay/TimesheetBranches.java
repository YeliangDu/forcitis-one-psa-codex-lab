package com.forcitis.one.platform.approval.infrastructure.replay;

import java.util.List;
import java.util.Map;

final class TimesheetBranches {

    record Branch(String flag, String nodeKey, Map<String, String> rule) {
    }

    static final List<Branch> ALL = List.of(
            new Branch("projectHours", "projectManagerReview", Map.of("type", "FORM_FIELD_USERS", "field", "projectManagerId")),
            new Branch("preSalesHours", "orgUnitHeadApproval", Map.of("type", "FORM_FIELD_USERS", "field", "orgUnitHeadId")),
            new Branch("overheadHours", "supervisorReview", Map.of("type", "INITIATOR_SUPERVISOR", "level", "1")));

    private TimesheetBranches() {
    }

    static Branch byNode(String nodeKey) {
        return ALL.stream().filter(b -> b.nodeKey().equals(nodeKey)).findFirst().orElse(null);
    }
}
