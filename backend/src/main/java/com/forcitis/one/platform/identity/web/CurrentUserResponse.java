package com.forcitis.one.platform.identity.web;

import com.forcitis.one.platform.identity.application.CurrentUserView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** orgUnitId is a string: external ids are signed 64-bit numbers, which JavaScript numbers cannot hold. */
record CurrentUserResponse(UUID userId, String displayName, @Nullable String orgUnitId, @Nullable String orgUnitName, List<String> permissionCodes,
                           List<NavigationResponse> navigation, String mode, @Nullable Instant administratorModeExpiresAt, boolean identityConfirmed) {

    static CurrentUserResponse of(CurrentUserView view) {
        return new CurrentUserResponse(view.userId().value(), view.displayName(), view.orgUnitId() == null ? null : String.valueOf(view.orgUnitId()), view.orgUnitName(),
                view.permissionCodes(), view.navigation().stream().map(NavigationResponse::of).toList(), view.mode().name(),
                view.administratorModeExpiresAt(), view.identityConfirmed());
    }
}
