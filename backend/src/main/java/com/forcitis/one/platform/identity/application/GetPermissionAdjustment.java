package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentId;
import com.forcitis.one.platform.identity.domain.PermissionAdjustment;
import com.forcitis.one.platform.identity.domain.PermissionAdjustmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reading one item is audited as viewed (ADR-0032 decision 5 item 5), in its own transaction because the read itself writes nothing. */
@Service
public class GetPermissionAdjustment {

    private final PermissionAdjustmentRepository adjustments;
    private final PermissionAdjustmentAccess access;
    private final CurrentUser currentUser;
    private final AuditLog audit;

    GetPermissionAdjustment(PermissionAdjustmentRepository adjustments, PermissionAdjustmentAccess access, CurrentUser currentUser, AuditLog audit) {
        this.adjustments = adjustments;
        this.access = access;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PermissionAdjustmentView execute(PermissionAdjustmentId id) {
        PermissionAdjustment item = access.readable(adjustments.findById(id).orElseThrow(() -> PermissionAdjustmentAccess.notFound(id.toString())), access.reader());
        audit.recordIndependently(new AuditEntry("platform.identity.permission-adjustment.viewed", currentUser.userId(), null,
                AuditSubject.of(AuditSubjectKind.PERMISSION_ADJUSTMENT, id.value()), "read by id"));
        return PermissionAdjustmentView.of(item);
    }
}
