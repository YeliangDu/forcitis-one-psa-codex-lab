package com.forcitis.one.platform.identity.domain;

import java.util.List;

/** One visible entry of the navigation tree. */
public record NavigationNode(String name, String routePath, List<NavigationNode> children) {

    public NavigationNode {
        children = List.copyOf(children);
    }
}
