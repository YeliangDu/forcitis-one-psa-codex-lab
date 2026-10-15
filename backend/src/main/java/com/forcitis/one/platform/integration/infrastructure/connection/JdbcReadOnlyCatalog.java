package com.forcitis.one.platform.integration.infrastructure.connection;

import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.DatabaseLevelPermission;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.Ownership;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.ReachableDatabase;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.SecurablePermissionRow;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.ServerPermissionRow;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;

/** SQL Server 2016 catalog only: on any other database every query fails, so every item is undeterminable and the login is refused. */
final class JdbcReadOnlyCatalog implements ReadOnlyCatalog {

    static final String DATABASE_PERMISSIONS = "SELECT permission_name FROM fn_my_permissions(NULL, 'DATABASE')";
    static final String SERVER_PERMISSIONS = "SELECT permission_name FROM fn_my_permissions(NULL, 'SERVER')";
    static final String SERVER_PERMISSION_ROWS = "SELECT class, class_desc, state, permission_name FROM sys.server_permissions "
            + "WHERE grantee_principal_id = (SELECT principal_id FROM sys.server_principals WHERE sid = SUSER_SID())";
    static final String DATABASE_LEVEL_PERMISSIONS = "SELECT state, permission_name FROM sys.database_permissions "
            + "WHERE class = 0 AND grantee_principal_id = DATABASE_PRINCIPAL_ID()";
    static final String SECURABLE_PERMISSION_ROWS = """
            SELECT class_desc, major_id, state, permission_name, CASE WHEN grantee_principal_id = 0 THEN 1 ELSE 0 END
            FROM sys.database_permissions
            WHERE class <> 0 AND (grantee_principal_id = DATABASE_PRINCIPAL_ID() OR (grantee_principal_id = 0 AND major_id > 0))
            """;
    static final String ROLES = "SELECT name FROM sys.user_token WHERE principal_id <> DATABASE_PRINCIPAL_ID()";
    static final String LISTED_FIXED_SERVER_ROLES = "SELECT name, IS_SRVROLEMEMBER(name) FROM sys.server_principals "
            + "WHERE type = 'R' AND is_fixed_role = 1";
    static final String OWNERSHIP = """
            SELECT CASE WHEN DATABASE_PRINCIPAL_ID() = 1 THEN 1 ELSE 0 END,
                   (SELECT COUNT(*) FROM sys.schemas WHERE principal_id = DATABASE_PRINCIPAL_ID()),
                   (SELECT COUNT(*) FROM sys.objects WHERE principal_id = DATABASE_PRINCIPAL_ID()),
                   (SELECT COUNT(*) FROM sys.database_principals WHERE owning_principal_id = DATABASE_PRINCIPAL_ID()),
                   (SELECT COUNT(*) FROM sys.database_permissions WHERE class = 4 AND grantee_principal_id = DATABASE_PRINCIPAL_ID())
            """;
    static final String REACHABLE_DATABASES = "SELECT database_id, name FROM sys.databases WHERE HAS_DBACCESS(name) = 1";
    static final String OWN_USERS = "SELECT COUNT(*) FROM %s.sys.database_principals WHERE sid = SUSER_SID()";
    static final String CURRENT_DATABASE = "SELECT DB_NAME()";

    private final JdbcClient jdbc;

    JdbcReadOnlyCatalog(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<String> databasePermissions() {
        return jdbc.sql(DATABASE_PERMISSIONS).query(String.class).list();
    }

    @Override
    public List<String> serverPermissions() {
        return jdbc.sql(SERVER_PERMISSIONS).query(String.class).list();
    }

    @Override
    public List<ServerPermissionRow> serverPermissionRows() {
        return jdbc.sql(SERVER_PERMISSION_ROWS)
                .query((rs, n) -> new ServerPermissionRow(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4))).list();
    }

    @Override
    public List<DatabaseLevelPermission> databaseLevelPermissions() {
        return jdbc.sql(DATABASE_LEVEL_PERMISSIONS).query((rs, n) -> new DatabaseLevelPermission(rs.getString(1), rs.getString(2))).list();
    }

    @Override
    public List<SecurablePermissionRow> securablePermissionRows() {
        return jdbc.sql(SECURABLE_PERMISSION_ROWS).query((rs, n) -> new SecurablePermissionRow(rs.getString(1), rs.getLong(2), rs.getString(3),
                rs.getString(4), rs.getInt(5) == 1)).list();
    }

    @Override
    public List<String> roles() {
        return jdbc.sql(ROLES).query(String.class).list();
    }

    @Override
    public Map<String, Integer> fixedServerRoleMemberships(Set<String> alwaysAsked) {
        Map<String, Integer> memberships = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        jdbc.sql(LISTED_FIXED_SERVER_ROLES).query((rs, n) -> memberships.put(rs.getString(1), (Integer) rs.getObject(2))).list();
        if (!alwaysAsked.isEmpty()) {
            alwaysAsked.stream().filter(role -> !role.matches("[a-z]+")).findAny().ifPresent(role -> {
                throw new IllegalArgumentException("a fixed server role name is lower-case letters only, was " + role);
            });
            String asked = alwaysAsked.stream().sorted()
                    .map(role -> "SELECT '" + role + "', IS_SRVROLEMEMBER('" + role + "')")
                    .collect(Collectors.joining(" UNION ALL "));
            jdbc.sql(asked).query((rs, n) -> memberships.put(rs.getString(1), (Integer) rs.getObject(2))).list();
        }
        return memberships;
    }

    @Override
    public Ownership ownership() {
        return jdbc.sql(OWNERSHIP).query((rs, n) -> new Ownership(rs.getInt(1) == 1, rs.getLong(2), rs.getLong(3), rs.getLong(4), rs.getLong(5))).single();
    }

    @Override
    public List<ReachableDatabase> reachableDatabases() {
        return jdbc.sql(REACHABLE_DATABASES).query((rs, n) -> new ReachableDatabase(rs.getInt(1), rs.getString(2))).list();
    }

    @Override
    public long ownUsers(String systemDatabase) {
        return jdbc.sql(OWN_USERS.formatted(bracketed(systemDatabase))).query(Long.class).single();
    }

    @Override
    public String currentDatabase() {
        return jdbc.sql(CURRENT_DATABASE).query(String.class).single();
    }

    private static String bracketed(String database) {
        return "[" + database.replace("]", "]]") + "]";
    }
}
