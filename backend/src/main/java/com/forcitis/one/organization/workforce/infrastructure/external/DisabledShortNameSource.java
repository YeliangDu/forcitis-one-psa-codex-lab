package com.forcitis.one.organization.workforce.infrastructure.external;

import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.domain.ShortNameSource;
import com.forcitis.one.organization.workforce.domain.SourceUnavailableException;
import java.util.Map;

/** Used while the old-system read is switched off (forcitis.organization.workforce.legacy.enabled=false). */
final class DisabledShortNameSource implements ShortNameSource {

    @Override
    public Map<OrgUnitId, String> fetchShortNames() {
        throw new SourceUnavailableException("old system access is disabled (forcitis.organization.workforce.legacy.enabled=false)");
    }
}
