package com.forcitis.one.platform.identity.web;

import com.forcitis.one.platform.identity.application.AuthorizationRequestView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

record AuthorizationRequestResponse(UUID requestId, String kind, UUID requestedBy, Instant requestedAt, @Nullable UUID subjectUserId,
                                    @Nullable UUID roleId, List<String> roleCodes, @Nullable String roleName, List<String> permissionCodes) {

    static AuthorizationRequestResponse of(AuthorizationRequestView view) {
        return new AuthorizationRequestResponse(view.requestId(), view.kind(), view.requestedBy(), view.requestedAt(), view.subjectUserId(),
                view.roleId(), view.roleCodes(), view.roleName(), view.permissionCodes());
    }
}
