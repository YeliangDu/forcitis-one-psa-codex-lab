package com.forcitis.one.platform.identity.application;

import java.util.List;

/** Who the caller is, what they may do, and what the menu shows (permissions sorted for a stable response). */
public record CurrentUserView(long userId, String displayName, Long orgUnitId, String orgUnitName, List<String> permissionCodes,
                              List<NavigationEntry> navigation) {

    public CurrentUserView {
        permissionCodes = List.copyOf(permissionCodes);
        navigation = List.copyOf(navigation);
    }
}
