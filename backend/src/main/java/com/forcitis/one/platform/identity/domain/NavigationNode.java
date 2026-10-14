package com.forcitis.one.platform.identity.domain;

import java.util.List;

public record NavigationNode(String name, String routePath, List<NavigationNode> children) {

    public NavigationNode {
        children = List.copyOf(children);
    }
}
