package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.organization.workforce.api.OrgUnitId;
import java.util.Map;

/** Read-only daily read from the old system until the maintenance page exists. */
public interface ShortNameSource {
    Map<OrgUnitId, String> fetchShortNames();
}
