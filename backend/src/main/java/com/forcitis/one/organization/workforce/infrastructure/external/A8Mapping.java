package com.forcitis.one.organization.workforce.infrastructure.external;

/**
 * The A8-instance specific ids, from configuration (RULE-356): relationship type ids of "org unit head" and "regional
 * receivables manager", and the enum item ids meaning "yes" for the delivery-unit marker and the fee-earner marker.
 * Wrong values would silently turn every head / marker into "none", so the startup check verifies them against the data.
 */
record A8Mapping(long headRelationshipTypeId, long regionalReceivablesManagerRelationshipTypeId,
                 String deliveryUnitYesEnumId, String feeEarnerYesEnumId) {

    /** Problems of the configuration itself (before looking at the data). */
    java.util.List<String> configurationProblems() {
        java.util.List<String> problems = new java.util.ArrayList<>();
        if (headRelationshipTypeId == 0) {
            problems.add("forcitis.organization.workforce.a8.head-relationship-type-id is not configured");
        }
        if (regionalReceivablesManagerRelationshipTypeId == 0) {
            problems.add("forcitis.organization.workforce.a8.regional-receivables-manager-relationship-type-id is not configured");
        }
        if (headRelationshipTypeId != 0 && headRelationshipTypeId == regionalReceivablesManagerRelationshipTypeId) {
            problems.add("the head and regional receivables manager relationship type ids must differ");
        }
        if (deliveryUnitYesEnumId == null || deliveryUnitYesEnumId.isBlank()) {
            problems.add("forcitis.organization.workforce.a8.delivery-unit-yes-enum-id is not configured");
        }
        if (feeEarnerYesEnumId == null || feeEarnerYesEnumId.isBlank()) {
            problems.add("forcitis.organization.workforce.a8.fee-earner-yes-enum-id is not configured");
        }
        return problems;
    }
}
