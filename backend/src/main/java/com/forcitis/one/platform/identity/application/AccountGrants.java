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

    static final String GRANTED = "platform.identity.account.role.granted";
    static final String REVOKED = "platform.identity.account.role.revoked";
    static final String OPERATIONS_GRANTED = "platform.identity.operations.account.role.granted";
    static final String OPERATIONS_REVOKED = "platform.identity.operations.account.role.revoked";

    /** Who acts and under which channel and cause the rows are written; note is added to every audit text. */
    record Origin(GrantChannel channel, RevocationCause cause, @Nullable UserId actor, @Nullable AuthorizationRequestId request,
                  @Nullable PersonnelCommandId separationCommand, String grantedAction, String revokedAction, String note) {

        static Origin manual(UserId actor, @Nullable AuthorizationRequestId request) {
            return new Origin(GrantChannel.MANUAL, RevocationCause.MANUAL, actor, request, null, GRANTED, REVOKED, "");
        }

        static Origin defaultRole() {
            return new Origin(GrantChannel.DEFAULT_ROLE, RevocationCause.MANUAL, null, null, null, GRANTED, REVOKED, "");
        }

        static Origin operations(OperationAttestation attestation) {
            return new Origin(GrantChannel.OPERATIONS, RevocationCause.OPERATIONS, null, null, null, OPERATIONS_GRANTED, OPERATIONS_REVOKED, attestation.describe());
        }

        static Origin separation(PersonnelCommandId command) {
            return new Origin(GrantChannel.MANUAL, RevocationCause.SEPARATION, null, null, command, GRANTED, REVOKED, "");
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
                AuditEntryId entry = audit.record(new AuditEntry(origin.revokedAction(), origin.actor(), null, subject,
                        text(origin, "grant=" + grant.id() + " role=" + grant.roleCode().value() + " cause=" + origin.cause())));
                closed.add(new GrantClosing(grant.id(), origin.cause(), origin.cause() == RevocationCause.MANUAL ? origin.actor() : null, origin.request(),
                        origin.separationCommand(), entry));
            }
        }
        List<GrantOpening> opened = new ArrayList<>();
        for (RoleId role : wanted) {
            if (!held.contains(role)) {
                AccountRoleGrantId id = new AccountRoleGrantId(ids.next());
                RoleCode code = roles.findById(role).orElseThrow(() -> new IllegalStateException("role " + role.value() + " vanished")).code();
                AuditEntryId entry = audit.record(new AuditEntry(origin.grantedAction(), origin.actor(), null, subject,
                        text(origin, "grant=" + id + " role=" + code.value() + " channel=" + origin.channel())));
                opened.add(new GrantOpening(id, role, origin.channel(), origin.channel() == GrantChannel.MANUAL ? origin.actor() : null, origin.request(), entry));
            }
        }
        GrantChanges changes = new GrantChanges(opened, closed);
        accounts.applyGrantChanges(user, changes);
        account.replaceRoles(wanted);
        account.grantsWritten(changes);
        return changes;
    }

    private String text(Origin origin, String what) {
        return (origin.note().isEmpty() ? "" : origin.note() + " ") + what + " at=" + clock.instant();
    }
}
