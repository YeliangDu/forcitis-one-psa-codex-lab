package com.forcitis.one.platform.integration.infrastructure.connection;

import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.DatabaseLevelPermission;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.Ownership;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.ReachableDatabase;
import com.forcitis.one.platform.integration.infrastructure.connection.EffectiveGrants.SecurablePermissionRow;
import com.forcitis.one.platform.integration.infrastructure.connection.MsdbPrivileges.TokenPermission;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.ServerPermissionRow;
import com.forcitis.one.platform.integration.infrastructure.connection.SystemDatabaseUsers.Membership;
import com.forcitis.one.platform.integration.infrastructure.connection.SystemDatabaseUsers.OwnUserPermission;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessResourceFailureException;

/** A catalog that answers exactly what the RDS baseline login sees; each test bends one answer. */
class FakeCatalog implements ReadOnlyCatalog {

    static final String LEAKED = "10.0.0.1 marker-value-7731";

    /** database_id as SQL Server numbers its system databases; user databases follow. */
    static final Map<String, Integer> DATABASE_IDS = Map.of("master", 1, "tempdb", 2, "model", 3, "msdb", 4, "rdscore", 5, "legacyworld", 6);

    final AtomicInteger queries = new AtomicInteger();
    List<String> databasePermissions = new ArrayList<>(ReadOnlyBaseline.DATABASE_PERMISSIONS);
    List<String> serverPermissions = new ArrayList<>(ReadOnlyBaseline.SERVER_PERMISSIONS);
    List<ServerPermissionRow> serverPermissionRows = new ArrayList<>(List.of(new ServerPermissionRow(100, "SERVER", "G", "CONNECT SQL")));
    List<DatabaseLevelPermission> databaseLevelPermissions = baselineDatabaseLevel();
    List<SecurablePermissionRow> securablePermissionRows = new ArrayList<>(List.of(
            row("OBJECT_OR_COLUMN", 917578307, "G", "SELECT", false)));
    List<String> roles = new ArrayList<>(List.of("public"));
    Map<String, Integer> memberships = noMemberships();
    Ownership ownership = new Ownership(false, 0, 0, 0, 0);
    List<String> reachableDatabases = new ArrayList<>(List.of("legacyworld", "master", "msdb", "rdscore", "tempdb"));
    /** When set, answers reachableDatabases() instead of the names above. */
    List<ReachableDatabase> reachableDatabaseRows;
    /** The login's own users by system database: none unless a test adds one (rdsOwnUsers() gives those RDS creates). */
    final Map<String, List<Membership>> ownUsers = new HashMap<>();
    final Map<String, List<OwnUserPermission>> ownUserPermissions = new HashMap<>();
    /** HAS_PERMS_BY_NAME in msdb as the baseline login answers it: every privilege of the review H-1 and IMPERSONATE on dbo false. */
    final Map<String, Integer> msdbEffectivePermissions = new HashMap<>();
    /** The token's rows in msdb as SQL Server gives them (review H-1′): CONNECT, public's two defaults and object-level grants of public and the roles. */
    List<TokenPermission> msdbTokenPermissions = new ArrayList<>(List.of(new TokenPermission(false, "DATABASE", "G", "CONNECT"),
            new TokenPermission(true, "DATABASE", "G", "VIEW ANY COLUMN MASTER KEY DEFINITION"),
            new TokenPermission(true, "DATABASE", "G", "VIEW ANY COLUMN ENCRYPTION KEY DEFINITION"), new TokenPermission(true, "OBJECT_OR_COLUMN", "G", "SELECT"),
            new TokenPermission(true, "OBJECT_OR_COLUMN", "G", "EXECUTE"), new TokenPermission(false, "OBJECT_OR_COLUMN", "G", "EXECUTE")));
    /** The roles of the token in msdb as the baseline login has them: public and the three SQL Agent roles. */
    List<String> msdbTokenRoles = new ArrayList<>(List.of("public", "SQLAgentOperatorRole", "SQLAgentReaderRole", "SQLAgentUserRole"));
    /** What the token owns in msdb beyond the schemas of the platform. */
    Long msdbOwnedByToken = 0L;
    long ownedAgentJobs;
    String currentDatabase = "legacyworld";

    {
        MsdbPrivileges.ASKED.forEach(p -> msdbEffectivePermissions.put(p, 0));
    }
    volatile boolean unreachable;
    /** When set, every query throws it instead of answering. */
    volatile RuntimeException failure;

