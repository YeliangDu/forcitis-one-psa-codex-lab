package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.kernel.ExpectedVersion;
import com.forcitis.one.kernel.InvalidRequestException;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.platform.audit.api.AuditEntryLookup;
import com.forcitis.one.platform.identity.domain.AdjustmentReference;
import com.forcitis.one.platform.identity.domain.AuthorizationRequestRepository;
import com.forcitis.one.platform.identity.domain.PermissionAdjustment;
import com.forcitis.one.platform.identity.domain.PermissionAdjustmentRepository;
import com.forcitis.one.platform.audit.api.AuditEntryId;
import com.forcitis.one.platform.identity.api.AuthorizationRequestId;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentId;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The handler shows the adjustment was made elsewhere: by an authorization request or by a grant already in the audit trail (ADR-0032 decision 5). */
@Service
public class MarkPermissionAdjustmentAdjusted {

    private final PermissionAdjustmentRepository adjustments;
    private final PermissionAdjustmentAccess access;
    private final PermissionAdjustmentViews views;
    private final CurrentUser currentUser;
    private final AuthorizationRequestRepository requests;
    private final AuditEntryLookup auditEntries;
    private final DomainEvents events;

    MarkPermissionAdjustmentAdjusted(PermissionAdjustmentRepository adjustments, PermissionAdjustmentAccess access, CurrentUser currentUser,
            AuthorizationRequestRepository requests, AuditEntryLookup auditEntries, DomainEvents events, PermissionAdjustmentViews views) {
        this.views = views;
        this.adjustments = adjustments;
        this.access = access;
        this.currentUser = currentUser;
        this.requests = requests;
        this.auditEntries = auditEntries;
        this.events = events;
    }

    @Transactional
    public PermissionAdjustmentView execute(PermissionAdjustmentId id, ExpectedVersion expected, @Nullable UUID authorizationRequestId, @Nullable UUID auditEntryId) {
        AdjustmentReference reference = new AdjustmentReference(authorizationRequestId == null ? null : new AuthorizationRequestId(authorizationRequestId),
                auditEntryId == null ? null : new AuditEntryId(auditEntryId));
        PermissionAdjustment item = access.handleable(id, expected);
        if (reference.authorizationRequest() != null && requests.find(reference.authorizationRequest()).isEmpty()) {
            throw new InvalidRequestException("the authorization request does not exist");
        }
        if (reference.auditEntry() != null && !auditEntries.exists(reference.auditEntry())) {
            throw new InvalidRequestException("the audit entry does not exist");
        }
        item.markAdjusted(currentUser.userId(), reference);
        adjustments.update(item);
        events.publish(item);
        return views.of(item);
    }
}
