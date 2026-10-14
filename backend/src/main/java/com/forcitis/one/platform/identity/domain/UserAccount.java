package com.forcitis.one.platform.identity.domain;

import com.forcitis.one.kernel.AggregateRoot;
import com.forcitis.one.kernel.StateMachine;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.EventDraft;
import com.forcitis.one.kernel.event.EventSource;
import com.forcitis.one.kernel.event.SourceSystem;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.api.RoleId;
import com.forcitis.one.platform.identity.events.AccountDisabled;
import com.forcitis.one.platform.identity.events.AccountEnabled;
import com.forcitis.one.platform.identity.events.AccountRolesReplaced;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** A login account. Employment is owned by the organisation; this status is the administrator's switch. */
public final class UserAccount extends AggregateRoot {
    public static final StateMachine<AccountStatus> MACHINE = StateMachine.of(AccountStatus.class)
            .allow(AccountStatus.ENABLED, AccountStatus.DISABLED)
            .allow(AccountStatus.DISABLED, AccountStatus.ENABLED)
            .build();

    private final UserId userId;
    private final AccountStatus loadedStatus;
    private AccountStatus status;

    private UserAccount(UserId userId, AccountStatus status, long version) {
        super(version);
        this.userId = Objects.requireNonNull(userId, "userId");
        this.status = Objects.requireNonNull(status, "status");
        this.loadedStatus = status;
    }

    public static UserAccount restore(UserId userId, String status, long version) {
        return new UserAccount(userId, AccountStatus.valueOf(status), version);
    }

    public UserId userId() {
        return userId;
    }

    public AccountStatus status() {
        return status;
    }

    public AccountStatus loadedStatus() {
        return loadedStatus;
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
        List<RoleId> ordered = roleIds.stream().sorted(Comparator.comparingLong(RoleId::value)).toList();
        record((envelopes, version) -> new AccountRolesReplaced(envelopes.wrap(draft("AccountRolesReplaced", version)), userId, ordered));
    }

    private EventDraft draft(String eventName, long version) {
        return new EventDraft("platform.identity." + eventName, 1, "UserAccount", String.valueOf(userId.value()), version, null, null,
                new EventSource(SourceSystem.ONE, null), List.of());
    }
}
