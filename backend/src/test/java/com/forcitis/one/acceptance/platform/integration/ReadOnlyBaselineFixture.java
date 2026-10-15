package com.forcitis.one.acceptance.platform.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Read-only SQL Server logins whose effective permissions equal the RDS platform baseline (ADR-0037 item 4, B-5 README "有效权限平台基线").
 *
 * <p>The container is not RDS: there the platform grants the server permissions of the baseline to the server role public, so each login's
 * own sys.server_permissions holds only GRANT CONNECT SQL, and SQL Server 2016 knows no finer ones. Here the same server permissions are granted
 * to public and the finer ones SQL Server 2022 derives from them are denied to public, so the effective permissions equal the RDS export
 * exactly and no login holds a server permission of its own beyond CONNECT SQL. rdscore is an empty database with guest enabled, as on RDS.
 *
 * <p>RDS also gives every login a user of its own in msdb (member of the three SQL Agent roles) and in rdscore (no role, CONNECT only); one-ops
 * read that off the three real logins before C-14 rc.3 and the user took it into the baseline on 2026-10-15 (「纳入基线」). Every login here gets the
 * same two users, so the container shows what RDS shows.
 */
final class ReadOnlyBaselineFixture {

    /** B-5 README, "有效权限平台基线": real logins of the four accounts exported with fn_my_permissions(NULL, 'SERVER'), identical. */
    static final Set<String> SERVER_BASELINE = Set.of("ALTER ANY CONNECTION", "ALTER ANY EVENT SESSION", "ALTER ANY LINKED SERVER",
            "ALTER ANY LOGIN", "ALTER SERVER STATE", "ALTER TRACE", "CONNECT SQL", "VIEW ANY DATABASE", "VIEW SERVER STATE");

    static final Set<String> DATABASE_BASELINE = Set.of("CONNECT", "KILL DATABASE CONNECTION", "SELECT", "SHOWPLAN",
            "VIEW ANY COLUMN ENCRYPTION KEY DEFINITION", "VIEW ANY COLUMN MASTER KEY DEFINITION", "VIEW DATABASE STATE");

    static final Set<String> GUEST_DATABASES = Set.of("master", "msdb", "rdscore", "tempdb");

    static final String DATABASE_DENIES = "INSERT, UPDATE, DELETE, ALTER, EXECUTE, REFERENCES, CREATE TABLE, CREATE VIEW, CREATE PROCEDURE, "
            + "CREATE FUNCTION, CREATE SCHEMA";

    /** What the RDS platform grants the server role public; RDS refuses server-level GRANT and DENY, so the account owner cannot change it. */
    static final String PLATFORM_SERVER_GRANTS = "ALTER ANY CONNECTION, ALTER ANY EVENT SESSION, ALTER ANY LINKED SERVER, ALTER ANY LOGIN, "
            + "ALTER SERVER STATE, ALTER TRACE, VIEW SERVER STATE";

    /** Container only: SQL Server 2022 derives these finer permissions from the grants above; the RDS export (SQL Server 2016) shows none of them. */
    static final String SQL_SERVER_2022_ONLY = "ALTER ANY EVENT SESSION ADD EVENT, ALTER ANY EVENT SESSION ADD TARGET, ALTER ANY EVENT SESSION DISABLE, "
            + "ALTER ANY EVENT SESSION DROP EVENT, ALTER ANY EVENT SESSION DROP TARGET, ALTER ANY EVENT SESSION ENABLE, ALTER ANY EVENT SESSION OPTION, "
            + "CREATE ANY EVENT SESSION, DROP ANY EVENT SESSION, CREATE LOGIN, VIEW SERVER PERFORMANCE STATE, VIEW SERVER SECURITY STATE";

    static final String VIEW_SCHEMA = "forcitis_one";

    /** The roles of the login's own msdb user on RDS (one-ops sample, C-14 rc.3); SQL Server ships them in msdb whether or not the Agent runs. */
    static final List<String> MSDB_AGENT_ROLES = List.of("SQLAgentOperatorRole", "SQLAgentReaderRole", "SQLAgentUserRole");

