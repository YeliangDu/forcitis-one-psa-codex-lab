package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditEntryId;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import com.forcitis.one.platform.identity.api.AccountRoleGrantId;
import com.forcitis.one.platform.identity.api.AuthorizationRequestId;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.api.RoleCode;
import com.forcitis.one.platform.identity.api.RoleId;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import com.forcitis.one.platform.identity.domain.GrantChanges;
import com.forcitis.one.platform.identity.domain.GrantChannel;
import com.forcitis.one.platform.identity.domain.GrantClosing;
import com.forcitis.one.platform.identity.domain.GrantOpening;
import com.forcitis.one.platform.identity.domain.HeldGrant;
import com.forcitis.one.platform.identity.domain.RevocationCause;
import com.forcitis.one.platform.identity.domain.RoleRepository;
import com.forcitis.one.platform.identity.domain.UserAccount;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Replaces the roles of an account by difference (ADR-0034 decision 2 item 5): the grants of roles no longer wanted are closed, those of roles newly wanted
 * are opened, the others stay as they are. Each row gets its own audit entry in the same transaction, whose id is written back to the row. The caller holds
 * the account row FOR UPDATE.
 */
@Component
class AccountGrants {

    /** Who acts and under which channel and cause the rows are written; note is added to every audit text. */
    record Origin(GrantChannel channel, RevocationCause cause, @Nullable UserId actor, @Nullable AuthorizationRequestId request,
                  @Nullable PersonnelCommandId separationCommand, boolean operations, String note) {

        static Origin manual(UserId actor, @Nullable AuthorizationRequestId request) {
            return new Origin(GrantChannel.MANUAL, RevocationCause.MANUAL, actor, request, null, false, "");
        }

        static Origin defaultRole() {
            return new Origin(GrantChannel.DEFAULT_ROLE, RevocationCause.MANUAL, null, null, null, false, "");
        }

        static Origin operations(OperationAttestation attestation) {
            return new Origin(GrantChannel.OPERATIONS, RevocationCause.OPERATIONS, null, null, null, true, attestation.describe());
        }

        static Origin separation(PersonnelCommandId command) {
            return new Origin(GrantChannel.MANUAL, RevocationCause.SEPARATION, null, null, command, false, "");
        }
    }

    private final AccountRepository accounts;
    private final RoleRepository roles;
    private final AuditLog audit;
    private final IdGenerator ids;
    private final Clock clock;

    AccountGrants(AccountRepository accounts, RoleRepository roles, AuditLog audit, IdGenerator ids, Clock clock) {
        this.accounts = accounts;
        this.roles = roles;
        this.audit = audit;
        this.ids = ids;
        this.clock = clock;
    }

    /** The grants the replacement would close, for the caller that has to judge them first. */
    List<HeldGrant> closing(UserId user, Set<RoleId> wanted) {
        return accounts.openGrants(user).stream().filter(grant -> !wanted.contains(grant.roleId())).toList();
    }

    /** Writes the difference and records it on the account (its event names the rows); empty when nothing differs. The caller saves the account and publishes. */
    GrantChanges replace(UserAccount account, Set<RoleId> wanted, Origin origin) {
        UserId user = account.userId();
        List<HeldGrant> open = accounts.openGrants(user);
        Set<RoleId> held = open.stream().map(HeldGrant::roleId).collect(Collectors.toSet());
        if (held.equals(wanted)) {
            return GrantChanges.NONE;
        }
        AuditSubject subject = AuditSubject.of(AuditSubjectKind.USER_ACCOUNT, user.value());
        List<GrantClosing> closed = new ArrayList<>();
        for (HeldGrant grant : open) {
            if (!wanted.contains(grant.roleId())) {
                AuditEntryId entry = audit.record(revokedEntry(origin, subject,
                        text(origin, grant.id(), grant.roleCode(), "cause=" + origin.cause())));
                closed.add(new GrantClosing(grant.id(), origin.cause(), origin.cause() == RevocationCause.MANUAL ? origin.actor() : null, origin.request(),
                        origin.separationCommand(), entry));
            }
        }
        List<GrantOpening> opened = new ArrayList<>();
        for (RoleId role : wanted) {
            if (!held.contains(role)) {
                AccountRoleGrantId id = new AccountRoleGrantId(ids.next());
                RoleCode code = roles.findById(role).orElseThrow(() -> IdentityError.PLATFORM_IDENTITY_ROLE_NOT_FOUND.refusal("role " + role.value() + " was deleted")).code();
                AuditEntryId entry = audit.record(grantedEntry(origin, subject,
                        text(origin, id, code, "channel=" + origin.channel())));
                opened.add(new GrantOpening(id, role, origin.channel(), origin.channel() == GrantChannel.MANUAL ? origin.actor() : null, origin.request(), entry));
            }
        }
        GrantChanges changes = new GrantChanges(opened, closed);
        accounts.saveGrants(user, changes);
        account.replaceRoles(wanted);
        account.grantsWritten(changes);
        return changes;
    }

    private static AuditEntry revokedEntry(Origin origin, AuditSubject subject, String detail) {
        return origin.operations() ? new AuditEntry("platform.identity.operations.account.role.revoked", origin.actor(), null, subject, detail)
                : new AuditEntry("platform.identity.account.role.revoked", origin.actor(), null, subject, detail);
    }

    private static AuditEntry grantedEntry(Origin origin, AuditSubject subject, String detail) {
        return origin.operations() ? new AuditEntry("platform.identity.operations.account.role.granted", origin.actor(), null, subject, detail)
                : new AuditEntry("platform.identity.account.role.granted", origin.actor(), null, subject, detail);
    }

    /** The operations commands keep their audit text (the attestation and the role) as it was; the other acts name the grant row, the cause or channel and the actual clock. */
    private String text(Origin origin, AccountRoleGrantId grant, RoleCode role, String how) {
        return origin.operations() ? origin.note() + " role=" + role.value() : "grant=" + grant + " role=" + role.value() + " " + how + " at=" + clock.instant();
    }
}
