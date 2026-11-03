package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.kernel.CursorPage;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.AccountEmployees;
import com.forcitis.one.kernel.EmployeeAccountAccess;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.ExpectedVersion;
import com.forcitis.one.kernel.OrgUnitAccess;
import com.forcitis.one.kernel.PageRequest;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.ProfileChangeRequestId;
import com.forcitis.one.organization.workforce.api.ProfileChangeRequestView;
import com.forcitis.one.organization.workforce.api.ProfileChangeRequests;
import com.forcitis.one.organization.workforce.api.ProfileChangeStatus;
import com.forcitis.one.organization.workforce.api.ProfileVoidReason;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.domain.OrgSnapshotQueries;
import com.forcitis.one.organization.workforce.domain.PersonalProfile;
import com.forcitis.one.organization.workforce.domain.PersonnelCommandRepository;
import com.forcitis.one.organization.workforce.domain.ProfileChangeRequest;
import com.forcitis.one.organization.workforce.domain.ProfileChangeRequestRepository;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The second person of a profile change request (design note wecom-rebind-second-person.md section 5). The employee row is locked first, then the request is read
 * again under that lock; the sessions and browser identifiers are ended in the confirming transaction, so a failure leaves the profile unchanged and the request pending.
 * A void is returned, not thrown, so that its status and audit entry commit.
 */
@Component
class ProfileChangeRequestService implements ProfileChangeRequests {
    static final String PERMISSION = PersonnelCommandService.PERMISSION;
    static final String LIST = "profile-change-requests";

    private final ProfileChangeRequestRepository requests;
    private final PersonnelCommandRepository employees;
    private final AuditLog audit;
    private final CurrentUser currentUser;
    private final AccountEmployees accountEmployees;
    private final EmployeeAccountAccess access;
    private final OrgUnitAccess unitAccess;
    private final OrgSnapshotQueries snapshots;
    private final EmployeeProfileService profiles;
    private final Clock clock;

