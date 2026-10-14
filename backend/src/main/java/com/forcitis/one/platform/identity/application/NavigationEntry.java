package com.forcitis.one.platform.identity.application;

import java.util.List;

public record NavigationEntry(String name, String routePath, List<NavigationEntry> children) {

    public NavigationEntry {
        children = List.copyOf(children);
    }
}
