package com.forcitis.one.platform.identity.application;

import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The person says the account a ticket logged them into is theirs; until then the session holds no permission at all. */
@Service
public class ConfirmIdentity {

    private final CurrentUser currentUser;
    private final SessionRegistry sessions;
    private final AuditLog audit;
    private final BrowserIdentity browserIdentity;
    private final AccountRepository accounts;

    ConfirmIdentity(CurrentUser currentUser, SessionRegistry sessions, AuditLog audit, BrowserIdentity browserIdentity,
            AccountRepository accounts) {
        this.accounts = accounts;
        this.currentUser = currentUser;
        this.sessions = sessions;
        this.audit = audit;
        this.browserIdentity = browserIdentity;
    }

    /** Returns the new value for the browser's identifier cookie, so that the next login from this browser need not ask again. */
    @Transactional
    public String execute(@Nullable String presentedBrowserIdentifier) {
        var userId = currentUser.userId();
        if (accounts.find(userId).isEmpty()) {
            throw IdentityError.PLATFORM_IDENTITY_ACCOUNT_NOT_FOUND.refusal("no account " + userId.value());
        }
        sessions.confirmIdentity();
        String issued = browserIdentity.issue(userId, presentedBrowserIdentifier);
        audit.record(new AuditEntry("platform.identity.session.identity-confirmed", userId, null, AuditSubject.of(AuditSubjectKind.USER_ACCOUNT, userId.value()),
                "the person confirmed the account of the ticket login"));
        return issued;
    }
}
