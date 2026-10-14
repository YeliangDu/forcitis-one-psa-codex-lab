package com.forcitis.one.organization.workforce.infrastructure.external;

import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.domain.OrgSource;
import com.forcitis.one.organization.workforce.domain.SourceMember;
import com.forcitis.one.organization.workforce.domain.SourceSnapshot;
import com.forcitis.one.organization.workforce.domain.SourceUnavailableException;
import java.util.List;

/** Used while A8 access is switched off (forcitis.organization.workforce.a8.enabled=false): the snapshot is never touched. */
final class DisabledOrgSource implements OrgSource {

    @Override
    public SourceSnapshot fetchAll() {
        throw new SourceUnavailableException("A8 access is disabled (forcitis.organization.workforce.a8.enabled=false)");
    }

    @Override
    public List<SourceMember> fetchMembersByWecomUserId(WecomUserId wecomUserId) {
        return List.of();
    }
}
