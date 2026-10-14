package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.domain.OrgSource;
import com.forcitis.one.organization.workforce.domain.SourceMember;
import com.forcitis.one.organization.workforce.domain.SourceSnapshot;
import com.forcitis.one.organization.workforce.domain.SourceUnavailableException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class FakeOrgSource implements OrgSource {
    public volatile SourceSnapshot snapshot = new SourceSnapshot(List.of(), List.of(), List.of(), List.of());
    public volatile boolean unavailable;
    public volatile RuntimeException failure;
    public volatile List<SourceMember> singleLookupResult = List.of();
    public final AtomicInteger singleLookups = new AtomicInteger();

    @Override
    public SourceSnapshot fetchAll() {
        if (unavailable) {
            throw new SourceUnavailableException("source is down (test)");
        }
        if (failure != null) {
            throw failure;
        }
        return snapshot;
    }

    @Override
    public List<SourceMember> fetchMembersByWecomUserId(WecomUserId wecomUserId) {
        singleLookups.incrementAndGet();
        if (unavailable) {
            throw new SourceUnavailableException("source is down (test)");
        }
        return singleLookupResult.stream().filter(m -> wecomUserId.equals(m.wecomUserId())).toList();
    }

    @Override
    public List<SourceMember> fetchMembersByLoginName(String loginName) {
        singleLookups.incrementAndGet();
        if (unavailable) {
            throw new SourceUnavailableException("source is down (test)");
        }
        return singleLookupResult.stream().filter(m -> loginName.equals(m.loginName())).toList();
    }
}
