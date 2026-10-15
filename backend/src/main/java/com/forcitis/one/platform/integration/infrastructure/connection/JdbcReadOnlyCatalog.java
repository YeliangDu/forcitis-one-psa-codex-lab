package com.forcitis.one.platform.integration.infrastructure.connection;

import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.DatabaseLevelPermission;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.Ownership;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.ReachableDatabase;
import com.forcitis.one.platform.integration.infrastructure.connection.EffectiveGrants.SecurablePermissionRow;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.ServerPermissionRow;
import com.forcitis.one.platform.integration.infrastructure.connection.SystemDatabaseUsers.Membership;
import com.forcitis.one.platform.integration.infrastructure.connection.SystemDatabaseUsers.OwnUserPermission;
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
            SELECT p.class_desc, p.major_id, p.state, p.permission_name, CASE WHEN p.grantee_principal_id = 0 THEN 1 ELSE 0 END,
                   CASE p.class WHEN 3 THEN p.major_id WHEN 1 THEN o.schema_id END, s.name, o.name,
                   CASE WHEN p.grantee_principal_id = DATABASE_PRINCIPAL_ID() THEN NULL ELSE g.name END, g.type_desc
            FROM sys.database_permissions p
            LEFT JOIN sys.objects o ON p.class = 1 AND o.object_id = p.major_id
            LEFT JOIN sys.schemas s ON s.schema_id = CASE p.class WHEN 3 THEN p.major_id WHEN 1 THEN o.schema_id END
            LEFT JOIN sys.database_principals g ON g.principal_id = p.grantee_principal_id
            WHERE p.class <> 0 AND (p.grantee_principal_id = DATABASE_PRINCIPAL_ID() OR (p.grantee_principal_id = 0 AND p.major_id > 0))
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
    static final String OWN_USER_MEMBERSHIPS = """
            SELECT CASE WHEN m.role_principal_id IS NULL THEN 0 ELSE 1 END, r.name
            FROM %1$s.sys.database_principals u
            LEFT JOIN %1$s.sys.database_role_members m ON m.member_principal_id = u.principal_id
            LEFT JOIN %1$s.sys.database_principals r ON r.principal_id = m.role_principal_id
            WHERE u.sid = SUSER_SID()
            """;
    static final String OWN_USER_PERMISSIONS = "SELECT p.class_desc, p.state, p.permission_name FROM %1$s.sys.database_permissions p "
            + "JOIN %1$s.sys.database_principals u ON u.principal_id = p.grantee_principal_id WHERE u.sid = SUSER_SID()";
    /** Not msdb.dbo.sysjobs: only TargetServersRole may read it, so a member of the SQL Agent roles gets 229 (SQL Server 2022 container, 2026-10-15). */
    static final String OWNED_AGENT_JOBS = "SELECT COUNT(*) FROM msdb.dbo.sysjobs_view WHERE owner_sid = SUSER_SID()";
    /** Through msdb's sp_executesql, so that HAS_PERMS_BY_NAME answers for the login's user in msdb, not in its own database. */
    static final String MSDB_EFFECTIVE_PERMISSIONS = "EXEC msdb.sys.sp_executesql N'SELECT v.p, HAS_PERMS_BY_NAME(N''msdb'', N''DATABASE'', v.p) FROM (VALUES "
            + MsdbPrivileges.EFFECTIVE.stream().map(p -> "(N''" + p + "'')").collect(Collectors.joining(", ")) + ") v (p)'";
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
                rs.getString(4), rs.getInt(5) == 1, rs.getObject(6) == null ? null : rs.getLong(6), rs.getString(7), rs.getString(8), rs.getString(9),
                rs.getString(10))).list();
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
    public List<Membership> ownUserMemberships(String systemDatabase) {
        return jdbc.sql(OWN_USER_MEMBERSHIPS.formatted(bracketed(systemDatabase)))
                .query((rs, n) -> new Membership(rs.getInt(1) == 1, rs.getString(2))).list();
    }

    @Override
    public List<OwnUserPermission> ownUserPermissions(String systemDatabase) {
        return jdbc.sql(OWN_USER_PERMISSIONS.formatted(bracketed(systemDatabase)))
                .query((rs, n) -> new OwnUserPermission(rs.getString(1), rs.getString(2), rs.getString(3))).list();
    }

    @Override
    public Map<String, Integer> msdbEffectivePermissions() {
        Map<String, Integer> effective = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        jdbc.sql(MSDB_EFFECTIVE_PERMISSIONS).query((rs, n) -> effective.put(rs.getString(1), (Integer) rs.getObject(2))).list();
        return effective;
    }

    @Override
    public long ownedAgentJobs() {
        return jdbc.sql(OWNED_AGENT_JOBS).query(Long.class).single();
    }

    @Override
    public String currentDatabase() {
        return jdbc.sql(CURRENT_DATABASE).query(String.class).single();
    }

    private static String bracketed(String database) {
        return "[" + database.replace("]", "]]") + "]";
    }
}
