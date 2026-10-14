package com.forcitis.one.platform.identity.domain;

import java.util.Objects;

/**
 * A directory (routePath null) or a page. permissionCode null means every signed-in user may see the page.
 * parentItemId is null for top-level items.
 */
public record NavigationItem(NavigationItemId itemId, NavigationItemId parentItemId, String name, String routePath,
                             String permissionCode, int sortOrder) {

    public NavigationItem {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(name, "name");
    }
}
