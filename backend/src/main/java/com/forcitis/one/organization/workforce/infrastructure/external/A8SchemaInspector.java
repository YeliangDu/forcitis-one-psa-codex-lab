package com.forcitis.one.organization.workforce.infrastructure.external;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the startup check needs to know about the A8 database. A port so tests can replace it with an in-memory
 * stand-in (the check is switchable and never needs a real A8 in tests).
 */
interface A8SchemaInspector {

    /** View name (upper case) to its column names (upper case) for every object the account can see in the A8 view schema. */
    Map<String, Set<String>> viewColumns();

    int countRelationshipsOfType(long relationshipTypeId);

    int countDeliveryUnitsMarked(String enumId);

    int countFeeEarnersMarked(String enumId);

    /** Database-level permissions the account holds that a read-only view account must not have (empty when fine). */
    List<String> excessivePermissions();
}
