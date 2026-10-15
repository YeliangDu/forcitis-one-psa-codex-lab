package com.forcitis.one.platform.integration.infrastructure.connection;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

/**
 * ADR-0037 item 4 as amended by the implementation card (one-coordinator and one-ops, 2026-10-15): a read-only external login passes only when
 * each of the items (a)–(f) holds; anything more, anything less, or anything the catalog will not tell refuses. The baselines are what
 * fn_my_permissions returned for the four real logins on the RDS instance (SQL Server 2016), identical for all four (one-ops, PR #143,
 * B-5 README "有效权限平台基线").
 */
final class ReadOnlyBaseline {

    /**
     * RDS grants every login the ALTER and VIEW SERVER STATE items through the server role public and refuses server-level GRANT and DENY
     * (4613), so they cannot be taken away; the user accepted and registered that risk (ADR-0037 implementation card), with this equality
     * check and 90-day rotation as the mitigation. Any further server permission refuses.
     */
    static final Set<String> SERVER_PERMISSIONS = Set.of("ALTER ANY CONNECTION", "ALTER ANY EVENT SESSION", "ALTER ANY LINKED SERVER",
            "ALTER ANY LOGIN", "ALTER SERVER STATE", "ALTER TRACE", "CONNECT SQL", "VIEW ANY DATABASE", "VIEW SERVER STATE");

    /**
     * KILL DATABASE CONNECTION is derived from the server-level ALTER ANY CONNECTION and SQL Server 2016 refuses a database-level DENY of it
     * (4631). The A8 login holds no database-level VIEW DEFINITION either, so none is allowed although ADR-0037 item 4 (a) named one.
     */
    static final Set<String> DATABASE_PERMISSIONS = Set.of("CONNECT", "KILL DATABASE CONNECTION", "SELECT", "SHOWPLAN",
            "VIEW ANY COLUMN ENCRYPTION KEY DEFINITION", "VIEW ANY COLUMN MASTER KEY DEFINITION", "VIEW DATABASE STATE");

    /** A database-level DENY overrides every schema, object and role GRANT below it, so its presence covers what (a) cannot see. */
    static final Set<String> DATABASE_DENIES = Set.of("INSERT", "UPDATE", "DELETE", "ALTER", "EXECUTE", "REFERENCES", "CREATE TABLE",
            "CREATE VIEW", "CREATE PROCEDURE", "CREATE FUNCTION", "CREATE SCHEMA");

    static final Set<String> DATABASE_GRANTS = Set.of("CONNECT", "SELECT");

    /** RDS opens these to every login through guest; the implementation card allows them besides the login's own database. */
    static final Set<String> SYSTEM_DATABASES = Set.of("master", "msdb", "rdscore", "tempdb");

    /** Asked by name as well, in case the server lists fewer fixed roles than it has. */
    static final Set<String> FIXED_SERVER_ROLES = Set.of("sysadmin", "serveradmin", "securityadmin", "processadmin", "setupadmin", "bulkadmin",
            "diskadmin", "dbcreator");

    /** state as in sys.database_permissions: D deny, G grant, W grant with grant option. */
    record DatabaseLevelPermission(String state, String permission) {
    }

    record Ownership(boolean databaseOwner, long schemas, long objects, long roles, long principalGrants) {
    }

    private ReadOnlyBaseline() {
    }

    /** Each entry starts with the item it breaks, "(a)" to "(f)". */
    static List<String> violations(ReadOnlyCatalog catalog) {
        List<String> violations = new ArrayList<>();
        item(violations, 'a', () -> sameAs("database-level effective permissions", catalog.databasePermissions(), DATABASE_PERMISSIONS));
        item(violations, 'b', () -> databaseLevel(catalog.databaseLevelPermissions()));
        item(violations, 'c', () -> onlyPublic(catalog.roles()));
        item(violations, 'd', () -> server(catalog.serverPermissions(), catalog.fixedServerRoleMemberships(FIXED_SERVER_ROLES)));
        item(violations, 'e', () -> ownsNothing(catalog.ownership()));
        item(violations, 'f', () -> reachable(catalog.reachableDatabases(), catalog.currentDatabase()));
        return violations;
    }

    private static void item(List<String> violations, char item, Supplier<Optional<String>> rule) {
        try {
            rule.get().ifPresent(problem -> violations.add("(" + item + ") " + problem));
        } catch (RuntimeException undeterminable) {
            violations.add("(" + item + ") cannot be determined (" + sqlFailure(undeterminable) + ")");
        }
    }

