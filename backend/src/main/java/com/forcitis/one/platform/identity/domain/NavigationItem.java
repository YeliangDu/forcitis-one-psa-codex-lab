package com.forcitis.one.platform.identity.domain;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** permissionCode null means every signed-in user may see the page. */
public record NavigationItem(NavigationItemId itemId, @Nullable NavigationItemId parentItemId, String name, @Nullable String routePath,
                             @Nullable String permissionCode, int sortOrder) {

    public NavigationItem {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(name, "name");
    }
}
