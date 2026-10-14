package com.forcitis.one.platform.integration.api;

import java.util.Map;

/** Read-only from the old system until a maintenance page exists. */
public interface OrgShortNames {

    /** A unit without a short name maps to null. */
    Map<SourceUnitId, String> readShortNames();
}
