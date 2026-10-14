package com.forcitis.one.organization.workforce.infrastructure.external;

import com.forcitis.one.kernel.ExternalSystemUnavailableException;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.domain.ShortNameSource;
import com.forcitis.one.organization.workforce.domain.SourceUnavailableException;
import com.forcitis.one.platform.integration.api.OrgShortNames;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
class ShortNameAdapter implements ShortNameSource {

    private final OrgShortNames integration;

    ShortNameAdapter(OrgShortNames integration) {
        this.integration = integration;
    }

    @Override
    public Map<OrgUnitId, String> fetchShortNames() {
        try {
            Map<OrgUnitId, String> names = new LinkedHashMap<>();
            integration.readShortNames().forEach((id, name) -> names.put(new OrgUnitId(id.value()), name));
            return names;
        } catch (ExternalSystemUnavailableException unavailable) {
            throw new SourceUnavailableException(unavailable.getMessage(), unavailable);
        }
    }
}
