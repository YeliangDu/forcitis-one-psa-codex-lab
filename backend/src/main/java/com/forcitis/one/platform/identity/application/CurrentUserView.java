package com.forcitis.one.platform.identity.application;

import java.time.Instant;
import java.util.List;

public record CurrentUserView(long userId, String displayName, Long orgUnitId, String orgUnitName, List<String> permissionCodes,
                              List<NavigationEntry> navigation, SessionMode mode, Instant administratorModeExpiresAt,
                              boolean identityConfirmed) {

    public CurrentUserView {
        permissionCodes = List.copyOf(permissionCodes);
        navigation = List.copyOf(navigation);
    }
}
