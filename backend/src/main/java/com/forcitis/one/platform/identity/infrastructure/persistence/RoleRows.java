package com.forcitis.one.platform.identity.infrastructure.persistence;

import com.forcitis.one.platform.identity.api.AccessScopeKind;
import com.forcitis.one.platform.identity.api.RoleCode;
import com.forcitis.one.platform.identity.domain.Role;
import com.forcitis.one.platform.identity.api.RoleId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;

final class RoleRows {

    private static final String COLUMNS = "r.role_id, r.code, r.name, r.kind, r.status, r.access_scope, r.is_administrative, r.version, r.event_count";

    private RoleRows() {
    }

    /** from: everything after "select ... from", starting with the roles table aliased r. */
    static List<Role> select(JdbcClient jdbc, String from, Map<String, Object> params) {
        record Row(long roleId, String code, String name, String kind, String status, String accessScope, boolean administrative, long version, long eventSequence) {
        }
        List<Row> rows = jdbc.sql("select " + COLUMNS + " " + from).params(params)
                .query((rs, n) -> new Row(rs.getLong("role_id"), rs.getString("code"), rs.getString("name"), rs.getString("kind"),
                        rs.getString("status"), rs.getString("access_scope"), rs.getBoolean("is_administrative"),
                        rs.getLong("version"), rs.getLong("event_count")))
                .list();
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, Set<String>> permissions = new HashMap<>();
        jdbc.sql("select role_id, permission_code from platform_identity.role_permissions where role_id in (:ids)")
                .param("ids", rows.stream().map(Row::roleId).toList())
                .query((rs, n) -> {
                    permissions.computeIfAbsent(rs.getLong("role_id"), id -> new HashSet<>()).add(rs.getString("permission_code"));
                    return null;
                }).list();
        return rows.stream().map(row -> Role.restore(new RoleId(row.roleId()), new RoleCode(row.code()), row.name(), row.kind(),
                row.status(), AccessScopeKind.valueOf(row.accessScope()), row.administrative(),
                permissions.getOrDefault(row.roleId(), Set.of()), row.version(), row.eventSequence())).toList();
    }
}
