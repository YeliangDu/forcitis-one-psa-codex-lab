package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.kernel.ExpectedVersion;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditEntryId;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import com.forcitis.one.platform.identity.domain.AdministratorFloor;
import com.forcitis.one.platform.identity.domain.HeldGrant;
import com.forcitis.one.platform.identity.domain.Role;
import com.forcitis.one.platform.identity.domain.UserAccount;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Disabling the account of an available super_admin or security_auditor holder is floor-protected like a separation (user decision 2026-10-30
 * 「加，同离职一样」): refused when no available super administrator would remain, announced when one remains. Lock order: the separation advisory
 * lock, then the account row.
 */
@Service
public class ChangeAccountStatus {

    private final AccountRepository accounts;
    private final SessionRevocation revocation;
    private final AccessCache cache;
    private final DomainEvents events;
    private final RevokeBrowserIdentifiers revokeBrowserIdentifiers;
    private final AdministratorFloor floor;
    private final AdministratorFloorNotice notice;
    private final AuditLog audit;
    private final CurrentUser currentUser;

    ChangeAccountStatus(AccountRepository accounts, SessionRevocation revocation, AccessCache cache, DomainEvents events,
            RevokeBrowserIdentifiers revokeBrowserIdentifiers, AdministratorFloor floor, AdministratorFloorNotice notice, AuditLog audit,
            CurrentUser currentUser) {
        this.revokeBrowserIdentifiers = revokeBrowserIdentifiers;
        this.accounts = accounts;
        this.revocation = revocation;
        this.cache = cache;
        this.events = events;
        this.floor = floor;
        this.notice = notice;
        this.audit = audit;
        this.currentUser = currentUser;
    }

    @Transactional
    public long execute(UserId userId, boolean enable, ExpectedVersion expected) {
        if (!enable) {
            floor.lockDecisions();
        }
        UserAccount account = accounts.findForUpdate(userId).orElse(null);
        if (account == null) {
            throw IdentityError.PLATFORM_IDENTITY_ACCOUNT_NOT_FOUND.refusal("no account " + userId.value());
        }
        expected.check(account.version());
        boolean wasEnabled = account.isEnabled();
        account.declareEnabled(enable);
        if (account.hasPendingEvents()) {
            AdministratorFloor.Judgement judgement = AdministratorFloor.Judgement.UNAFFECTED;
            AuditEntryId disabledEntry = null;
            if (!enable && wasEnabled) {
                List<HeldGrant> open = accounts.openGrants(userId);
                if (open.stream().anyMatch(grant -> grant.roleCode().equals(Role.SUPER_ADMIN) || grant.roleCode().equals(Role.SECURITY_AUDITOR))) {
                    judgement = floor.judge(userId, open.stream().anyMatch(grant -> grant.roleCode().equals(Role.SUPER_ADMIN)));
                }
                disabledEntry = audit.record(new AuditEntry("platform.identity.account.disabled", currentUser.userId(), null, AuditSubject.of(AuditSubjectKind.USER_ACCOUNT, userId.value()),
                        "the account was disabled"));
            }
            accounts.save(account);
            events.publish(account);
            if (judgement.announce()) {
                notice.accountDisabled(userId, disabledEntry, judgement.noticeTo(), currentUser.userId());
            }
        }
        if (!enable) {
            revocation.revokeAll(account.userId(), "the account was disabled");
            revokeBrowserIdentifiers.execute(account.userId());
        }
        cache.evict(account.userId());
        return account.version();
    }
}