    private static Optional<String> sameAs(String what, List<String> actual, Set<String> baseline) {
        Set<String> have = normalised(actual);
        if (have.isEmpty()) {
            return Optional.of(what + " came back empty");
        }
        Set<String> more = new TreeSet<>(have);
        more.removeAll(baseline);
        Set<String> fewer = new TreeSet<>(baseline);
        fewer.removeAll(have);
        if (more.isEmpty() && fewer.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(what + " differ from the platform baseline: more " + more + ", fewer " + fewer);
    }

    private static Optional<String> databaseLevel(List<DatabaseLevelPermission> rows) {
        Set<String> denies = new TreeSet<>();
        Set<String> grants = new TreeSet<>();
        Set<String> grantable = new TreeSet<>();
        for (DatabaseLevelPermission row : rows) {
            String permission = row.permission().trim().toUpperCase(Locale.ROOT);
            switch (row.state().trim()) {
                case "D" -> denies.add(permission);
                case "G" -> grants.add(permission);
                default -> grantable.add(permission);
            }
        }
        List<String> problems = new ArrayList<>();
        Set<String> missingDenies = new TreeSet<>(DATABASE_DENIES);
        missingDenies.removeAll(denies);
        if (!missingDenies.isEmpty()) {
            problems.add("database-level DENY lacks " + missingDenies);
        }
        if (!grants.equals(DATABASE_GRANTS)) {
            problems.add("database-level GRANT is " + grants + ", must be exactly " + new TreeSet<>(DATABASE_GRANTS));
        }
        if (!grantable.isEmpty()) {
            problems.add("database-level GRANT WITH GRANT OPTION " + grantable);
        }
        return problems.isEmpty() ? Optional.empty() : Optional.of(String.join(", ", problems));
    }

    private static Optional<String> onlyPublic(List<String> roles) {
        Set<String> have = new TreeSet<>(roles.stream().map(String::trim).toList());
        return have.equals(Set.of("public")) ? Optional.empty() : Optional.of("the login is in the database roles " + have + ", only public is allowed");
    }

    private static Optional<String> server(List<String> permissions, Map<String, Integer> memberships) {
        List<String> problems = new ArrayList<>();
        sameAs("server-level effective permissions", permissions, SERVER_PERMISSIONS).ifPresent(problems::add);
        Set<String> member = new TreeSet<>();
        Set<String> unknown = new TreeSet<>();
        memberships.forEach((role, answer) -> {
            if (answer == null) {
                unknown.add(role);
            } else if (answer != 0) {
                member.add(role);
            }
        });
        if (!member.isEmpty()) {
            problems.add("the login is a member of the fixed server roles " + member);
        }
        if (!unknown.isEmpty()) {
            problems.add("membership of the fixed server roles " + unknown + " cannot be determined");
        }
        return problems.isEmpty() ? Optional.empty() : Optional.of(String.join(", ", problems));
    }

    private static Optional<String> ownsNothing(Ownership ownership) {
        List<String> problems = new ArrayList<>();
        if (ownership.databaseOwner()) {
            problems.add("the login is dbo");
        }
        if (ownership.schemas() > 0) {
            problems.add("owns " + ownership.schemas() + " schema(s)");
        }
        if (ownership.objects() > 0) {
            problems.add("owns " + ownership.objects() + " object(s)");
        }
        if (ownership.roles() > 0) {
            problems.add("owns " + ownership.roles() + " role(s)");
        }
        if (ownership.principalGrants() > 0) {
            problems.add("holds " + ownership.principalGrants() + " grant(s) on database principals such as IMPERSONATE");
        }
        return problems.isEmpty() ? Optional.empty() : Optional.of(String.join(", ", problems));
    }

    private static Optional<String> reachable(List<String> databases, String own) {
        Set<String> beyond = new TreeSet<>();
        boolean ownReachable = false;
        for (String database : databases) {
            if (database.equals(own)) {
                ownReachable = true;
            } else if (SYSTEM_DATABASES.stream().noneMatch(database::equalsIgnoreCase)) {
                beyond.add(database);
            }
        }
        if (!ownReachable) {
            return Optional.of("HAS_DBACCESS does not list the login's own database");
        }
        return beyond.isEmpty() ? Optional.empty() : Optional.of("the login can also enter the databases " + beyond);
    }

    private static Set<String> normalised(List<String> values) {
        Set<String> set = new TreeSet<>();
        values.forEach(v -> set.add(v.trim().toUpperCase(Locale.ROOT)));
        return set;
    }

    /** ADR-0037 item 3.5: SQL Server messages can carry column values, so only the type, the error number and the SQLState are told. */
    static String sqlFailure(Throwable failure) {
        for (Throwable t = failure; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql) {
                return failure.getClass().getSimpleName() + ", error " + sql.getErrorCode() + ", SQLState " + sql.getSQLState();
            }
        }
        return failure.getClass().getSimpleName() + ", no SQL error";
    }
}
