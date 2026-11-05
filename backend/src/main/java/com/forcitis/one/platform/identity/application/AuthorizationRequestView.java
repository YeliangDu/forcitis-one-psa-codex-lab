package com.forcitis.one.platform.identity.application;

import com.forcitis.one.platform.identity.domain.AuthorizationRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** The names are null when no employee row or role row is left behind the id; the ids stay. */
public record AuthorizationRequestView(UUID requestId, String kind, UUID requestedBy, @Nullable String requestedByName, Instant requestedAt,
                                       @Nullable UUID subjectUserId, @Nullable String subjectUserName, @Nullable UUID roleId, List<String> roleCodes,
                                       List<RoleLabel> roleLabels, @Nullable String roleName, List<String> permissionCodes) {

    public record RoleLabel(String code, @Nullable String name) {}

    public AuthorizationRequestView {
        roleCodes = List.copyOf(roleCodes);
        roleLabels = List.copyOf(roleLabels);
        permissionCodes = List.copyOf(permissionCodes);
    }

    static AuthorizationRequestView of(AuthorizationRequest request, @Nullable String requestedByName, @Nullable String subjectUserName, List<RoleLabel> roleLabels) {
        return new AuthorizationRequestView(request.requestId().value(), request.kind().name(), request.requestedBy().value(), requestedByName,
                request.requestedAt(), request.subjectUser() == null ? null : request.subjectUser().value(), subjectUserName,
                request.roleId() == null ? null : request.roleId().value(), request.roleCodes().stream().sorted().toList(), roleLabels,
                request.roleDraft() == null ? null : request.roleDraft().name(),
                request.roleDraft() == null ? List.of() : request.roleDraft().permissionCodes().stream().sorted().toList());
    }
}
