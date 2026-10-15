package com.forcitis.one.platform.identity.infrastructure.persistence;

import com.forcitis.one.platform.identity.domain.NavigationItem;
import com.forcitis.one.platform.identity.domain.NavigationItemId;
import com.forcitis.one.platform.identity.domain.NavigationRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcNavigationRepository implements NavigationRepository {

    private final JdbcClient jdbc;

    JdbcNavigationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<NavigationItem> findEnabled() {
        return jdbc.sql("""
                        select item_id, parent_item_id, name, route_path, permission_code, sort_order
                          from platform_identity.navigation_items
                         where is_enabled
                        """)
                .query((rs, n) -> {
                    UUID parent = rs.getObject("parent_item_id", UUID.class);
                    NavigationItemId parentItemId = parent == null ? null : new NavigationItemId(parent);
                    return new NavigationItem(new NavigationItemId(rs.getObject("item_id", UUID.class)), parentItemId, rs.getString("name"),
                            rs.getString("route_path"), rs.getString("permission_code"), rs.getInt("sort_order"));
                }).list();
    }
}