    @FunctionalInterface
    interface Administrator {
        void run(String database, String... statements) throws SQLException;
    }

    private ReadOnlyBaselineFixture() {
    }

    /**
     * Before any login, once per container; repeatable, because classes share one container per fork: GRANT and DENY to the same permissions again change
     * nothing and rdscore is made only when missing. Affects every login but sa, which sysadmin puts above both the grants and the denies.
     */
    static void createRdsPlatform(Administrator sa) throws SQLException {
        sa.run("master", "GRANT " + PLATFORM_SERVER_GRANTS + " TO public", "DENY " + SQL_SERVER_2022_ONLY + " TO public",
                "IF DB_ID(N'rdscore') IS NULL CREATE DATABASE rdscore");
        sa.run("rdscore", "GRANT CONNECT TO guest");
    }

    /** The organisation views account: reads the whole view schema, default schema is the view schema, which must already exist. */
    static void createViewSchemaReader(Administrator sa, String database, String login, String password) throws SQLException {
        String user = createServerLogin(sa, login, password);
        sa.run(database, "CREATE USER " + user + " FOR LOGIN " + user + " WITH DEFAULT_SCHEMA = " + VIEW_SCHEMA,
                "GRANT SELECT ON SCHEMA::" + VIEW_SCHEMA + " TO " + user,
                "DENY INSERT, UPDATE, DELETE, EXECUTE, ALTER, REFERENCES ON SCHEMA::dbo TO " + user,
                "DENY INSERT, UPDATE, DELETE, EXECUTE, ALTER, REFERENCES ON SCHEMA::" + VIEW_SCHEMA + " TO " + user);
        grantDatabaseBaseline(sa, database, user);
    }

    /** An old-system account: SELECT on exactly one view of dbo, which must already exist. */
    static void createSingleViewReader(Administrator sa, String database, String view, String login, String password) throws SQLException {
        String user = createServerLogin(sa, login, password);
        sa.run(database, "CREATE USER " + user + " FOR LOGIN " + user + " WITH DEFAULT_SCHEMA = dbo",
                "GRANT SELECT ON " + view + " TO " + user);
        grantDatabaseBaseline(sa, database, user);
    }

    private static String createServerLogin(Administrator sa, String login, String password) throws SQLException {
        String user = q(login);
        sa.run("master", "CREATE LOGIN " + user + " WITH PASSWORD = N'" + password + "', CHECK_POLICY = OFF");
        createRdsOwnUsers(sa, user);
        return user;
    }

    /** A missing SQL Agent role fails here: the baseline is not reproduced, so nothing built on it is believed. */
    private static void createRdsOwnUsers(Administrator sa, String user) throws SQLException {
        List<String> msdb = new ArrayList<>(List.of("CREATE USER " + user + " FOR LOGIN " + user));
        MSDB_AGENT_ROLES.forEach(role -> msdb.add("ALTER ROLE " + role + " ADD MEMBER " + user));
        sa.run("msdb", msdb.toArray(String[]::new));
        sa.run("rdscore", "CREATE USER " + user + " FOR LOGIN " + user);
    }

    private static void grantDatabaseBaseline(Administrator sa, String database, String user) throws SQLException {
        sa.run(database, "GRANT SELECT TO " + user, "DENY " + DATABASE_DENIES + " TO " + user);
    }