    ProfileChangeRequestService(ProfileChangeRequestRepository requests, PersonnelCommandRepository employees, AuditLog audit, CurrentUser currentUser,
                                AccountEmployees accountEmployees, EmployeeAccountAccess access, OrgUnitAccess unitAccess, OrgSnapshotQueries snapshots,
                                EmployeeProfileService profiles, Clock clock) {
        this.profiles = profiles;
        this.requests = requests;
        this.employees = employees;
        this.audit = audit;
        this.currentUser = currentUser;
        this.accountEmployees = accountEmployees;
        this.access = access;
        this.unitAccess = unitAccess;
        this.snapshots = snapshots;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('" + PERMISSION + "')")
    public CursorPage<ProfileChangeRequestView> pending(PageRequest page) {
        OrgUnitAccess.Visible visible = unitAccess.visibleOrgUnits(PERMISSION);
        Set<OrgUnitId> allowed = visible.everything() ? Set.of() : OrgUnitVisibility.idsIn(snapshots.findAllOrgUnits(), visible);
        List<ProfileChangeRequest> items = new ArrayList<>();
        boolean more = false;
        UUID after = page.cursorUuid();
        while (!more) {
            List<ProfileChangeRequest> found = requests.pending(after, page.limit() + 1);
            for (ProfileChangeRequest request : found) {
                if (visible.everything() || snapshots.findStaff(request.employeeId()).map(staff -> allowed.contains(staff.assignment().orgUnitId())).orElse(false)) {
                    more = items.size() == page.limit();
                    if (more) {
                        break;
                    }
                    items.add(request);
                }
                after = request.id().value();
            }
            if (found.size() <= page.limit()) {
                break;
            }
        }
        return CursorPage.of(items.stream().map(ProfileChangeRequestService::view).toList(),
                more ? PageRequest.encode(LIST, items.getLast().id().value().toString()) : null);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('" + PERMISSION + "')")
    public ProfileChangeRequestView confirm(ProfileChangeRequestId id, ExpectedVersion expected) {
        ProfileChangeRequest request = lockedPending(id, expected);
        UserId by = currentUser.userId();
        if (by.equals(request.requestedBy()) || isTheEmployee(by, request)) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_PROFILE_CHANGE_CONFIRMER_NOT_ALLOWED, ErrorKind.FORBIDDEN,
                    "the requester and the employee themself do not confirm the change");
        }
        Instant now = clock.instant();
        long loaded = request.version();
        PersonalProfile current = employees.profileOf(request.employeeId()).orElseThrow();
        ProfileVoidReason moved = voidReason(request, current);
        if (moved != null) {
            request.voidWith(moved, by, now);
            requests.update(request, loaded, now);
            audit.record(new AuditEntry("organization.workforce.employee-profile.change-voided", by, null, subject(request),
                    "profile change request " + request.id().value() + " voided: " + moved + "; nothing was applied"));
            return view(request);
        }
        PersonalProfile.Change change = request.after().changeFrom(current).orElseThrow();
        employees.updateProfile(request.employeeId(), request.after(), now);
        access.revokeSessions(request.employeeId());
        request.confirm(by, now);
        requests.update(request, loaded, now);
        profiles.recordChanged(by, request.employeeId(), "profile changed (request " + request.id().value() + ", requested by " + request.requestedBy().value() + ")", change);
        return view(request);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('" + PERMISSION + "')")
    public ProfileChangeRequestView reject(ProfileChangeRequestId id, String reason, ExpectedVersion expected) {
        ProfileChangeRequest request = lockedPending(id, expected);
        UserId by = currentUser.userId();
        if (isTheEmployee(by, request)) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_PROFILE_CHANGE_CONFIRMER_NOT_ALLOWED, ErrorKind.FORBIDDEN,
                    "the employee themself does not reject the change");
        }
        Instant now = clock.instant();
        long loaded = request.version();
        request.reject(by, reason, now);
        requests.update(request, loaded, now);
        audit.record(new AuditEntry("organization.workforce.employee-profile.change-rejected", by, null, subject(request),
                "profile change request " + request.id().value() + (by.equals(request.requestedBy()) ? " withdrawn by its requester" : " rejected") + "; nothing was applied"));
        return view(request);
    }

    /** The request read under the lock of its employee, at the version the caller based the call on, and still pending. */
    private ProfileChangeRequest lockedPending(ProfileChangeRequestId id, ExpectedVersion expected) {
        ProfileChangeRequest first = requests.find(id).orElseThrow(() -> notFound());
        if (!employees.lockEmployee(first.employeeId())) {
            throw notFound();
        }
        ProfileChangeRequest request = requests.find(id).orElseThrow(() -> notFound());
        expected.check(request.version());
        if (request.status() != ProfileChangeStatus.PENDING) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_PROFILE_CHANGE_REQUEST_NOT_PENDING, ErrorKind.CONFLICT, "the request is already " + request.status());
        }
        return request;
    }

    /** Why the request cannot be applied now, or null when it can: the profile is not the one it was made against, or its proposed WeCom user id was taken meanwhile. */
    private ProfileVoidReason voidReason(ProfileChangeRequest request, PersonalProfile current) {
        if (!request.base().equals(current)) {
            return ProfileVoidReason.BASE_CHANGED;
        }
        boolean moves = !Objects.equals(request.after().wecomUserId(), current.wecomUserId());
        if (moves && request.after().wecomUserId() != null && employees.wecomUserIdHeld(request.after().wecomUserId(), request.employeeId())) {
            return ProfileVoidReason.WECOM_USER_ID_TAKEN;
        }
        return null;
    }

    private boolean isTheEmployee(UserId by, ProfileChangeRequest request) {
        return accountEmployees.employeeOf(by).filter(request.employeeId()::equals).isPresent();
    }

    private static AuditSubject subject(ProfileChangeRequest request) {
        return AuditSubject.of(AuditSubjectKind.EMPLOYEE, request.employeeId().value());
    }

    private static DomainException notFound() {
        return new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_PROFILE_CHANGE_REQUEST_NOT_FOUND, ErrorKind.NOT_FOUND, "no such profile change request");
    }

    private static ProfileChangeRequestView view(ProfileChangeRequest request) {
        PersonalProfile proposed = request.after();
        return new ProfileChangeRequestView(request.id(), request.employeeId(), request.requestedBy(), request.requestedAt(), request.status(), proposed.displayName(), proposed.gender(),
                proposed.email(), proposed.wecomUserId(), request.decidedBy(), request.decidedAt(), request.voidReason(), request.version());
    }
}
