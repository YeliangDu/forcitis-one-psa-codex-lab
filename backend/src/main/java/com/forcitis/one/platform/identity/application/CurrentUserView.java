package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.UserId;
import java.time.Instant;
import java.util.List;

public record CurrentUserView(UserId userId, String displayName, java.util.UUID orgUnitId, String orgUnitName, List<String> permissionCodes,
                              List<NavigationEntry> navigation, SessionMode mode, Instant administratorModeExpiresAt,
                              boolean identityConfirmed) {

    public CurrentUserView {
        permissionCodes = List.copyOf(permissionCodes);
        navigation = List.copyOf(navigation);
    }
}
