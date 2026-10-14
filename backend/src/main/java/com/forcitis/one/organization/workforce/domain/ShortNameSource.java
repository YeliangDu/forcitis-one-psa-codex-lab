package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.organization.workforce.api.OrgUnitId;
import java.util.Map;

/** Org unit short names, read daily from the old system (read-only) until the maintenance page exists (REIMAGINED_ARCHITECTURE §3.3). */
public interface ShortNameSource {

    /** Org unit id to short name; a unit without a short name maps to null. */
    Map<OrgUnitId, String> fetchShortNames();
}
