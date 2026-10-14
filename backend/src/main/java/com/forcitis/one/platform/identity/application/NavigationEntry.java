package com.forcitis.one.platform.identity.application;

import java.util.List;

/** One entry of the navigation tree the front end renders; routePath is null for a directory. */
public record NavigationEntry(String name, String routePath, List<NavigationEntry> children) {

    public NavigationEntry {
        children = List.copyOf(children);
    }
}
