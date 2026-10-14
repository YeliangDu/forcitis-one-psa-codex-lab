package com.forcitis.one.platform.integration.api;

import java.util.Map;

/** Org unit short names, read daily and read-only from the old system until a maintenance page exists. */
public interface OrgShortNames {

    /** Org unit id to short name; a unit without a short name maps to null. */
    Map<SourceUnitId, String> readShortNames();
}
