package com.forcitis.one.platform.identity.application;

import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import com.forcitis.one.kernel.ExpectedVersion;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.api.RoleCode;
import com.forcitis.one.platform.identity.api.RoleId;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import com.forcitis.one.platform.identity.api.RoleVersionId;
import com.forcitis.one.platform.identity.domain.AdministratorFloor;
import com.forcitis.one.platform.identity.domain.AuthorizationBase;
import com.forcitis.one.platform.identity.domain.AuthorizationRequest;
import org.jspecify.annotations.Nullable;
import com.forcitis.one.platform.identity.domain.GrantChanges;
import com.forcitis.one.platform.identity.domain.HeldGrant;
import com.forcitis.one.platform.identity.domain.Role;
import com.forcitis.one.platform.identity.domain.RoleRepository;
import com.forcitis.one.platform.identity.domain.SensitivePermissions;
import com.forcitis.one.platform.identity.domain.UserAccount;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A role change takes effect at once: the account's sessions are deleted and the cache evicted. Nobody changes their own roles (I-12);
 * a change that gives or takes a sensitive role waits for a second person.
 */
@Service
public class ReplaceAccountRoles {

    private final AccountRepository accounts;
    private final RoleRepository roles;
    private final PrivilegeCeiling ceiling;
    private final SessionRevocation revocation;
    private final AccessCache cache;
    private final DomainEvents events;
    private final CurrentUser currentUser;
    private final AuthorizationGate gate;
    private final AuditLog audit;
    private final AccountGrants grants;
    private final AuthorizationVoiding voiding;
    private final AdministratorFloor floor;
    private final AdministratorFloorNotice notice;
    private final SubjectEmployment employment;

    ReplaceAccountRoles(AccountRepository accounts, RoleRepository roles, PrivilegeCeiling ceiling, SessionRevocation revocation,
            AccessCache cache, DomainEvents events, CurrentUser currentUser, AuthorizationGate gate, AuditLog audit, AccountGrants grants,
            AdministratorFloor floor, AdministratorFloorNotice notice, AuthorizationVoiding voiding, SubjectEmployment employment) {
        this.employment = employment;
        this.voiding = voiding;
        this.grants = grants;
        this.floor = floor;
        this.notice = notice;
        this.accounts = accounts;
        this.roles = roles;
        this.ceiling = ceiling;
        this.revocation = revocation;
        this.cache = cache;
        this.events = events;
        this.currentUser = currentUser;
        this.gate = gate;
        this.audit = audit;
    }

    @Transactional
    public AdminOutcome<Long> execute(UserId userId, Set<String> roleCodes, ExpectedVersion expected) {
        Optional<UserAccount> found = accounts.find(userId);
        if (found.isEmpty()) {
            return new AdminOutcome.Refused<>(IdentityError.PLATFORM_IDENTITY_ACCOUNT_NOT_FOUND);
        }
        UserAccount account = found.get();
        expected.check(account.version());
        UserId caller = currentUser.userId();
        if (account.userId().equals(caller)) {
            audit.recordIndependently(new AuditEntry("platform.identity.grant.refused", caller, null, AuditSubject.of(AuditSubjectKind.USER_ACCOUNT, userId.value()),
                    "nobody changes their own roles"));
            return new AdminOutcome.Refused<>(IdentityError.PLATFORM_IDENTITY_OWN_ROLES_FORBIDDEN);
        }
        List<Role> wanted = new ArrayList<>();
        for (String code : roleCodes) {
            Optional<Role> role = roles.findByCode(new RoleCode(code));
            if (role.isEmpty()) {
                return new AdminOutcome.Refused<>(IdentityError.PLATFORM_IDENTITY_ROLE_NOT_FOUND);
            }
            wanted.add(role.get());
        }
        Set<RoleCode> alreadyHeld = accounts.findAssignedRoleCodes(account.userId());
        if (givesARole(wanted, alreadyHeld) && !employment.isEmployed(account)) {
            return new AdminOutcome.Refused<>(IdentityError.PLATFORM_IDENTITY_EMPLOYEE_SEPARATED);
        }
        if (touchesSensitiveRole(wanted, alreadyHeld)) {
            Set<String> requested = new HashSet<>();
            boolean administrativeRole = false;
            for (Role role : wanted) {
                if (!alreadyHeld.contains(role.code())) {
                    requested.addAll(role.permissionCodes());
                    administrativeRole |= role.administrative();
                }
            }
            if (!ceiling.mayRequest(requested, administrativeRole)) {
                return new AdminOutcome.Refused<>(IdentityError.PLATFORM_IDENTITY_ROLE_NOT_ALLOWED);
            }
            return new AdminOutcome.Awaiting<>(gate.openReplaceRoles(caller, account.userId(), roleCodes, baseOf(account, wanted, alreadyHeld)).value().toString());
        }
        Set<String> wantedPermissions = new HashSet<>();
        boolean administrative = false;
        for (Role role : wanted) {
            if (!alreadyHeld.contains(role.code())) {
                wantedPermissions.addAll(role.permissionCodes());
                administrative |= role.administrative();
            }
        }
        if (!ceiling.mayGrant(wantedPermissions, administrative)) {
            return new AdminOutcome.Refused<>(IdentityError.PLATFORM_IDENTITY_ROLE_NOT_ALLOWED);
        }
        return new AdminOutcome.Done<>(replace(account, wanted, AccountGrants.Origin.manual(caller, null), null).orElseThrow());
    }

