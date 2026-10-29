package com.forcitis.one.platform.identity.domain;

import java.util.List;
import org.jspecify.annotations.Nullable;

public record NavigationNode(String name, @Nullable String routePath, List<NavigationNode> children) {

    public NavigationNode {
        children = List.copyOf(children);
    }
}
