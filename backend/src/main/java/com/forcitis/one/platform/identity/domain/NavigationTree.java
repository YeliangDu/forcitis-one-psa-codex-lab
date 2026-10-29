package com.forcitis.one.platform.identity.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/** A directory with nothing visible below it disappears; items whose parent is missing are dropped. */
public final class NavigationTree {

    private static final Comparator<NavigationItem> ORDER = Comparator.comparingInt(NavigationItem::sortOrder)
            .thenComparing(NavigationItem::name);

    private NavigationTree() {
    }

    public static List<NavigationNode> visibleTo(Set<String> permissionCodes, List<NavigationItem> enabledItems) {
        Map<NavigationItemId, List<NavigationItem>> byParent = enabledItems.stream()
                .filter(item -> item.parentItemId() != null)
                .collect(Collectors.groupingBy(NavigationItem::parentItemId));
        List<NavigationNode> roots = new ArrayList<>();
        enabledItems.stream().filter(item -> item.parentItemId() == null).sorted(ORDER).forEach(item -> {
            @Nullable NavigationNode node = build(item, permissionCodes, byParent, 0);
            if (node != null) {
                roots.add(node);
            }
        });
        return roots;
    }

    private static @Nullable NavigationNode build(NavigationItem item, Set<String> permissionCodes,
            Map<NavigationItemId, List<NavigationItem>> byParent, int depth) {
        if (item.routePath() != null) {
            boolean allowed = item.permissionCode() == null || permissionCodes.contains(item.permissionCode());
            return allowed ? new NavigationNode(item.name(), item.routePath(), List.of()) : null;
        }
        if (depth > 8) {
            return null;
        }
        List<NavigationNode> children = new ArrayList<>();
        byParent.getOrDefault(item.itemId(), List.of()).stream().sorted(ORDER).forEach(child -> {
            @Nullable NavigationNode node = build(child, permissionCodes, byParent, depth + 1);
            if (node != null) {
                children.add(node);
            }
        });
        return children.isEmpty() ? null : new NavigationNode(item.name(), null, children);
    }
}
