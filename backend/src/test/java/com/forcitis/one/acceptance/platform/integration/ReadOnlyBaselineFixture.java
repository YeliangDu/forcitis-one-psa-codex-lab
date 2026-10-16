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

    /** Review H-A: a role of our own, made and dropped by the nesting counterexample; createRdsPlatform drops it again if a killed JVM left it. */
    static final String SCHEMA_OWNED_PROCEDURE = "forcitis_schema_owned_proc";

    static final String NESTED_ROLE = "forcitis_nested_role";

    /** Review H-A: the only roles the msdb token may hold beside the own user (or guest): public and the three SQL Agent roles. */
    static final List<String> MSDB_TOKEN_ROLES_ALLOWED = List.of("public", "SQLAgentUserRole", "SQLAgentReaderRole", "SQLAgentOperatorRole");

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
                "REVOKE CONTROL ON DATABASE::msdb FROM SQLAgentUserRole", "REVOKE ALTER ANY SCHEMA FROM public",
                "REVOKE TAKE OWNERSHIP ON DATABASE::msdb FROM SQLAgentUserRole", "REVOKE ALTER ANY DATABASE DDL TRIGGER FROM public",
                "REVOKE CREATE FUNCTION FROM SQLAgentUserRole", "REVOKE IMPERSONATE ON USER::dbo FROM SQLAgentUserRole",
                "REVOKE ALTER ON SCHEMA::dbo FROM SQLAgentUserRole", "REVOKE ALTER ON OBJECT::" + MSDB_PROCEDURE + " FROM public",
                "REVOKE ALTER ANY SCHEMA FROM guest", GUEST_READS_AGENT_JOBS_UNDO,
                // review H-A: the nesting counterexamples undo themselves too
                "IF IS_ROLEMEMBER('db_ssisadmin', 'SQLAgentUserRole') = 1 ALTER ROLE db_ssisadmin DROP MEMBER SQLAgentUserRole",
                "IF IS_ROLEMEMBER('" + NESTED_ROLE + "', 'SQLAgentUserRole') = 1 ALTER ROLE " + NESTED_ROLE + " DROP MEMBER SQLAgentUserRole",
                "DROP PROCEDURE IF EXISTS SQLAgentUserRole." + SCHEMA_OWNED_PROCEDURE,
                "DROP ROLE IF EXISTS " + NESTED_ROLE, "IF IS_ROLEMEMBER('DatabaseMailUserRole', 'guest') = 1 ALTER ROLE DatabaseMailUserRole DROP MEMBER guest");
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

    /**
     * Review H-1': every grant to a principal of the login's token in msdb (the roles, nested ones, public, and guest when the login has no user of its
     * own there) outside the same class allow-list as the own user's rows; public alone may also hold SQL Server's two default VIEW ANY COLUMN * KEY
     * DEFINITION grants. The own user's rows are left out: they are reported as DIRECT. COLLATE: msdb's catalog and the string literals differ in collation.
     */
    static final String TOKEN_SCOPE_OUTSIDE_THE_ALLOW_LIST = "SELECT DISTINCT CONCAT('TOKEN ', pr.name COLLATE DATABASE_DEFAULT, ' ', "
            + "p.class_desc COLLATE DATABASE_DEFAULT, ' ', p.state COLLATE DATABASE_DEFAULT, ' ', p.permission_name COLLATE DATABASE_DEFAULT) "
            + "FROM sys.database_permissions p JOIN sys.database_principals pr ON pr.principal_id = p.grantee_principal_id "
            + "WHERE p.grantee_principal_id IN (SELECT principal_id FROM sys.user_token) AND ISNULL(pr.sid, 0x00) <> SUSER_SID() AND NOT ("
            + "(p.class = 0 AND p.state = 'G' AND p.permission_name = 'CONNECT') OR "
            + "(p.class = 0 AND p.state = 'G' AND pr.name = 'public' AND p.permission_name IN "
            + "('VIEW ANY COLUMN MASTER KEY DEFINITION', 'VIEW ANY COLUMN ENCRYPTION KEY DEFINITION')) OR "
            + "(p.class = 1 AND ((p.state = 'G' AND p.permission_name IN ('SELECT', 'EXECUTE')) OR p.state = 'D')))";

    /**
     * Review H-A: every ROLE of the token in msdb beyond public and the three SQL Agent roles, whatever it can do (fixed or self-made, nested through a
     * SQL Agent role or, for a login that reaches msdb through guest alone, through guest). Object-level grants of such a role (db_ssisadmin, DatabaseMailUserRole,
     * dc_admin) are inside the class allow-list, so only the membership itself shows. Reported as "TOKEN_ROLE name" (fixture only: the command prints no role name).
     */
    static final String TOKEN_ROLES_OUTSIDE_THE_ALLOW_LIST = "SELECT CONCAT('TOKEN_ROLE ', name COLLATE DATABASE_DEFAULT) FROM sys.user_token WHERE type = 'ROLE' "
            + "AND name COLLATE DATABASE_DEFAULT NOT IN (" + MSDB_TOKEN_ROLES_ALLOWED.stream().map(r -> "N'" + r + "'").collect(java.util.stream.Collectors.joining(", ")) + ")";

    /** msdb's own default: each of these principals owns the schema of its name. */
    static final List<String> PLATFORM_SELF_SCHEMA_OWNERS = List.of("guest", "SQLAgentUserRole", "SQLAgentReaderRole", "SQLAgentOperatorRole");

    /** The principals of the token that count for ownership: the own user (or guest) and the allowed roles; a role beyond them is a violation by itself. */
    private static final String OWNING_TOKEN_MEMBERS = "(SELECT principal_id FROM sys.user_token WHERE type <> 'ROLE' OR name COLLATE DATABASE_DEFAULT IN ("
            + MSDB_TOKEN_ROLES_ALLOWED.stream().map(r -> "N'" + r + "'").collect(java.util.stream.Collectors.joining(", ")) + "))";

    /**
     * Review M-A: a principal of the token owns a role, a schema or an object in msdb. "OWNS ROLE" / "OWNS SCHEMA" / "OWNS OBJECT" and, review M-1, "OWNS CERTIFICATE" / "OWNS KEY" / "OWNS ASSEMBLY" / "OWNS TYPE" /
     * "OWNS XML_SCHEMA_COLLECTION" / "OWNS SCHEMA_OBJECT" (kind only, fixture only). The fixture counts only the own user (or guest) and the allowed roles; production counts the whole token, so it is the stricter.
     * The platform default stays out: guest owns the schema guest and each SQL Agent role the schema of its own name (checked by {@link #assertPlatformSchemasAreOwnedByTheirNamesakes});
     * dbo and the rest are owned by dbo, which no token holds. A schema named like its owner is not exempt in general, only those four.
     */
    static final String OWNED_BY_THE_TOKEN = "SELECT 'OWNS ROLE' WHERE EXISTS (SELECT 1 FROM sys.database_principals WHERE owning_principal_id IN " + OWNING_TOKEN_MEMBERS + ") "
            + "UNION ALL SELECT 'OWNS SCHEMA' WHERE EXISTS (SELECT 1 FROM sys.schemas s JOIN sys.database_principals o ON o.principal_id = s.principal_id "
            + "WHERE s.principal_id IN " + OWNING_TOKEN_MEMBERS + " AND NOT (s.name COLLATE DATABASE_DEFAULT = o.name COLLATE DATABASE_DEFAULT AND o.name COLLATE DATABASE_DEFAULT IN ("
            + PLATFORM_SELF_SCHEMA_OWNERS.stream().map(r -> "N'" + r + "'").collect(java.util.stream.Collectors.joining(", ")) + "))) "
            + "UNION ALL SELECT 'OWNS OBJECT' WHERE EXISTS (SELECT 1 FROM sys.objects WHERE principal_id IN " + OWNING_TOKEN_MEMBERS + ") "
            // review M-1: the other securables an owner holds CONTROL on
            + "UNION ALL SELECT 'OWNS CERTIFICATE' WHERE EXISTS (SELECT 1 FROM sys.certificates WHERE principal_id IN " + OWNING_TOKEN_MEMBERS + ") "
            + "UNION ALL SELECT 'OWNS KEY' WHERE EXISTS (SELECT 1 FROM sys.asymmetric_keys WHERE principal_id IN " + OWNING_TOKEN_MEMBERS + ") "
            + "OR EXISTS (SELECT 1 FROM sys.symmetric_keys WHERE principal_id IN " + OWNING_TOKEN_MEMBERS + ") "
            + "UNION ALL SELECT 'OWNS ASSEMBLY' WHERE EXISTS (SELECT 1 FROM sys.assemblies WHERE principal_id IN " + OWNING_TOKEN_MEMBERS + ") "
            + "UNION ALL SELECT 'OWNS TYPE' WHERE EXISTS (SELECT 1 FROM sys.types WHERE is_user_defined = 1 AND principal_id IN " + OWNING_TOKEN_MEMBERS + ") "
            + "UNION ALL SELECT 'OWNS XML_SCHEMA_COLLECTION' WHERE EXISTS (SELECT 1 FROM sys.xml_schema_collections WHERE principal_id IN " + OWNING_TOKEN_MEMBERS + ") "
            // an object in one of the four allowed schemas has principal_id NULL and belongs to the schema's owner, who is in the token
            + "UNION ALL SELECT 'OWNS SCHEMA_OBJECT' WHERE EXISTS (SELECT 1 FROM sys.objects o JOIN sys.schemas s ON s.schema_id = o.schema_id WHERE o.principal_id IS NULL "
            + "AND s.name COLLATE DATABASE_DEFAULT IN (" + PLATFORM_SELF_SCHEMA_OWNERS.stream().map(r -> "N'" + r + "'").collect(java.util.stream.Collectors.joining(", ")) + "))";

    /** Review M-A, asked as sa in msdb: the default ownerships the check must let through really exist (guest and the SQL Agent roles each own the schema of their name). */
    static void assertPlatformSchemasAreOwnedByTheirNamesakes(Statement sa) throws SQLException {
        for (String owner : PLATFORM_SELF_SCHEMA_OWNERS) {
            assertThat(strings(sa, "SELECT COUNT(*) FROM sys.schemas s JOIN sys.database_principals p ON p.principal_id = s.principal_id WHERE s.name = N'" + owner
                    + "' AND p.name = N'" + owner + "'")).as("msdb: %s owns the schema %s", owner, owner).containsExactly("1");
        }
    }

    /** The database-level permissions of the msdb review H-1 (one-coordinator 2026-10-15): any one of them effective for the own msdb user is a violation. */
    static final List<String> MSDB_PRIVILEGES = List.of("CONTROL", "ALTER", "ALTER ANY ROLE", "ALTER ANY USER", "ALTER ANY SCHEMA", "CREATE PROCEDURE");

    /**
     * What the login's own msdb user holds beyond the baseline, asked as the login itself inside msdb: "EFFECTIVE p" for each of {@link #MSDB_PRIVILEGES}
     * that HAS_PERMS_BY_NAME('msdb', 'DATABASE', p) finds true (roles, nesting and public included), and "DIRECT class state permission" for each permission
     * granted to the user itself outside the allowed shape (database: GRANT CONNECT; object: GRANT SELECT, GRANT EXECUTE, any DENY; never WITH GRANT OPTION).
     * Review H-1': "EFFECTIVE IMPERSONATE" when HAS_PERMS_BY_NAME('dbo', 'USER', 'IMPERSONATE') is true (a user-level permission the six cannot see), and
     * "TOKEN principal class state permission" for each grant to another principal of the token outside the allow-list ({@link #TOKEN_SCOPE_OUTSIDE_THE_ALLOW_LIST}).
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
            if (strings(s, "SELECT HAS_PERMS_BY_NAME(N'dbo', N'USER', N'IMPERSONATE')").contains("1")) {
                found.add("EFFECTIVE IMPERSONATE");
            }
            found.addAll(strings(s, TOKEN_SCOPE_OUTSIDE_THE_ALLOW_LIST));
            found.addAll(strings(s, TOKEN_ROLES_OUTSIDE_THE_ALLOW_LIST));
            found.addAll(strings(s, OWNED_BY_THE_TOKEN));
            found.addAll(strings(s, "SELECT CONCAT('DIRECT ', p.class_desc, ' ', p.state, ' ', p.permission_name) FROM sys.database_permissions p "
                    + "JOIN sys.database_principals u ON u.principal_id = p.grantee_principal_id WHERE u.sid = SUSER_SID() AND NOT ("
                    + "(p.class = 0 AND p.state = 'G' AND p.permission_name = 'CONNECT') OR "
                    + "(p.class = 1 AND ((p.state = 'G' AND p.permission_name IN ('SELECT', 'EXECUTE')) OR p.state = 'D')))"));
            return found;
        } finally {
            c.setCatalog(home);
        }
    }

    /** The principals of the login's token inside msdb, asked as the login itself; the login itself shows as its user name or, through guest, as guest. */
    static List<String> msdbToken(String jdbcUrl, String login, String password) throws SQLException {
        try (Connection c = DriverManager.getConnection(jdbcUrl + ";loginTimeout=30", login, password); Statement s = c.createStatement()) {
            c.setCatalog("msdb");
            return strings(s, "SELECT name FROM sys.user_token ORDER BY name");
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
            Set<String> direct, Set<String> reached) {

        /** reached: the further lines of {@link #ownMsdbPrivileges} (review H-1': "EFFECTIVE IMPERSONATE", "TOKEN ...") that the six and the own rows do not show. */
        MsdbCounterexample(String name, boolean global, Function<String, List<String>> apply, Function<String, List<String>> undo, Set<String> effective,
                Set<String> direct) {
            this(name, global, apply, undo, effective, direct, Set.of());
        }

        List<String> expected() {
            List<String> all = new ArrayList<>();
            effective.forEach(p -> all.add("EFFECTIVE " + p));
            direct.forEach(d -> all.add("DIRECT " + d));
            all.addAll(reached);
            return all;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /** An msdb object the SQL Agent roles do not grant: used where a counterexample or the one-ops sample needs a direct object-level grant. */
    static final String MSDB_PROCEDURE = "dbo.sp_help_category";

    static final String IMPERSONATE = "EFFECTIVE IMPERSONATE";

    /**
     * A login that reaches msdb through guest alone cannot read msdb.dbo.sysjobs_view (only the SQL Agent roles may, error 229), so the Agent-job count of (f)
     * is undetermined for it whatever else holds. Cases that want the verdict to rest on the privileges alone let guest read the view; a plain object-level
     * GRANT SELECT is inside the allow-list.
     */
    static final String GUEST_READS_AGENT_JOBS = "GRANT SELECT ON OBJECT::dbo.sysjobs_view TO guest";
    static final String GUEST_READS_AGENT_JOBS_UNDO = "REVOKE SELECT ON OBJECT::dbo.sysjobs_view FROM guest";

    /**
     * Review H-1' (15) GRANT ALTER ANY SCHEMA TO guest is not in this list: guest is in the token only of a login without a user of its own in msdb,
     * so beside the own user of the baseline it reaches nobody. It is exercised with {@link #dropMsdbOwnUser}, in both ITs.
     */
    static final List<MsdbCounterexample> MSDB_COUNTEREXAMPLES = List.of(
            new MsdbCounterexample("(1) GRANT CONTROL ON DATABASE::msdb", false, u -> List.of("GRANT CONTROL ON DATABASE::msdb TO " + u),
                    u -> List.of("REVOKE CONTROL ON DATABASE::msdb FROM " + u), Set.copyOf(MSDB_PRIVILEGES), Set.of("DATABASE G CONTROL"), Set.of(IMPERSONATE)),
            new MsdbCounterexample("(2) GRANT ALTER ANY ROLE", false, u -> List.of("GRANT ALTER ANY ROLE TO " + u), u -> List.of("REVOKE ALTER ANY ROLE FROM " + u),
                    Set.of("ALTER ANY ROLE"), Set.of("DATABASE G ALTER ANY ROLE")),
            new MsdbCounterexample("(3) GRANT IMPERSONATE ON USER::dbo", false, u -> List.of("GRANT IMPERSONATE ON USER::dbo TO " + u),
                    u -> List.of("REVOKE IMPERSONATE ON USER::dbo FROM " + u), Set.of(), Set.of("DATABASE_PRINCIPAL G IMPERSONATE"), Set.of(IMPERSONATE)),
            new MsdbCounterexample("(4) GRANT ALTER ON SCHEMA::dbo and CREATE PROCEDURE", false,
                    u -> List.of("GRANT ALTER ON SCHEMA::dbo TO " + u, "GRANT CREATE PROCEDURE TO " + u),
                    u -> List.of("REVOKE ALTER ON SCHEMA::dbo FROM " + u, "REVOKE CREATE PROCEDURE FROM " + u), Set.of("CREATE PROCEDURE"),
                    Set.of("SCHEMA G ALTER", "DATABASE G CREATE PROCEDURE")),
            new MsdbCounterexample("(5) role nesting (review H-A case A4): db_owner gets SQLAgentUserRole as a member", true,
                    u -> List.of("ALTER ROLE db_owner ADD MEMBER SQLAgentUserRole"), u -> List.of("ALTER ROLE db_owner DROP MEMBER SQLAgentUserRole"),
                    Set.copyOf(MSDB_PRIVILEGES), Set.of(), Set.of(IMPERSONATE, "TOKEN_ROLE db_owner")),
            new MsdbCounterexample("(6) GRANT CONTROL ON DATABASE::msdb TO SQLAgentUserRole", true,
                    u -> List.of("GRANT CONTROL ON DATABASE::msdb TO SQLAgentUserRole"), u -> List.of("REVOKE CONTROL ON DATABASE::msdb FROM SQLAgentUserRole"),
                    Set.copyOf(MSDB_PRIVILEGES), Set.of(), Set.of(IMPERSONATE, "TOKEN SQLAgentUserRole DATABASE G CONTROL")),
            new MsdbCounterexample("(7) GRANT ALTER ANY SCHEMA TO public in msdb", true, u -> List.of("GRANT ALTER ANY SCHEMA TO public"),
                    u -> List.of("REVOKE ALTER ANY SCHEMA FROM public"), Set.of("ALTER ANY SCHEMA"), Set.of(),
                    Set.of("TOKEN public DATABASE G ALTER ANY SCHEMA")),
            new MsdbCounterexample("(8) GRANT EXECUTE ON " + MSDB_PROCEDURE + " WITH GRANT OPTION", false,
                    u -> List.of("GRANT EXECUTE ON " + MSDB_PROCEDURE + " TO " + u + " WITH GRANT OPTION"),
                    u -> List.of("REVOKE EXECUTE ON " + MSDB_PROCEDURE + " FROM " + u + " CASCADE"), Set.of(), Set.of("OBJECT_OR_COLUMN W EXECUTE")),
            // Review H-1' (opus): indirect paths the six effective permissions do not see; each is granted to a role of the token or to public, never to the own user.
            new MsdbCounterexample("(9) GRANT TAKE OWNERSHIP ON DATABASE::msdb TO SQLAgentUserRole", true,
                    u -> List.of("GRANT TAKE OWNERSHIP ON DATABASE::msdb TO SQLAgentUserRole"), u -> List.of("REVOKE TAKE OWNERSHIP ON DATABASE::msdb FROM SQLAgentUserRole"),
                    Set.of(), Set.of(), Set.of("TOKEN SQLAgentUserRole DATABASE G TAKE OWNERSHIP")),
            new MsdbCounterexample("(10) GRANT ALTER ANY DATABASE DDL TRIGGER TO public in msdb", true,
                    u -> List.of("GRANT ALTER ANY DATABASE DDL TRIGGER TO public"), u -> List.of("REVOKE ALTER ANY DATABASE DDL TRIGGER FROM public"),
                    Set.of(), Set.of(), Set.of("TOKEN public DATABASE G ALTER ANY DATABASE DDL TRIGGER")),
            new MsdbCounterexample("(11) GRANT CREATE FUNCTION TO SQLAgentUserRole", true,
                    u -> List.of("GRANT CREATE FUNCTION TO SQLAgentUserRole"), u -> List.of("REVOKE CREATE FUNCTION FROM SQLAgentUserRole"),
                    Set.of(), Set.of(), Set.of("TOKEN SQLAgentUserRole DATABASE G CREATE FUNCTION")),
            new MsdbCounterexample("(12) GRANT IMPERSONATE ON USER::dbo TO SQLAgentUserRole (user level, the six stay 0)", true,
                    u -> List.of("GRANT IMPERSONATE ON USER::dbo TO SQLAgentUserRole"), u -> List.of("REVOKE IMPERSONATE ON USER::dbo FROM SQLAgentUserRole"),
                    Set.of(), Set.of(), Set.of(IMPERSONATE, "TOKEN SQLAgentUserRole DATABASE_PRINCIPAL G IMPERSONATE")),
            new MsdbCounterexample("(13) GRANT ALTER ON SCHEMA::dbo TO SQLAgentUserRole", true,
                    u -> List.of("GRANT ALTER ON SCHEMA::dbo TO SQLAgentUserRole"), u -> List.of("REVOKE ALTER ON SCHEMA::dbo FROM SQLAgentUserRole"),
                    Set.of(), Set.of(), Set.of("TOKEN SQLAgentUserRole SCHEMA G ALTER")),
            new MsdbCounterexample("(14) GRANT ALTER ON the dbo procedure " + MSDB_PROCEDURE + " TO public", true,
                    u -> List.of("GRANT ALTER ON OBJECT::" + MSDB_PROCEDURE + " TO public"), u -> List.of("REVOKE ALTER ON OBJECT::" + MSDB_PROCEDURE + " FROM public"),
                    Set.of(), Set.of(), Set.of("TOKEN public OBJECT_OR_COLUMN G ALTER")),
            // Review H-A (opus): the capability of these roles is object-level EXECUTE / SELECT, so neither the token scan nor the six permissions see it; the membership is the violation.
            // Container finding: db_ssisadmin of SQL Server 2022 also holds object-level INSERT / UPDATE / DELETE / REFERENCES, which the token scan already catches (A1 does not pin the role rule alone).
            new MsdbCounterexample("(16) role nesting (review H-A case A1): db_ssisadmin gets SQLAgentUserRole as a member", true,
                    u -> List.of("ALTER ROLE db_ssisadmin ADD MEMBER SQLAgentUserRole"), u -> List.of("ALTER ROLE db_ssisadmin DROP MEMBER SQLAgentUserRole"),
                    Set.of(), Set.of(), Set.of("TOKEN_ROLE db_ssisadmin", "TOKEN db_ssisadmin OBJECT_OR_COLUMN G DELETE", "TOKEN db_ssisadmin OBJECT_OR_COLUMN G INSERT",
                            "TOKEN db_ssisadmin OBJECT_OR_COLUMN G REFERENCES", "TOKEN db_ssisadmin OBJECT_OR_COLUMN G UPDATE")),
            new MsdbCounterexample("(17) role nesting (review H-A case A3): a role of our own, without any grant, gets SQLAgentUserRole as a member", true,
                    u -> List.of("CREATE ROLE " + NESTED_ROLE, "ALTER ROLE " + NESTED_ROLE + " ADD MEMBER SQLAgentUserRole"),
                    u -> List.of("ALTER ROLE " + NESTED_ROLE + " DROP MEMBER SQLAgentUserRole", "DROP ROLE " + NESTED_ROLE), Set.of(), Set.of(),
                    Set.of("TOKEN_ROLE " + NESTED_ROLE)),
            // Review M-A: ownership inside msdb; the names carry the login so that parallel forks never meet.
            new MsdbCounterexample("(18) the account owns a role of msdb (review M-A case M1)", false,
                    u -> List.of("CREATE ROLE " + owned(u, "role") + " AUTHORIZATION " + u), u -> List.of("DROP ROLE " + owned(u, "role")), Set.of(), Set.of(),
                    Set.of("OWNS ROLE")),
            new MsdbCounterexample("(19) the account owns a schema of msdb (review M-A case M2)", false,
                    u -> List.of("CREATE SCHEMA " + owned(u, "schema") + " AUTHORIZATION " + u), u -> List.of("DROP SCHEMA " + owned(u, "schema")), Set.of(), Set.of(),
                    Set.of("OWNS SCHEMA")),
            // Review M-1: the securables the first count missed; the certificate and the type belong to the account, the procedure to the schema of SQLAgentUserRole.
            new MsdbCounterexample("(20) the account owns a certificate of msdb (review M-1)", false,
                    u -> List.of("CREATE CERTIFICATE " + owned(u, "cert") + " AUTHORIZATION " + u + " ENCRYPTION BY PASSWORD = '" + throwawayPassword() + "' WITH SUBJECT = 'forcitis fixture'"),
                    u -> List.of("DROP CERTIFICATE " + owned(u, "cert")), Set.of(), Set.of(), Set.of("OWNS CERTIFICATE")),
            new MsdbCounterexample("(21) the account owns a user-defined type of msdb (review M-1)", false,
                    u -> List.of("CREATE TYPE dbo." + owned(u, "type") + " FROM int", "ALTER AUTHORIZATION ON TYPE::dbo." + owned(u, "type") + " TO " + u),
                    u -> List.of("DROP TYPE dbo." + owned(u, "type")), Set.of(), Set.of(), Set.of("OWNS TYPE")),
            new MsdbCounterexample("(22) an object in the allowed schema SQLAgentUserRole, owned through the schema (review M-1)", true,
                    u -> List.of("CREATE PROCEDURE SQLAgentUserRole." + SCHEMA_OWNED_PROCEDURE + " AS SELECT 1"),
                    u -> List.of("DROP PROCEDURE IF EXISTS SQLAgentUserRole." + SCHEMA_OWNED_PROCEDURE), Set.of(), Set.of(), Set.of("OWNS SCHEMA_OBJECT")));

    /** A password for a certificate that is dropped at once, made at run time so no secret literal sits in the source. */
    private static String throwawayPassword() {
        return "Fx!" + java.util.UUID.randomUUID().toString().replace("-", "");
    }

    /** "[login_kind]" from "[login]": a name of our own beside the login, unique per account. */
    private static String owned(String quotedUser, String kind) {
        return q(quotedUser.substring(1, quotedUser.length() - 1) + "_owned_" + kind);
    }

    /**
     * What one-ops read off the real accounts on RDS (34 platform defaults): beside CONNECT, object-level GRANT SELECT several times, GRANT EXECUTE and one
     * DENY SELECT directly on the own msdb user. The baseline plus this must stay inside the allowed shape; msdb objects chosen here, not RDS's.
     */
    static List<String> oneOpsSampleMsdbGrants(String quotedUser) {
        return List.of("GRANT SELECT ON OBJECT::dbo.backupset TO " + quotedUser, "GRANT SELECT ON OBJECT::dbo.backupfile TO " + quotedUser,
                "GRANT SELECT ON OBJECT::dbo.backupmediaset TO " + quotedUser, "GRANT EXECUTE ON OBJECT::" + MSDB_PROCEDURE + " TO " + quotedUser,
                "DENY SELECT ON OBJECT::dbo.restorehistory TO " + quotedUser);
    }

    /** Asked as the login: its Agent-job count, the way (f) reads it; throws when the login may not read the view. */
    static List<String> ownAgentJobs(String jdbcUrl, String login, String password) throws SQLException {
        try (Connection c = DriverManager.getConnection(jdbcUrl + ";loginTimeout=30", login, password); Statement s = c.createStatement()) {
            return strings(s, "SELECT COUNT(*) FROM msdb.dbo.sysjobs_view WHERE owner_sid = SUSER_SID()");
        }
    }

    /** The login reaches msdb through guest alone, as the enabled guest lets it: its own user (and with it the three role memberships) goes. */
    static void dropMsdbOwnUser(Administrator sa, String quotedUser) throws SQLException {
        sa.run("msdb", "DROP USER " + quotedUser);
    }

    /**
     * Review H-1' (4), asked as sa in msdb: the default grants the token scan must not take for a violation really exist in the container, so the case that
     * lets the account pass proves something. SQL Server gives public the two VIEW ANY COLUMN * KEY DEFINITION at database level and, as object-level GRANT
     * SELECT and GRANT EXECUTE, a good many system objects; the SQL Agent roles hold object-level GRANT SELECT and GRANT EXECUTE of their own.
     */
    static void assertMsdbDefaultGrantsExist(Statement sa) throws SQLException {
        assertThat(strings(sa, "SELECT p.permission_name FROM sys.database_permissions p JOIN sys.database_principals pr ON pr.principal_id = p.grantee_principal_id "
                + "WHERE pr.name = 'public' AND p.class = 0 AND p.state = 'G'")).as("database-level grants of public in msdb")
                .containsExactlyInAnyOrder("VIEW ANY COLUMN ENCRYPTION KEY DEFINITION", "VIEW ANY COLUMN MASTER KEY DEFINITION");
        for (String role : List.of("SQLAgentUserRole", "SQLAgentOperatorRole")) {
            for (String permission : List.of("SELECT", "EXECUTE")) {
                assertThat(strings(sa, "SELECT COUNT(*) FROM sys.database_permissions p JOIN sys.database_principals pr ON pr.principal_id = p.grantee_principal_id "
                        + "WHERE pr.name = '" + role + "' AND p.class = 1 AND p.state = 'G' AND p.permission_name = '" + permission + "'"))
                        .as("object-level GRANT %s of %s in msdb", permission, role).isNotEqualTo(List.of("0"));
            }
        }
        assertThat(strings(sa, "SELECT COUNT(*) FROM sys.database_permissions p JOIN sys.database_principals pr ON pr.principal_id = p.grantee_principal_id "
                + "WHERE pr.name = 'public' AND p.class = 1 AND p.state = 'G' AND p.permission_name IN ('SELECT', 'EXECUTE')")).as("object-level grants of public in msdb")
                .isNotEqualTo(List.of("0"));
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
