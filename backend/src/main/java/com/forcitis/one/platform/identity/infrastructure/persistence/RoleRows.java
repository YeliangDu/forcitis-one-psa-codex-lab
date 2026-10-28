package com.forcitis.one.platform.identity.infrastructure.persistence;

import com.forcitis.one.platform.identity.api.AccessScopeKind;
import com.forcitis.one.platform.identity.api.Permission;
import com.forcitis.one.platform.identity.api.RoleCode;
import com.forcitis.one.platform.identity.api.RoleId;
import com.forcitis.one.platform.identity.api.RoleVersionId;
import com.forcitis.one.platform.identity.domain.Role;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Reads roles with their current version (ADR-0034 decision 1 item 2: current = effective_to_at is null). A role without a current version
 * (a deleted role) is never read. A permission code the catalogue does not know or has deprecated is left out of the role and alerted;
 * the other codes of the version still count and nothing is thrown (decision 1 item 3, fail-closed).
 */
final class RoleRows {

    private static final Logger LOG = LoggerFactory.getLogger(RoleRows.class);

    /** The only way identity reads the current version of a role: the join every role read goes through. */
    static final String CURRENT_VERSION = "join platform_identity.role_versions v on v.role_id = r.role_id and v.effective_to_at is null";

    private static final String COLUMNS = "r.role_id, r.code, r.kind, r.is_administrative, r.version, r.event_count,"
            + " v.role_version_id, v.role_name, v.role_status, v.access_scope_kind, v.permission_codes";

    private RoleRows() {
    }

    /** rest: what follows "from platform_identity.roles r join (current version) v": further joins, where, order by. */
    static List<Role> select(JdbcClient jdbc, String rest, Map<String, Object> params) {
        return jdbc.sql("select " + COLUMNS + " from platform_identity.roles r " + CURRENT_VERSION + " " + rest).params(params)
                .query((rs, n) -> {
                    RoleId roleId = new RoleId(rs.getObject("role_id", UUID.class));
                    RoleVersionId versionId = new RoleVersionId(rs.getObject("role_version_id", UUID.class));
                    return Role.restore(roleId, new RoleCode(rs.getString("code")), rs.getString("kind"), rs.getBoolean("is_administrative"), versionId,
                            rs.getString("role_name"), rs.getString("role_status"), AccessScopeKind.valueOf(rs.getString("access_scope_kind")),
                            grantable(roleId, versionId, codes(rs, "permission_codes")), rs.getLong("version"), rs.getLong("event_count"));
                })
                .list();
    }

    static Set<String> codes(ResultSet rs, String column) throws SQLException {
        Array array = rs.getArray(column);
        return array == null ? Set.of() : new HashSet<>(List.of((String[]) array.getArray()));
    }

    /** The codes of a stored version the catalogue still grants; the others are ignored, each with an alert. */
    static Set<String> grantable(RoleId roleId, RoleVersionId versionId, Set<String> stored) {
        Set<String> grantable = new HashSet<>();
        for (String code : stored) {
            if (Permission.isLive(code)) {
                grantable.add(code);
            } else {
                LOG.error("ALERT platform.identity role {} version {} holds the permission code {} that the catalogue does not know or has deprecated:"
                        + " the code is ignored, the other codes of the version count", roleId.value(), versionId.value(), code);
            }
        }
        return grantable;
    }
}
