package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.organization.workforce.api.ProfileChangeRequestId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** The profile change requests of the workforce schema (table employee_profile_change_requests): inserted pending, decided once. */
public interface ProfileChangeRequestRepository {

    void insert(ProfileChangeRequest request, Instant at);

    Optional<ProfileChangeRequest> find(ProfileChangeRequestId id);

    /** Writes the decision the aggregate carries; only the version it was loaded at is replaced, otherwise a DomainException (CONFLICT). */
    void update(ProfileChangeRequest request, long loadedVersion, Instant at);

    boolean pendingFor(EmployeeId employee);

    /** Up to limit requests that wait, oldest first, after the request the cursor names (a request that is still in the table). */
    List<ProfileChangeRequest> pending(@Nullable UUID after, int limit);

    /** When a second person last confirmed a request of the employee that cleared the WeCom user id (proposed WeCom user id empty); empty when there is none. */
    Optional<Instant> lastConfirmedClearAt(EmployeeId employee);
}
