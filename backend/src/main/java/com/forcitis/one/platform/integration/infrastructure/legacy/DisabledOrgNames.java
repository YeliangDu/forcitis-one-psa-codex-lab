package com.forcitis.one.platform.integration.infrastructure.legacy;

import com.forcitis.one.kernel.ExternalSystemUnavailableException;
import com.forcitis.one.platform.integration.api.OrgShortNames;
import com.forcitis.one.platform.integration.api.SourceUnitId;
import java.util.Map;

final class DisabledOrgNames implements OrgShortNames {

    @Override
    public Map<SourceUnitId, String> readShortNames() {
        throw new ExternalSystemUnavailableException("old system access is disabled (forcitis.platform.integration.legacy.enabled=false)");
    }
}
