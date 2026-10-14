package com.forcitis.one.platform.integration.infrastructure.seeyon;

import java.util.ArrayList;
import java.util.List;

/** Wrong values would silently turn every head / marker into "none", so the startup check verifies them against the data. */
record SeeyonMapping(long headRelationshipTypeId, long regionalReceivablesManagerRelationshipTypeId,
                     String deliveryUnitYesEnumId, String feeEarnerYesEnumId) {

    static final String KEY_PREFIX = "forcitis.platform.integration.seeyon.views.";

    List<String> configurationProblems() {
        List<String> problems = new ArrayList<>();
        if (headRelationshipTypeId == 0) {
            problems.add(KEY_PREFIX + "head-relationship-type-id is not configured");
        }
        if (regionalReceivablesManagerRelationshipTypeId == 0) {
            problems.add(KEY_PREFIX + "regional-receivables-manager-relationship-type-id is not configured");
        }
        if (headRelationshipTypeId != 0 && headRelationshipTypeId == regionalReceivablesManagerRelationshipTypeId) {
            problems.add("the head and regional receivables manager relationship type ids must differ");
        }
        if (deliveryUnitYesEnumId == null || deliveryUnitYesEnumId.isBlank()) {
            problems.add(KEY_PREFIX + "delivery-unit-yes-enum-id is not configured");
        }
        if (feeEarnerYesEnumId == null || feeEarnerYesEnumId.isBlank()) {
            problems.add(KEY_PREFIX + "fee-earner-yes-enum-id is not configured");
        }
        return problems;
    }
}
