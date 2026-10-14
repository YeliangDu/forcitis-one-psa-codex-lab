package com.forcitis.one.platform.integration.infrastructure.seeyon;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** A port so tests can replace it with an in-memory stand-in. */
interface SeeyonSchemaInspector {

    Map<String, Set<String>> viewColumns();

    int countRelationshipsOfType(long relationshipTypeId);

    int countDeliveryUnitsMarked(String enumId);

    int countFeeEarnersMarked(String enumId);

    List<String> excessivePermissions();
}
