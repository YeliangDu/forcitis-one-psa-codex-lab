package com.forcitis.one.platform.integration.infrastructure.connection;

import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.DatabaseLevelPermission;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.Ownership;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessResourceFailureException;

/** A catalog that answers exactly what the RDS baseline login sees; each test bends one answer. */
final class FakeCatalog implements ReadOnlyCatalog {

    static final String LEAKED = "10.0.0.1 marker-value-7731";

    final AtomicInteger queries = new AtomicInteger();
    List<String> databasePermissions = new ArrayList<>(ReadOnlyBaseline.DATABASE_PERMISSIONS);
    List<String> serverPermissions = new ArrayList<>(ReadOnlyBaseline.SERVER_PERMISSIONS);
    List<DatabaseLevelPermission> databaseLevelPermissions = baselineDatabaseLevel();
    List<String> roles = new ArrayList<>(List.of("public"));
    Map<String, Integer> memberships = noMemberships();
    Ownership ownership = new Ownership(false, 0, 0, 0, 0);
    List<String> reachableDatabases = new ArrayList<>(List.of("legacyworld", "master", "msdb", "rdscore", "tempdb"));
    String currentDatabase = "legacyworld";
    volatile boolean unreachable;

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
    public List<DatabaseLevelPermission> databaseLevelPermissions() {
        return answer(() -> databaseLevelPermissions);
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
    public List<String> reachableDatabases() {
        return answer(() -> reachableDatabases);
    }

    @Override
    public String currentDatabase() {
        return answer(() -> currentDatabase);
    }
}
