package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.kernel.AggregateRoot;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.StateMachine;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.EventDraft;
import com.forcitis.one.platform.identity.api.AccountRoleGrantId;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.api.RoleId;
import com.forcitis.one.platform.identity.events.AccountDisabled;
import com.forcitis.one.platform.identity.events.AccountEnabled;
import com.forcitis.one.platform.identity.api.AccountOpeningRoute;
import com.forcitis.one.platform.identity.events.AccountOpened;
import com.forcitis.one.platform.identity.events.AccountRolesReplaced;
import com.forcitis.one.platform.identity.events.AccountSeparated;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** A login account and the roles granted to it. Employment is owned by the organisation; this status is the administrator's switch. */
public final class UserAccount extends AggregateRoot {
    public static final StateMachine<AccountStatus> MACHINE = StateMachine.of(AccountStatus.class)
            .allow(AccountStatus.ENABLED, AccountStatus.DISABLED)
            .allow(AccountStatus.DISABLED, AccountStatus.ENABLED)
            .build();

    private final UserId userId;
    private final EmployeeId employeeId;
    private final AccountStatus loadedStatus;
    private final boolean stored;
    private AccountStatus status;
    private Set<RoleId> roles;
    private List<AccountRoleGrantId> openedGrants = List.of();
    private List<AccountRoleGrantId> closedGrants = List.of();

    private UserAccount(UserId userId, EmployeeId employeeId, AccountStatus status, Set<RoleId> roles, boolean stored, long version,
            long eventSequence) {
        super(version, eventSequence);
        this.userId = Objects.requireNonNull(userId, "userId");
        this.employeeId = Objects.requireNonNull(employeeId, "employeeId");
        this.status = Objects.requireNonNull(status, "status");
        this.loadedStatus = status;
        this.roles = Set.copyOf(roles);
        this.stored = stored;
    }

    public static UserAccount open(UserId userId, EmployeeId employeeId, Set<RoleId> initialRoles, AccountOpeningRoute route) {
        UserAccount account = new UserAccount(userId, employeeId, AccountStatus.ENABLED, initialRoles, false, 0, 0);
        account.record((envelopes, version) -> new AccountOpened(envelopes.wrap(account.draft("AccountOpened", version)), userId,
                account.orderedRoles(), route));
        return account;
    }

    public static UserAccount restore(UserId userId, EmployeeId employeeId, String status, Set<RoleId> roles, long version, long eventSequence) {
        return new UserAccount(userId, employeeId, AccountStatus.valueOf(status), roles, true, version, eventSequence);
    }

    public UserId userId() {
        return userId;
    }

    public EmployeeId employeeId() {
        return employeeId;
    }

    public AccountStatus status() {
        return status;
    }

    public AccountStatus loadedStatus() {
        return loadedStatus;
    }

    public Set<RoleId> roles() {
        return roles;
    }

    public boolean isStored() {
        return stored;
    }

    public boolean isEnabled() {
        return status == AccountStatus.ENABLED;
    }

    public void enable() {
        this.status = MACHINE.require(status, AccountStatus.ENABLED, IdentityError.PLATFORM_IDENTITY_ACCOUNT_TRANSITION_ILLEGAL);
        record((envelopes, version) -> new AccountEnabled(envelopes.wrap(draft("AccountEnabled", version)), userId));
    }

    public void disable() {
        this.status = MACHINE.require(status, AccountStatus.DISABLED, IdentityError.PLATFORM_IDENTITY_ACCOUNT_TRANSITION_ILLEGAL);
        record((envelopes, version) -> new AccountDisabled(envelopes.wrap(draft("AccountDisabled", version)), userId));
    }

    /** Sets the wanted state: asking for the state it already has changes nothing. */
    public void declareEnabled(boolean enabled) {
        if (enabled == isEnabled()) {
            return;
        }
        if (enabled) {
            enable();
        } else {
            disable();
        }
    }

    public void replaceRoles(Set<RoleId> roleIds) {
        if (roles.equals(Set.copyOf(roleIds))) {
            return;
        }
        this.roles = Set.copyOf(roleIds);
        List<RoleId> ordered = orderedRoles();
        record((envelopes, version) -> new AccountRolesReplaced(envelopes.wrap(draft("AccountRolesReplaced", version)), userId, ordered, openedGrants,
                closedGrants));
    }

    /** The grant rows the repository wrote for the roles just replaced; the event names them, so call this before the events are published. */
    public void grantsWritten(GrantChanges changes) {
        this.openedGrants = changes.opened().stream().map(GrantOpening::id).toList();
        this.closedGrants = changes.closed().stream().map(GrantClosing::id).toList();
    }

    /** The person is no longer with the company: no role stays, whatever the roles were. An account without roles has nothing to record. */
    public void recordSeparation() {
        if (roles.isEmpty()) {
            return;
        }
        replaceRoles(Set.of());
        record((envelopes, version) -> new AccountSeparated(envelopes.wrap(draft("AccountSeparated", version)), userId));
    }

    private List<RoleId> orderedRoles() {
        return roles.stream().sorted(Comparator.comparing(RoleId::value)).toList();
    }

    private EventDraft draft(String eventName, long version) {
        return new EventDraft("platform.identity." + eventName, 1, "UserAccount", userId.value(), version, null, null, List.of());
    }
}
