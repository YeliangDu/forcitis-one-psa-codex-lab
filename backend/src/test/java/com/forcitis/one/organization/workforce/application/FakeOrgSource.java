package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.platform.integration.api.OrgSource;
import com.forcitis.one.platform.integration.api.SourcePerson;
import com.forcitis.one.platform.integration.api.SourceOrgSnapshot;
import com.forcitis.one.kernel.ExternalSystemUnavailableException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class FakeOrgSource implements OrgSource {
    public volatile SourceOrgSnapshot snapshot = new SourceOrgSnapshot(List.of(), List.of(), List.of(), List.of());
    public volatile boolean unavailable;
    public volatile RuntimeException failure;
    public volatile Runnable afterRead = () -> { };
    public volatile List<SourcePerson> singleLookupResult = List.of();
    public final AtomicInteger singleLookups = new AtomicInteger();

    @Override
    public SourceOrgSnapshot readSnapshot() {
        if (unavailable) {
            throw new ExternalSystemUnavailableException("source is down (test)");
        }
        if (failure != null) {
            throw failure;
        }
        afterRead.run();
        return snapshot;
    }

    @Override
    public List<SourcePerson> findStaffByWecomUser(WecomUserId wecomUserId) {
        singleLookups.incrementAndGet();
        if (unavailable) {
            throw new ExternalSystemUnavailableException("source is down (test)");
        }
        return singleLookupResult.stream().filter(m -> wecomUserId.equals(m.wecomUserId())).toList();
    }

    @Override
    public List<SourcePerson> findStaffByLoginName(String loginName) {
        singleLookups.incrementAndGet();
        if (unavailable) {
            throw new ExternalSystemUnavailableException("source is down (test)");
        }
        return singleLookupResult.stream().filter(m -> loginName.equals(m.loginName())).toList();
    }
}
