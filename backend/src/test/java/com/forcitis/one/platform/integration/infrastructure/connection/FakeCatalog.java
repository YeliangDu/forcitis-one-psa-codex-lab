package com.forcitis.one.platform.integration.infrastructure.connection;

import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.DatabaseLevelPermission;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.Ownership;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.ReachableDatabase;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.SecurablePermissionRow;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.ServerPermissionRow;
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
            new SecurablePermissionRow("OBJECT_OR_COLUMN", 917578307, "G", "SELECT", false)));
    List<String> roles = new ArrayList<>(List.of("public"));
    Map<String, Integer> memberships = noMemberships();
    Ownership ownership = new Ownership(false, 0, 0, 0, 0);
    List<String> reachableDatabases = new ArrayList<>(List.of("legacyworld", "master", "msdb", "rdscore", "tempdb"));
    /** When set, answers reachableDatabases() instead of the names above. */
    List<ReachableDatabase> reachableDatabaseRows;
    final Map<String, Long> ownUsers = new HashMap<>();
    String currentDatabase = "legacyworld";
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

    static Map<String, Integer> noMemberships() {
        Map<String, Integer> memberships = new TreeMap<>();
        ReadOnlyBaseline.FIXED_SERVER_ROLES.forEach(r -> memberships.put(r, 0));
        return memberships;
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
    public long ownUsers(String systemDatabase) {
        return answer(() -> ownUsers.getOrDefault(systemDatabase, 0L));
    }

    @Override
    public String currentDatabase() {
        return answer(() -> currentDatabase);
    }
}
