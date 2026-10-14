package com.forcitis.one.platform.identity.web;

import com.forcitis.one.platform.identity.application.CurrentUserView;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** userId and orgUnitId are strings: external ids are signed 64-bit numbers, which JavaScript numbers cannot hold. */
record CurrentUserResponse(String userId, String displayName, @Nullable String orgUnitId, @Nullable String orgUnitName, List<String> permissionCodes,
                           List<NavigationResponse> navigation, String mode, @Nullable Instant administratorModeExpiresAt) {

    static CurrentUserResponse of(CurrentUserView view) {
        return new CurrentUserResponse(String.valueOf(view.userId()), view.displayName(), view.orgUnitId() == null ? null : String.valueOf(view.orgUnitId()), view.orgUnitName(),
                view.permissionCodes(), view.navigation().stream().map(NavigationResponse::of).toList(), view.mode().name(),
                view.administratorModeExpiresAt());
    }
}
