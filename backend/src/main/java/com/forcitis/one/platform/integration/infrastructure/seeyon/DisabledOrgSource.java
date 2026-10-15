package com.forcitis.one.platform.integration.infrastructure.seeyon;

import com.forcitis.one.kernel.ExternalSystemUnavailableException;
import com.forcitis.one.platform.integration.api.OrgSource;
import com.forcitis.one.platform.integration.api.SourceOrgSnapshot;
import com.forcitis.one.platform.integration.api.SourcePerson;
import com.forcitis.one.platform.integration.api.StaffLookup;
import java.util.List;

final class DisabledOrgSource implements OrgSource {

    @Override
    public SourceOrgSnapshot readSnapshot() {
        throw new ExternalSystemUnavailableException("organisation source access is disabled (forcitis.platform.integration.seeyon.views.enabled=false)");
    }

    @Override
    public List<SourcePerson> findStaffByLogin(StaffLookup lookup) {
        return List.of();
    }
}
