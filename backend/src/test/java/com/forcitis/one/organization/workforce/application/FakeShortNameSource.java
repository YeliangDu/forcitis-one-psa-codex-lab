package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.domain.ShortNameSource;
import com.forcitis.one.organization.workforce.domain.SourceUnavailableException;
import java.util.Map;

public class FakeShortNameSource implements ShortNameSource {
    public volatile Map<OrgUnitId, String> names = Map.of();
    public volatile boolean unavailable;

    @Override
    public Map<OrgUnitId, String> fetchShortNames() {
        if (unavailable) {
            throw new SourceUnavailableException("old system is down (test)");
        }
        return names;
    }
}
