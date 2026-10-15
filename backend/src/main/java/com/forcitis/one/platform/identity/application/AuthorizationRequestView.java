package com.forcitis.one.platform.identity.application;

import com.forcitis.one.platform.identity.domain.AuthorizationRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record AuthorizationRequestView(UUID requestId, String kind, UUID requestedBy, Instant requestedAt, @Nullable UUID subjectUserId,
                                       @Nullable UUID roleId, List<String> roleCodes, @Nullable String roleName, List<String> permissionCodes) {

    public AuthorizationRequestView {
        roleCodes = List.copyOf(roleCodes);
        permissionCodes = List.copyOf(permissionCodes);
    }

    static AuthorizationRequestView of(AuthorizationRequest request) {
        return new AuthorizationRequestView(request.requestId().value(), request.kind().name(), request.requestedBy().value(),
                request.requestedAt(), request.subjectUser() == null ? null : request.subjectUser().value(),
                request.roleId() == null ? null : request.roleId().value(), request.roleCodes().stream().sorted().toList(),
                request.roleDraft() == null ? null : request.roleDraft().name(),
                request.roleDraft() == null ? List.of() : request.roleDraft().permissionCodes().stream().sorted().toList());
    }
}
