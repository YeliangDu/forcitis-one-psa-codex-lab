package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.domain.OrgSource;
import com.forcitis.one.organization.workforce.domain.SourceMember;
import com.forcitis.one.organization.workforce.domain.SourceSnapshot;
import com.forcitis.one.organization.workforce.domain.SourceUnavailableException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** In-memory stand-in for the A8 views: no real A8 is ever contacted in tests. */
public class FakeOrgSource implements OrgSource {

    public volatile SourceSnapshot snapshot = new SourceSnapshot(List.of(), List.of(), List.of(), List.of());
    public volatile boolean unavailable;
    public volatile List<SourceMember> singleLookupResult = List.of();
    public final AtomicInteger singleLookups = new AtomicInteger();

    @Override
    public SourceSnapshot fetchAll() {
        if (unavailable) {
            throw new SourceUnavailableException("A8 is down (test)");
        }
        return snapshot;
    }

    @Override
    public List<SourceMember> fetchMembersByWecomUserId(WecomUserId wecomUserId) {
        singleLookups.incrementAndGet();
        if (unavailable) {
            throw new SourceUnavailableException("A8 is down (test)");
        }
        return singleLookupResult.stream().filter(m -> wecomUserId.equals(m.wecomUserId())).toList();
    }
}
