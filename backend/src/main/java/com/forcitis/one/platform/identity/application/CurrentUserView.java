package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.UserId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CurrentUserView(UserId userId, String displayName, UUID orgUnitId, String orgUnitName, List<String> permissionCodes,
                              List<NavigationEntry> navigation, SessionMode mode, Instant administratorModeExpiresAt,
                              boolean identityConfirmed) {

    public CurrentUserView {
        permissionCodes = List.copyOf(permissionCodes);
        navigation = List.copyOf(navigation);
    }
}
