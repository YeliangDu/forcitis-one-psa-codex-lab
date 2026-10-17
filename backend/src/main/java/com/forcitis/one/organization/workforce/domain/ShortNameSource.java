package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.organization.workforce.api.OrgUnitId;
import java.util.Map;

/** Read-only daily read from the old system until the maintenance page exists. A blank name means the unit has no short name. */
public interface ShortNameSource {
    Map<OrgUnitId, String> fetchShortNames();
}