    static void assertMatchesTheRdsBaseline(String jdbcUrl, String database, String login, String password) throws SQLException {
        try (Connection c = DriverManager.getConnection(jdbcUrl + ";loginTimeout=30", login, password); Statement s = c.createStatement()) {
            assertThat(strings(s, "SELECT permission_name FROM fn_my_permissions(NULL, 'SERVER')")).as("server, %s", login)
                    .containsExactlyInAnyOrderElementsOf(SERVER_BASELINE);
            assertThat(strings(s, "SELECT CONCAT(class, ' ', permission_name, ' ', state_desc) FROM sys.server_permissions "
                    + "WHERE grantee_principal_id = SUSER_ID()")).as("the login's own server permissions, %s (RDS: one row each)", login)
                    .containsExactly("100 CONNECT SQL GRANT");
            assertThat(strings(s, "SELECT permission_name FROM fn_my_permissions(NULL, 'DATABASE')")).as("database, %s", login)
                    .containsExactlyInAnyOrderElementsOf(DATABASE_BASELINE);
            assertThat(strings(s, "SELECT name FROM sys.user_token WHERE principal_id <> USER_ID()")).as("roles").containsExactly("public");
            Set<String> reachable = new TreeSet<>(GUEST_DATABASES);
            reachable.add(database);
            assertThat(strings(s, "SELECT name FROM sys.databases WHERE HAS_DBACCESS(name) = 1")).as("databases")
                    .containsExactlyInAnyOrderElementsOf(reachable);
            assertThat(strings(s, "SELECT permission_name FROM sys.database_permissions WHERE class = 0 AND state = 'D' AND grantee_principal_id = USER_ID()"))
                    .as("database-level DENY").containsExactlyInAnyOrder("INSERT", "UPDATE", "DELETE", "ALTER", "EXECUTE", "REFERENCES",
                            "CREATE TABLE", "CREATE VIEW", "CREATE PROCEDURE", "CREATE FUNCTION", "CREATE SCHEMA");
            assertThat(strings(s, "SELECT permission_name FROM sys.database_permissions WHERE class = 0 AND state = 'G' AND grantee_principal_id = USER_ID()"))
                    .as("database-level GRANT").containsExactlyInAnyOrder("CONNECT", "SELECT");
            assertOwnUsersMatchRds(s, login);
        }
    }

    /** Asked as the login itself: it sees its own role memberships in msdb and rdscore, and its own jobs through sysjobs_view (SQLAgentUserRole). */
    private static void assertOwnUsersMatchRds(Statement s, String login) throws SQLException {
        assertThat(strings(s, ownRoles("msdb"))).as("roles of the own msdb user, %s", login).containsExactlyInAnyOrderElementsOf(MSDB_AGENT_ROLES);
        assertThat(strings(s, ownPermissions("msdb"))).as("permissions of the own msdb user itself, %s", login).containsExactly("DATABASE G CONNECT");
        assertThat(strings(s, "SELECT COUNT(*) FROM rdscore.sys.database_principals WHERE sid = SUSER_SID()")).as("own rdscore user, %s", login)
                .containsExactly("1");
        assertThat(strings(s, ownRoles("rdscore"))).as("roles of the own rdscore user, %s", login).isEmpty();
        assertThat(strings(s, ownPermissions("rdscore"))).as("permissions of the own rdscore user, %s", login).containsExactly("DATABASE G CONNECT");
        for (String database : List.of("master", "tempdb")) {
            assertThat(strings(s, "SELECT COUNT(*) FROM " + database + ".sys.database_principals WHERE sid = SUSER_SID()"))
                    .as("no own user in %s, %s", database, login).containsExactly("0");
        }
        assertThat(strings(s, "SELECT COUNT(*) FROM msdb.dbo.sysjobs_view WHERE owner_sid = SUSER_SID()")).as("Agent jobs owned, %s", login)
                .containsExactly("0");
    }

    /** Role names of the login's own user in a system database, read through three-part names from the login's own database. */
    static String ownRoles(String database) {
        return "SELECT r.name FROM " + database + ".sys.database_role_members m JOIN " + database + ".sys.database_principals u "
                + "ON u.principal_id = m.member_principal_id JOIN " + database + ".sys.database_principals r ON r.principal_id = m.role_principal_id "
                + "WHERE u.sid = SUSER_SID()";
    }

    private static String ownPermissions(String database) {
        return "SELECT CONCAT(p.class_desc, ' ', p.state, ' ', p.permission_name) FROM " + database + ".sys.database_permissions p JOIN " + database
                + ".sys.database_principals u ON u.principal_id = p.grantee_principal_id WHERE u.sid = SUSER_SID()";
    }

    static List<String> strings(Statement s, String query) throws SQLException {
        List<String> values = new ArrayList<>();
        try (ResultSet rs = s.executeQuery(query)) {
            while (rs.next()) {
                values.add(rs.getString(1).trim());
            }
        }
        return values;
    }

    static String q(String name) {
        return "[" + name + "]";
    }
}
