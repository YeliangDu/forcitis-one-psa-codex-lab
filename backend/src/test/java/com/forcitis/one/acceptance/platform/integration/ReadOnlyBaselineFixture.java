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
import java.util.function.Function;

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
        // the global counterexamples of MSDB_COUNTEREXAMPLES undo themselves; a container left dirty by a killed JVM is made clean here
        sa.run("msdb", "IF IS_ROLEMEMBER('db_owner', 'SQLAgentUserRole') = 1 ALTER ROLE db_owner DROP MEMBER SQLAgentUserRole",
                "REVOKE CONTROL ON DATABASE::msdb FROM SQLAgentUserRole", "REVOKE ALTER ANY SCHEMA FROM public");
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
        assertThat(ownMsdbPrivileges(s)).as("C-14 (f) H-1: privileges of the own msdb user beyond the baseline, %s", login).isEmpty();
    }

    /** The database-level permissions of the msdb review H-1 (one-coordinator 2026-10-15): any one of them effective for the own msdb user is a violation. */
    static final List<String> MSDB_PRIVILEGES = List.of("CONTROL", "ALTER", "ALTER ANY ROLE", "ALTER ANY USER", "ALTER ANY SCHEMA", "CREATE PROCEDURE");

    /**
     * What the login's own msdb user holds beyond the baseline, asked as the login itself inside msdb: "EFFECTIVE p" for each of {@link #MSDB_PRIVILEGES}
     * that HAS_PERMS_BY_NAME('msdb', 'DATABASE', p) finds true (roles, nesting and public included), and "DIRECT class state permission" for each permission
     * granted to the user itself outside the allowed shape (database: GRANT CONNECT; object: GRANT SELECT, GRANT EXECUTE, any DENY; never WITH GRANT OPTION).
     */
    static List<String> ownMsdbPrivileges(Statement s) throws SQLException {
        Connection c = s.getConnection();
        String home = c.getCatalog();
        c.setCatalog("msdb");
        try {
            List<String> found = new ArrayList<>();
            for (String privilege : MSDB_PRIVILEGES) {
                if (strings(s, "SELECT HAS_PERMS_BY_NAME('msdb', 'DATABASE', '" + privilege + "')").contains("1")) {
                    found.add("EFFECTIVE " + privilege);
                }
            }
            found.addAll(strings(s, "SELECT CONCAT('DIRECT ', p.class_desc, ' ', p.state, ' ', p.permission_name) FROM sys.database_permissions p "
                    + "JOIN sys.database_principals u ON u.principal_id = p.grantee_principal_id WHERE u.sid = SUSER_SID() AND NOT ("
                    + "(p.class = 0 AND p.state = 'G' AND p.permission_name = 'CONNECT') OR "
                    + "(p.class = 1 AND ((p.state = 'G' AND p.permission_name IN ('SELECT', 'EXECUTE')) OR p.state = 'D')))"));
            return found;
        } finally {
            c.setCatalog(home);
        }
    }

    static List<String> ownMsdbPrivileges(String jdbcUrl, String login, String password) throws SQLException {
        try (Connection c = DriverManager.getConnection(jdbcUrl + ";loginTimeout=30", login, password); Statement s = c.createStatement()) {
            return ownMsdbPrivileges(s);
        }
    }

    /**
     * A way for the own msdb user to hold more than the baseline (review H-1). apply and undo take the quoted login name and run in msdb as sa; undo must
     * leave msdb as it was. effective and direct are what {@link #ownMsdbPrivileges} must report while it holds, so a counterexample that does not really
     * give the privilege fails the fixture check instead of passing for nothing. global: changes msdb for every login in it (a SQL Agent role or public),
     * so it must not run while another test of the same container uses msdb; classes of one fork run one after the other.
     */
    record MsdbCounterexample(String name, boolean global, Function<String, List<String>> apply, Function<String, List<String>> undo, Set<String> effective,
            Set<String> direct) {

        List<String> expected() {
            List<String> all = new ArrayList<>();
            effective.forEach(p -> all.add("EFFECTIVE " + p));
            direct.forEach(d -> all.add("DIRECT " + d));
            return all;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /** An msdb object the SQL Agent roles do not grant: used where a counterexample or the one-ops sample needs a direct object-level grant. */
    static final String MSDB_PROCEDURE = "dbo.sp_help_category";

    static final List<MsdbCounterexample> MSDB_COUNTEREXAMPLES = List.of(
            new MsdbCounterexample("(1) GRANT CONTROL ON DATABASE::msdb", false, u -> List.of("GRANT CONTROL ON DATABASE::msdb TO " + u),
                    u -> List.of("REVOKE CONTROL ON DATABASE::msdb FROM " + u), Set.copyOf(MSDB_PRIVILEGES), Set.of("DATABASE G CONTROL")),
            new MsdbCounterexample("(2) GRANT ALTER ANY ROLE", false, u -> List.of("GRANT ALTER ANY ROLE TO " + u), u -> List.of("REVOKE ALTER ANY ROLE FROM " + u),
                    Set.of("ALTER ANY ROLE"), Set.of("DATABASE G ALTER ANY ROLE")),
            new MsdbCounterexample("(3) GRANT IMPERSONATE ON USER::dbo", false, u -> List.of("GRANT IMPERSONATE ON USER::dbo TO " + u),
                    u -> List.of("REVOKE IMPERSONATE ON USER::dbo FROM " + u), Set.of(), Set.of("DATABASE_PRINCIPAL G IMPERSONATE")),
            new MsdbCounterexample("(4) GRANT ALTER ON SCHEMA::dbo and CREATE PROCEDURE", false,
                    u -> List.of("GRANT ALTER ON SCHEMA::dbo TO " + u, "GRANT CREATE PROCEDURE TO " + u),
                    u -> List.of("REVOKE ALTER ON SCHEMA::dbo FROM " + u, "REVOKE CREATE PROCEDURE FROM " + u), Set.of("CREATE PROCEDURE"),
                    Set.of("SCHEMA G ALTER", "DATABASE G CREATE PROCEDURE")),
            new MsdbCounterexample("(5) role nesting: db_owner gets SQLAgentUserRole as a member", true,
                    u -> List.of("ALTER ROLE db_owner ADD MEMBER SQLAgentUserRole"), u -> List.of("ALTER ROLE db_owner DROP MEMBER SQLAgentUserRole"),
                    Set.copyOf(MSDB_PRIVILEGES), Set.of()),
            new MsdbCounterexample("(6) GRANT CONTROL ON DATABASE::msdb TO SQLAgentUserRole", true,
                    u -> List.of("GRANT CONTROL ON DATABASE::msdb TO SQLAgentUserRole"), u -> List.of("REVOKE CONTROL ON DATABASE::msdb FROM SQLAgentUserRole"),
                    Set.copyOf(MSDB_PRIVILEGES), Set.of()),
            new MsdbCounterexample("(7) GRANT ALTER ANY SCHEMA TO public in msdb", true, u -> List.of("GRANT ALTER ANY SCHEMA TO public"),
                    u -> List.of("REVOKE ALTER ANY SCHEMA FROM public"), Set.of("ALTER ANY SCHEMA"), Set.of()),
            new MsdbCounterexample("(8) GRANT EXECUTE ON " + MSDB_PROCEDURE + " WITH GRANT OPTION", false,
                    u -> List.of("GRANT EXECUTE ON " + MSDB_PROCEDURE + " TO " + u + " WITH GRANT OPTION"),
                    u -> List.of("REVOKE EXECUTE ON " + MSDB_PROCEDURE + " FROM " + u + " CASCADE"), Set.of(), Set.of("OBJECT_OR_COLUMN W EXECUTE")));

    /**
     * What one-ops read off the real accounts on RDS (34 platform defaults): beside CONNECT, object-level GRANT SELECT several times, GRANT EXECUTE and one
     * DENY SELECT directly on the own msdb user. The baseline plus this must stay inside the allowed shape; msdb objects chosen here, not RDS's.
     */
    static List<String> oneOpsSampleMsdbGrants(String quotedUser) {
        return List.of("GRANT SELECT ON OBJECT::dbo.backupset TO " + quotedUser, "GRANT SELECT ON OBJECT::dbo.backupfile TO " + quotedUser,
                "GRANT SELECT ON OBJECT::dbo.backupmediaset TO " + quotedUser, "GRANT EXECUTE ON OBJECT::" + MSDB_PROCEDURE + " TO " + quotedUser,
                "DENY SELECT ON OBJECT::dbo.restorehistory TO " + quotedUser);
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
