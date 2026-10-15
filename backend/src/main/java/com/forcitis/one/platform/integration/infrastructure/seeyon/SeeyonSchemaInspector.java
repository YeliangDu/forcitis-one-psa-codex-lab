package com.forcitis.one.platform.integration.infrastructure.seeyon;

import java.util.Map;
import java.util.Set;

interface SeeyonSchemaInspector {

    Map<String, Set<String>> viewColumns();

    int countUnitsAtPath(String path);

    int countRelationshipsOfType(long relationshipTypeId);

    int countDeliveryUnitsMarked(String enumId);

    int countFeeEarnersMarked(String enumId);
}