    /** The second person agreed: Done(true) when applied, Done(false) when the base had moved (a requested role that no longer exists included) and nothing was applied; applied without the single-person checks, which the confirmation replaces; the floor still applies. The request asked, so it is the one on the rows. */
    @Transactional
    AdminOutcome<Boolean> applyConfirmed(UserId userId, Set<String> roleCodes, AuthorizationRequest request) {
        Optional<UserAccount> found = accounts.find(userId);
        if (found.isEmpty()) {
            return new AdminOutcome.Refused<>(IdentityError.PLATFORM_IDENTITY_ACCOUNT_NOT_FOUND);
        }
        List<Role> wanted = new ArrayList<>();
        for (String code : roleCodes) {
            Optional<Role> role = roles.findByCode(new RoleCode(code));
            if (role.isEmpty()) {
                return new AdminOutcome.Done<>(false);
            }
            wanted.add(role.get());
        }
        if (givesARole(wanted, accounts.findAssignedRoleCodes(userId)) && !employment.isEmployed(found.get())) {
            return new AdminOutcome.Refused<>(IdentityError.PLATFORM_IDENTITY_EMPLOYEE_SEPARATED);
        }
        return new AdminOutcome.Done<>(replace(found.get(), wanted, AccountGrants.Origin.manual(request.requestedBy(), request.requestId()), request).isPresent());
    }

    /** The account version and the current version of every role of the wanted set and of the set held now. */
    private AuthorizationBase baseOf(UserAccount account, List<Role> wanted, Set<RoleCode> alreadyHeld) {
        Set<RoleVersionId> versions = new LinkedHashSet<>();
        wanted.forEach(role -> versions.add(Objects.requireNonNull(role.roleVersionId())));
        alreadyHeld.forEach(code -> roles.findByCode(code).ifPresent(role -> versions.add(Objects.requireNonNull(role.roleVersionId()))));
        return new AuthorizationBase(account.version(), List.copyOf(versions));
    }

    /** Taking roles away from a person who left stays possible; giving one does not (#1322). */
    private static boolean givesARole(List<Role> wanted, Set<RoleCode> alreadyHeld) {
        return wanted.stream().anyMatch(role -> !alreadyHeld.contains(role.code()));
    }

    private boolean touchesSensitiveRole(List<Role> wanted, Set<RoleCode> alreadyHeld) {
        Set<RoleCode> wantedCodes = new HashSet<>();
        for (Role role : wanted) {
            wantedCodes.add(role.code());
            if (!alreadyHeld.contains(role.code()) && SensitivePermissions.roleIsSensitive(role)) {
                return true;
            }
        }
        for (RoleCode held : alreadyHeld) {
            if (!wantedCodes.contains(held) && roles.findByCode(held).map(SensitivePermissions::roleIsSensitive).orElse(false)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Lock order: the separation advisory lock, then the account row, then the role rows (inside the repository). Revoking a super_admin or security_auditor
     * grant of an available holder is refused when no available super administrator would remain, and announced when one remains. Returns the account version, or empty when a confirmed request's base moved (the caller voids it).
     */
    private Optional<Long> replace(UserAccount loaded, List<Role> wanted, AccountGrants.Origin origin, @Nullable AuthorizationRequest confirmed) {
        floor.lockDecisions();
        UserAccount account = accounts.findForUpdate(loaded.userId()).orElseThrow(() -> IdentityError.PLATFORM_IDENTITY_ACCOUNT_NOT_FOUND.refusal("no account"));
        if (confirmed == null && account.version() != loaded.version()) {
            throw IdentityError.PLATFORM_IDENTITY_CONCURRENT_UPDATE.refusal("account " + account.userId().value() + " was changed by someone else");
        }
        if (confirmed != null && voiding.moved(confirmed, account.version())) {
            return Optional.empty();
        }
        Set<RoleId> wantedIds = new HashSet<>();
        wanted.forEach(role -> wantedIds.add(role.roleId()));
        Map<RoleCode, HeldGrant> administratorGrants = new HashMap<>();
        for (HeldGrant grant : grants.closing(account.userId(), wantedIds)) {
            if (grant.roleCode().equals(Role.SUPER_ADMIN) || grant.roleCode().equals(Role.SECURITY_AUDITOR)) {
                administratorGrants.put(grant.roleCode(), grant);
            }
        }
        AdministratorFloor.Judgement judgement = administratorGrants.isEmpty() ? AdministratorFloor.Judgement.UNAFFECTED
                : floor.judge(account.userId(), administratorGrants.containsKey(Role.SUPER_ADMIN));
        GrantChanges changes = grants.replace(account, wantedIds, origin);
        if (!changes.isEmpty()) {
            accounts.save(account);
            events.publish(account);
            revocation.revokeAll(account.userId(), "the roles of the account were replaced");
            cache.evict(account.userId());
            if (judgement.noticeDue()) {
                HeldGrant about = administratorGrants.getOrDefault(Role.SUPER_ADMIN, administratorGrants.get(Role.SECURITY_AUDITOR));
                notice.roleRevoked(about.id(), judgement.noticeTo(), currentUser.userId());
            }
        }
        return Optional.of(account.version());
    }
}
