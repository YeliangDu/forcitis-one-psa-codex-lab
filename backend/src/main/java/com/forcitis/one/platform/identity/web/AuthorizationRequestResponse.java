package com.forcitis.one.platform.identity.web;

import com.forcitis.one.platform.identity.application.AuthorizationRequestView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

record AuthorizationRequestResponse(UUID requestId, String kind, UUID requestedBy, @Nullable String requestedByName, Instant requestedAt,
                                    @Nullable UUID subjectUserId, @Nullable String subjectUserName, @Nullable UUID roleId, List<String> roleCodes,
                                    List<RoleLabelResponse> roleLabels, @Nullable String roleName, List<String> permissionCodes) {

    record RoleLabelResponse(String code, @Nullable String name) {}

    static AuthorizationRequestResponse of(AuthorizationRequestView view) {
        return new AuthorizationRequestResponse(view.requestId(), view.kind(), view.requestedBy(), view.requestedByName(), view.requestedAt(),
                view.subjectUserId(), view.subjectUserName(), view.roleId(), view.roleCodes(),
                view.roleLabels().stream().map(label -> new RoleLabelResponse(label.code(), label.name())).toList(), view.roleName(), view.permissionCodes());
    }
}
