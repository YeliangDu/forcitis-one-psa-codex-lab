package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.CallerPermissions;
import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.api.Permission;
import com.forcitis.one.kernel.ExpectedVersion;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentId;
import com.forcitis.one.platform.identity.domain.PermissionAdjustment;
import com.forcitis.one.platform.identity.domain.PermissionAdjustmentReader;
import com.forcitis.one.platform.identity.domain.PermissionAdjustmentRepository;
import org.springframework.stereotype.Component;

/** The relation scope of the permission adjustments: the caller's reader, and what an item outside it costs (a denied-access audit entry and a 404, ADR-0026 section 6 item 5; security-invariants I-10). */
@Component
class PermissionAdjustmentAccess {

    private final CurrentUser currentUser;
    private final CallerPermissions permissions;
    private final AuditLog audit;
    private final PermissionAdjustmentRepository adjustments;

    PermissionAdjustmentAccess(CurrentUser currentUser, CallerPermissions permissions, AuditLog audit, PermissionAdjustmentRepository adjustments) {
        this.adjustments = adjustments;
        this.currentUser = currentUser;
        this.permissions = permissions;
        this.audit = audit;
    }

    PermissionAdjustmentReader reader() {
        return new PermissionAdjustmentReader(currentUser.userId(), currentUser.employeeId(),
                permissions.holds(Permission.Codes.PLATFORM_IDENTITY_PERMISSION_ADJUSTMENT_READ), permissions.holds(Permission.Codes.PLATFORM_IDENTITY_PERMISSION_ADJUSTMENT_UPDATE));
    }

    /** The reader's view of an item they asked for by id; an item outside their scope is audited as denied and looks like an absent one. */
    PermissionAdjustment readable(PermissionAdjustment item, PermissionAdjustmentReader reader) {
        if (!reader.canRead(item)) {
            audit.recordIndependently(new AuditEntry("platform.identity.access.denied", currentUser.userId(), null,
                    AuditSubject.of(AuditSubjectKind.PERMISSION_ADJUSTMENT, item.id().value()), "outside the scope of the caller"));
            throw notFound(item.id().value().toString());
        }
        return item;
    }

    /** The item a command is about: not about the caller (else 403), in the caller's scope (else 404), one they may handle (else 403), and at the version the caller read (else 412). */
    PermissionAdjustment handleable(PermissionAdjustmentId id, ExpectedVersion expected) {
        PermissionAdjustmentReader reader = reader();
        PermissionAdjustment found = adjustments.findById(id).orElseThrow(() -> notFound(id.toString()));
        if (reader.isSubject(found)) {
            throw IdentityError.PLATFORM_IDENTITY_OWN_PERMISSION_ADJUSTMENT_FORBIDDEN.refusal("the item " + id.value() + " is about the caller");
        }
        PermissionAdjustment item = readable(found, reader);
        if (!reader.canHandle(item)) {
            throw IdentityError.PLATFORM_IDENTITY_PERMISSION_ADJUSTMENT_HANDLER_NOT_ALLOWED.refusal("the caller does not handle the item " + id.value());
        }
        expected.check(item.version());
        return item;
    }

    static RuntimeException notFound(String id) {
        return IdentityError.PLATFORM_IDENTITY_PERMISSION_ADJUSTMENT_NOT_FOUND.refusal("no permission adjustment " + id);
    }
}
