package com.forcitis.one.platform.identity.api;

import com.forcitis.one.kernel.UserId;
import java.util.HashSet;
import java.util.Set;

/** Fail-closed: a scope with no grants, no people and no unrestricted flag permits nothing. */
public record AccessScope(boolean unrestricted, Set<AccessGrant> grants, Set<UserId> ownerIds) {

    public AccessScope {
        grants = Set.copyOf(grants);
        ownerIds = Set.copyOf(ownerIds);
    }

    public static AccessScope none() {
        return new AccessScope(false, Set.of(), Set.of());
    }

    public static AccessScope everything() {
        return new AccessScope(true, Set.of(), Set.of());
    }

    public boolean isEmpty() {
        return !unrestricted && grants.isEmpty() && ownerIds.isEmpty();
    }

    public boolean permits(AccessTarget target) {
        if (unrestricted) {
            return true;
        }
        return ownerIds.contains(target.ownerId()) || grants.stream().anyMatch(grant -> grant.covers(target));
    }

    public AccessScope union(AccessScope other) {
        if (unrestricted || other.unrestricted) {
            return everything();
        }
        Set<AccessGrant> allGrants = new HashSet<>(grants);
        allGrants.addAll(other.grants);
        Set<UserId> allOwners = new HashSet<>(ownerIds);
        allOwners.addAll(other.ownerIds);
        return new AccessScope(false, allGrants, allOwners);
    }
}
