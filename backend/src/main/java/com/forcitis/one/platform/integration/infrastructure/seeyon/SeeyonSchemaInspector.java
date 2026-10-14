package com.forcitis.one.platform.integration.infrastructure.seeyon;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the startup check needs to know about the Seeyon database. A port so tests can replace it with an in-memory
 * stand-in (the check is switchable and never needs a real Seeyon database in tests).
 */
interface SeeyonSchemaInspector {

    /** View name (upper case) to its column names (upper case) for every object the account can see in the Seeyon view schema. */
    Map<String, Set<String>> viewColumns();

    int countRelationshipsOfType(long relationshipTypeId);

    int countDeliveryUnitsMarked(String enumId);

    int countFeeEarnersMarked(String enumId);

    /** Database-level permissions the account holds that a read-only view account must not have (empty when fine). */
    List<String> excessivePermissions();
}
