package com.forcitis.one.platform.identity.web;

import com.forcitis.one.platform.identity.application.CurrentUserView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

record CurrentUserResponse(UUID userId, String displayName, @Nullable UUID orgUnitId, @Nullable String orgUnitName, List<String> permissionCodes,
                           List<NavigationResponse> navigation, String mode, @Nullable Instant administratorModeExpiresAt, boolean identityConfirmed) {

    static CurrentUserResponse of(CurrentUserView view) {
        return new CurrentUserResponse(view.userId().value(), view.displayName(), view.orgUnitId(), view.orgUnitName(),
                view.permissionCodes(), view.navigation().stream().map(NavigationResponse::of).toList(), view.mode().name(),
                view.administratorModeExpiresAt(), view.identityConfirmed());
    }
}
