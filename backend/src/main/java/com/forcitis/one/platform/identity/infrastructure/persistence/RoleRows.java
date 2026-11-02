package com.forcitis.one.platform.identity.infrastructure.persistence;

import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.platform.identity.api.AccessScopeKind;
import com.forcitis.one.platform.identity.api.CustomOrgUnit;
import com.forcitis.one.platform.identity.api.Permission;
import com.forcitis.one.platform.identity.api.RoleCode;
import com.forcitis.one.platform.identity.api.RoleId;
import com.forcitis.one.platform.identity.api.RoleVersionId;
import com.forcitis.one.platform.identity.domain.Role;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Reads roles with their current version (ADR-0034 decision 1 item 2: current = the row not yet closed, see Validity) and that version's custom org
 * units (ADR-0034 section 5), the units of all roles read in one more statement. A role without a current version (a deleted role) is
 * never read. A permission code the catalogue does not know or has deprecated is left out of the role and alerted; the other codes of the
 * version still count and nothing is thrown (decision 1 item 3, fail-closed).
 */
final class RoleRows {

    private static final Logger LOG = LoggerFactory.getLogger(RoleRows.class);

    /** The only way identity reads the current version of a role: the join every role read goes through. */
    static final String CURRENT_VERSION = "join platform_identity.role_versions v on v.role_id = r.role_id and v." + Validity.CURRENT + "\n";

    private static final String COLUMNS = """
            r.role_id, r.code, r.kind, r.is_administrative, r.version, r.event_count,
            v.role_version_id, v.role_name, v.role_status, v.access_scope_kind, v.permission_codes
            """;

    private RoleRows() {
    }

    /** One row of the role read, before the version's custom org units are attached. */
    private record Row(RoleId roleId, RoleCode code, String kind, boolean administrative, RoleVersionId versionId, String name, String status,
                       AccessScopeKind accessScope, Set<String> permissionCodes, long version, long eventCount) {
    }

    /** rest: what follows "from platform_identity.roles r join (current version) v": further joins, where, order by. */
    static List<Role> select(JdbcClient jdbc, String rest, Map<String, Object> params) {
        List<Row> rows = jdbc.sql("""
                select
                """ + COLUMNS + """
                 from platform_identity.roles r
                """ + CURRENT_VERSION + rest).params(params)
                .query((rs, n) -> {
                    RoleId roleId = new RoleId(rs.getObject("role_id", UUID.class));
                    RoleVersionId versionId = new RoleVersionId(rs.getObject("role_version_id", UUID.class));
                    return new Row(roleId, new RoleCode(rs.getString("code")), rs.getString("kind"), rs.getBoolean("is_administrative"), versionId,
                            rs.getString("role_name"), rs.getString("role_status"), AccessScopeKind.valueOf(rs.getString("access_scope_kind")),
                            grantable(roleId, versionId, codes(rs, "permission_codes")), rs.getLong("version"), rs.getLong("event_count"));
                })
                .list();
        Map<RoleVersionId, Set<CustomOrgUnit>> units = customOrgUnits(jdbc, rows.stream().map(Row::versionId).toList());
        return rows.stream().map(row -> Role.restore(row.roleId(), row.code(), row.kind(), row.administrative(), row.versionId(), row.name(), row.status(),
                row.accessScope(), row.permissionCodes(), units.getOrDefault(row.versionId(), Set.of()), row.version(), row.eventCount())).toList();
    }

    /** The custom org units of the given role versions, in one statement; a version without units is absent from the map. */
    static Map<RoleVersionId, Set<CustomOrgUnit>> customOrgUnits(JdbcClient jdbc, Collection<RoleVersionId> versions) {
        Map<RoleVersionId, Set<CustomOrgUnit>> units = new HashMap<>();
        if (versions.isEmpty()) {
            return units;
        }
        jdbc.sql("""
                        select role_version_id, org_unit_id, is_descendants_included from platform_identity.role_version_custom_org_units
                         where role_version_id in (:versions)
                        """)
                .param("versions", versions.stream().map(RoleVersionId::value).distinct().toList())
                .query((ResultSet rs) -> {
                    units.computeIfAbsent(new RoleVersionId(rs.getObject("role_version_id", UUID.class)), id -> new HashSet<>())
                            .add(new CustomOrgUnit(new OrgUnitId(rs.getObject("org_unit_id", UUID.class)), rs.getBoolean("is_descendants_included")));
                });
        return units;
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