    static List<DatabaseLevelPermission> baselineDatabaseLevel() {
        List<DatabaseLevelPermission> rows = new ArrayList<>();
        ReadOnlyBaseline.DATABASE_DENIES.forEach(p -> rows.add(new DatabaseLevelPermission("D", p)));
        rows.add(new DatabaseLevelPermission("G", "CONNECT"));
        rows.add(new DatabaseLevelPermission("G", "SELECT"));
        return rows;
    }

    /** Without names; a schema-level row belongs to its own schema, any other to none the catalog shows. */
    static SecurablePermissionRow row(String classDescription, long majorId, String state, String permission, boolean toPublic) {
        return new SecurablePermissionRow(classDescription, majorId, state, permission, toPublic, classDescription.equals("SCHEMA") ? majorId : null, null,
                null, toPublic ? "public" : null, toPublic ? "DATABASE_ROLE" : "SQL_USER");
    }

    static Map<String, Integer> noMemberships() {
        Map<String, Integer> memberships = new TreeMap<>();
        ReadOnlyBaseline.FIXED_SERVER_ROLES.forEach(r -> memberships.put(r, 0));
        return memberships;
    }

    /** An own user in the given database, in the given roles (none: one row without a role), with CONNECT only. */
    FakeCatalog ownUser(String database, String... roles) {
        List<Membership> rows = new ArrayList<>();
        for (String role : roles) {
            rows.add(new Membership(true, role));
        }
        if (rows.isEmpty()) {
            rows.add(new Membership(false, null));
        }
        ownUsers.put(database, rows);
        ownUserPermissions.put(database, new ArrayList<>(List.of(new OwnUserPermission("DATABASE", "G", "CONNECT"))));
        return this;
    }

    /** What RDS gives every login (one-ops, C-14 rc.3): msdb in the three SQL Agent roles, rdscore without a role. */
    FakeCatalog rdsOwnUsers() {
        return ownUser("msdb", "SQLAgentOperatorRole", "SQLAgentReaderRole", "SQLAgentUserRole").ownUser("rdscore");
    }

    private <T> T answer(Supplier<T> value) {
        queries.incrementAndGet();
        if (unreachable) {
            throw new DataAccessResourceFailureException("cannot reach " + LEAKED);
        }
        if (failure != null) {
            throw failure;
        }
        return value.get();
    }

    @Override
    public List<String> databasePermissions() {
        return answer(() -> databasePermissions);
    }

    @Override
    public List<String> serverPermissions() {
        return answer(() -> serverPermissions);
    }

    @Override
    public List<ServerPermissionRow> serverPermissionRows() {
        return answer(() -> serverPermissionRows);
    }

    @Override
    public List<DatabaseLevelPermission> databaseLevelPermissions() {
        return answer(() -> databaseLevelPermissions);
    }

    @Override
    public List<SecurablePermissionRow> securablePermissionRows() {
        return answer(() -> securablePermissionRows);
    }

    @Override
    public List<String> roles() {
        return answer(() -> roles);
    }

    @Override
    public Map<String, Integer> fixedServerRoleMemberships(Set<String> alwaysAsked) {
        return answer(() -> memberships);
    }

    @Override
    public Ownership ownership() {
        return answer(() -> ownership);
    }

    @Override
    public List<ReachableDatabase> reachableDatabases() {
        return answer(() -> reachableDatabaseRows != null ? reachableDatabaseRows
                : reachableDatabases.stream().map(name -> new ReachableDatabase(DATABASE_IDS.getOrDefault(name, 100), name)).toList());
    }

    @Override
    public List<Membership> ownUserMemberships(String systemDatabase) {
        return answer(() -> ownUsers.getOrDefault(systemDatabase, List.of()));
    }

    @Override
    public List<OwnUserPermission> ownUserPermissions(String systemDatabase) {
        return answer(() -> ownUserPermissions.getOrDefault(systemDatabase, List.of()));
    }

    @Override
    public Map<String, Integer> msdbEffectivePermissions() {
        return answer(() -> msdbEffectivePermissions);
    }

    @Override
    public List<TokenPermission> msdbTokenPermissions() {
        return answer(() -> msdbTokenPermissions);
    }

    @Override
    public List<String> msdbTokenRoles() {
        return answer(() -> msdbTokenRoles);
    }

    @Override
    public Long msdbOwnedByToken() {
        return answer(() -> msdbOwnedByToken);
    }

    @Override
    public long ownedAgentJobs() {
        return answer(() -> ownedAgentJobs);
    }

    @Override
    public String currentDatabase() {
        return answer(() -> currentDatabase);
    }
}
