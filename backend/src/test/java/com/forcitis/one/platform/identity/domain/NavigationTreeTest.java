package com.forcitis.one.platform.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** RULE-348: navigation shows enabled directories and pages the person's permissions allow; empty directories disappear. */
class NavigationTreeTest {

    static NavigationItem item(long id, Long parent, String name, String route, String permission, int order) {
        return new NavigationItem(new NavigationItemId(id), parent == null ? null : new NavigationItemId(parent), name, route, permission, order);
    }

    static final List<NavigationItem> ITEMS = List.of(
            item(1, null, "Inbox", "/inbox", null, 20),
            item(2, null, "Admin", null, null, 90),
            item(3, 2L, "Roles", "/admin/roles", "platform.identity:role:read", 10),
            item(4, 2L, "Numbering", "/admin/numbering", "platform.numbering:numbering-rule:read", 20),
            item(5, null, "Reports", null, null, 30));

    @Test
    @DisplayName("RULE-348 pages need their permission; directories show only with a visible child; order is by sort order")
    void visibility() {
        var tree = NavigationTree.visibleTo(Set.of("platform.identity:role:read"), ITEMS);

        assertThat(tree).extracting(NavigationNode::name).containsExactly("Inbox", "Admin");
        assertThat(tree.get(1).children()).extracting(NavigationNode::name).containsExactly("Roles");
        assertThat(tree.get(1).routePath()).isNull();
    }

    @Test
    @DisplayName("RULE-348 a person with no permissions sees only pages for everybody")
    void nothingHeld() {
        assertThat(NavigationTree.visibleTo(Set.of(), ITEMS)).extracting(NavigationNode::name).containsExactly("Inbox");
        assertThat(NavigationTree.visibleTo(Set.of(), List.of())).isEmpty();
    }

    @Test
    @DisplayName("RULE-348 an item whose parent is missing (disabled) is not shown")
    void orphansAreDropped() {
        var orphaned = List.of(item(10, 99L, "Lost", "/lost", null, 1));

        assertThat(NavigationTree.visibleTo(Set.of(), orphaned)).isEmpty();
    }
}
