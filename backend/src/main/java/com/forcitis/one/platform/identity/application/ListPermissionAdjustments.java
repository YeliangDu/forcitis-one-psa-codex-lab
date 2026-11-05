package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.CursorPage;
import com.forcitis.one.kernel.PageRequest;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentId;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentKind;
import com.forcitis.one.platform.identity.domain.PermissionAdjustment;
import com.forcitis.one.platform.identity.domain.PermissionAdjustmentRepository;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentStatus;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListPermissionAdjustments {

    static final String LIST = "permission-adjustments";

    private final PermissionAdjustmentRepository adjustments;
    private final PermissionAdjustmentAccess access;
    private final PermissionAdjustmentViews views;

    ListPermissionAdjustments(PermissionAdjustmentRepository adjustments, PermissionAdjustmentAccess access, PermissionAdjustmentViews views) {
        this.views = views;
        this.adjustments = adjustments;
        this.access = access;
    }

    /** Only the items in the caller's scope, newest first; a caller with none gets an empty page, not a refusal. */
    @Transactional(readOnly = true)
    public CursorPage<PermissionAdjustmentView> execute(PageRequest page, @Nullable PermissionAdjustmentStatus status, @Nullable PermissionAdjustmentKind kind) {
        PermissionAdjustmentId after = page.cursorUuid() == null ? null : new PermissionAdjustmentId(page.cursorUuid());
        List<PermissionAdjustment> found = adjustments.findFor(access.reader(), status, kind, after, page.limit() + 1);
        List<PermissionAdjustment> items = found.size() > page.limit() ? found.subList(0, page.limit()) : found;
        String next = found.size() > page.limit() ? PageRequest.encode(LIST, items.getLast().id().toString()) : null;
        return CursorPage.of(views.of(items), next);
    }
}
