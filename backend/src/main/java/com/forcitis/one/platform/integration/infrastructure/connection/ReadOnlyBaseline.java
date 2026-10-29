package com.forcitis.one.platform.integration.infrastructure.connection;

import com.forcitis.one.platform.integration.infrastructure.connection.EffectiveGrants.DatabaseLocator;
import com.forcitis.one.platform.integration.infrastructure.connection.EffectiveGrants.Locator;
import com.forcitis.one.platform.integration.infrastructure.connection.EffectiveGrants.OverriddenGrant;
import com.forcitis.one.platform.integration.infrastructure.connection.SystemDatabaseUsers.OwnUserRole;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * ADR-0037 item 4 as amended by the implementation card (one-coordinator and one-ops, 2026-10-15) and the review of PR #163 (RULING-ADR37-10):
 * a read-only external login passes only when each of the items (a)–(g) holds. Anything more refuses as a violation; anything the catalog
 * will not tell (a failed or timed-out query, an empty answer where a real login always has rows) is undeterminable, which also refuses the
 * start-up but is told apart at a re-check (ADR-0037 item 4.4 as interpreted by the user, intent 2026-10-15-120940). The baselines are what
 * the four real logins on the RDS instance (SQL Server 2016) returned, identical for all four (one-ops, PR #143, B-5 README
 * "有效权限平台基线"; H-1 export data/evidence/dba-handoff/h1-m3-permissions.sql under docs).
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

    /** These eleven only; CONTROL, TAKE OWNERSHIP and IMPERSONATE on a schema or an object are not among them, which is why (g) exists. */
    static final Set<String> DATABASE_DENIES = Set.of("INSERT", "UPDATE", "DELETE", "ALTER", "EXECUTE", "REFERENCES", "CREATE TABLE",
            "CREATE VIEW", "CREATE PROCEDURE", "CREATE FUNCTION", "CREATE SCHEMA");

    static final Set<String> DATABASE_GRANTS = Set.of("CONNECT", "SELECT");

    /** RDS's own database, open to every login through guest like the system databases; matched exactly, a case-insensitive name is not enough. */
    static final String RDS_DATABASE = "rdscore";

    /** master, tempdb, model and msdb. */
    static final int LAST_SYSTEM_DATABASE_ID = 4;

    /** Asked by name as well, in case the server lists fewer fixed roles than it has. */
    static final Set<String> FIXED_SERVER_ROLES = Set.of("sysadmin", "serveradmin", "securityadmin", "processadmin", "setupadmin", "bulkadmin",
            "diskadmin", "dbcreator");

    /** class, class_desc, state and permission of the only row the real logins hold in sys.server_permissions (H-1 export). */
    static final String LOGIN_SERVER_ROW = "100 SERVER G CONNECT SQL";

    private static final int MAXIMUM_CAUSE_DEPTH = 64;

    /** state as in sys.database_permissions: D deny, G grant, W grant with grant option. */
    record DatabaseLevelPermission(String state, String permission) {
    }

    record Ownership(boolean databaseOwner, long schemas, long objects, long roles, long principalGrants) {
    }

    /** A row of sys.server_permissions granted to or denied the login itself. */
    record ServerPermissionRow(int securableClass, String classDescription, String state, String permission) {
    }

    record ReachableDatabase(int id, String name) {

        boolean system() {
            return id <= LAST_SYSTEM_DATABASE_ID || name.equals(RDS_DATABASE);
        }
    }

    /** What was found, by category only: never a role, database, login or object name (the problem text may carry those). */
    enum Reason {
        EFFECTIVE_PERMISSIONS_DIFFER,
        DATABASE_DENY_MISSING,
        DATABASE_GRANT_NOT_BASELINE,
        GRANT_WITH_GRANT_OPTION,
        DATABASE_ROLE_MEMBERSHIP,
        FIXED_SERVER_ROLE_MEMBERSHIP,
        SERVER_PERMISSION_ROW_NOT_BASELINE,
        DATABASE_OWNER,
        OWNS_SCHEMA,
        OWNS_OBJECT,
        OWNS_ROLE,
        DATABASE_PRINCIPAL_GRANT,
        OTHER_DATABASE_REACHABLE,
        SYSTEM_DATABASE_OWN_USER,
        SYSTEM_DATABASE_OWN_USER_PRIVILEGE,
        OWNS_AGENT_JOB,
        SECURABLE_GRANT_BEYOND_SELECT,
        CATALOG_QUERY_FAILED,
        CHECK_FAILED,
        NO_ANSWER,
        EMPTY_ANSWER,
        FIXED_SERVER_ROLE_UNANSWERED,
        OWN_DATABASE_NOT_LISTED
    }

    /** The error number and SQLState of the innermost SQL exception of a failure, as the driver reported them; never its message. */
    record SqlError(int number, @Nullable String state) {

        static Optional<SqlError> of(Throwable failure) {
            SQLException innermost = null;
            int depth = 0;
            for (Throwable t = failure; t != null && depth < MAXIMUM_CAUSE_DEPTH; t = t.getCause(), depth++) {
                if (t instanceof SQLException sql) {
                    innermost = sql;
                }
            }
            return Optional.ofNullable(innermost).map(sql -> new SqlError(sql.getErrorCode(), sql.getSQLState()));
        }
    }

    /**
     * One finding per call within an item: count is how many entries of the category the call found (permissions, roles, databases, rows);
     * a failed or empty answer counts one, so an item whose queries all fail counts its failed queries.
     */
    record Finding(Reason reason, int count, @Nullable SqlError failure, List<Locator> locators) {

        Finding {
            locators = List.copyOf(locators);
        }

        Finding(Reason reason, int count, @Nullable SqlError failure) {
            this(reason, count, failure, List.of());
        }

        Optional<SqlError> sqlError() {
            return Optional.ofNullable(failure);
        }

        /** Never a name: the locators carry database, schema and object names. */
        @Override
        public String toString() {
            return "Finding[reason=" + reason + ", count=" + count + ", failure=" + failure + ", locators=" + locators + "]";
        }
    }

    /** One unmet item; confirmed when the catalog showed a violation, not only an answer it would not give. */
    record Unmet(char item, String problem, boolean confirmed, List<Finding> findings) {

        Unmet {
            findings = List.copyOf(findings);
        }

        String described() {
            return "(" + item + ") " + problem;
        }

        /** Never the problem text: it may name roles and databases. */
        @Override
        public String toString() {
            return "Unmet[item=" + item + ", confirmed=" + confirmed + ", findings=" + findings + "]";
        }
    }

    record Verdict(List<Unmet> unmet, List<OverriddenGrant> overridden, List<OwnUserRole> ownUsers) {

        Verdict {
            unmet = List.copyOf(unmet);
            overridden = List.copyOf(overridden);
            ownUsers = List.copyOf(ownUsers);
        }

        boolean passed() {
            return unmet.isEmpty();
        }

        boolean violated() {
            return unmet.stream().anyMatch(Unmet::confirmed);
        }

        List<String> described() {
            return unmet.stream().map(Unmet::described).toList();
        }

        @Override
        public String toString() {
            return "Verdict[unmet=" + unmet + ", overridden=" + overridden + ", ownUsers=" + ownUsers.size() + "]";
        }
    }

    private ReadOnlyBaseline() {
    }

    static Verdict check(ReadOnlyCatalog catalog) {
        List<Unmet> unmet = new ArrayList<>();
        List<OverriddenGrant> overridden = new ArrayList<>();
        List<OwnUserRole> ownUsers = new ArrayList<>();
        item(unmet, 'a', found -> found.ask(catalog::databasePermissions)
                .ifPresent(permissions -> sameAs(found, "database-level effective permissions", permissions, DATABASE_PERMISSIONS)));
        item(unmet, 'b', found -> found.ask(catalog::databaseLevelPermissions).ifPresent(rows -> databaseLevel(found, rows)));
        item(unmet, 'c', found -> found.ask(catalog::roles).ifPresent(roles -> onlyPublic(found, roles)));
        item(unmet, 'd', found -> server(found, catalog));
        item(unmet, 'e', found -> found.ask(catalog::ownership).ifPresent(ownership -> ownsNothing(found, ownership)));
        item(unmet, 'f', found -> {
            reachable(found, catalog);
            ownUsers.addAll(found.ownUsers);
        });
        item(unmet, 'g', found -> {
            found.ask(catalog::securablePermissionRows).ifPresent(rows -> EffectiveGrants.belowTheDatabase(found, rows, catalog));
            overridden.addAll(found.overridden);
        });
        return new Verdict(unmet, overridden, ownUsers);
    }

    /** Each entry starts with the item it breaks, "(a)" to "(g)". */
    static List<String> violations(ReadOnlyCatalog catalog) {
        return check(catalog).described();
    }

    private static void item(List<Unmet> unmet, char item, Consumer<Findings> rule) {
        Findings found = new Findings();
        try {
            rule.accept(found);
        } catch (RuntimeException failure) {
            found.failed(Reason.CHECK_FAILED, failure);
        }
        List<String> problems = new ArrayList<>(found.confirmed);
        problems.addAll(found.undeterminable);
        if (!problems.isEmpty()) {
            List<Finding> findings = new ArrayList<>(found.confirmedFindings);
            findings.addAll(found.undeterminableFindings);
            unmet.add(new Unmet(item, String.join(", ", problems), !found.confirmed.isEmpty(), findings));
        }
    }

    private static void sameAs(Findings found, String what, List<String> actual, Set<String> baseline) {
        Set<String> have = normalised(actual);
        if (have.isEmpty()) {
            found.unknown(Reason.EMPTY_ANSWER, 1, what + " came back empty, cannot be determined");
            return;
        }
        Set<String> more = new TreeSet<>(have);
        more.removeAll(baseline);
        Set<String> fewer = new TreeSet<>(baseline);
        fewer.removeAll(have);
        if (!more.isEmpty() || !fewer.isEmpty()) {
            found.violated(Reason.EFFECTIVE_PERMISSIONS_DIFFER, more.size() + fewer.size(), what + " differ from the platform baseline: more " + more + ", fewer " + fewer);
        }
    }

    private static void databaseLevel(Findings found, List<DatabaseLevelPermission> rows) {
        if (rows.isEmpty()) {
            found.unknown(Reason.EMPTY_ANSWER, 1, "database-level permissions came back empty, cannot be determined");
            return;
        }
        Set<String> denies = new TreeSet<>();
        Set<String> grants = new TreeSet<>();
        Set<String> grantable = new TreeSet<>();
        for (DatabaseLevelPermission row : rows) {
            String permission = upper(row.permission());
            switch (row.state().trim()) {
                case "D" -> denies.add(permission);
                case "G" -> grants.add(permission);
                default -> grantable.add(permission);
            }
        }
        Set<String> missingDenies = new TreeSet<>(DATABASE_DENIES);
        missingDenies.removeAll(denies);
        if (!missingDenies.isEmpty()) {
            found.violated(Reason.DATABASE_DENY_MISSING, missingDenies.size(), "database-level DENY lacks " + missingDenies);
        }
        if (!grants.equals(DATABASE_GRANTS)) {
            Set<String> differing = new TreeSet<>(grants);
            differing.addAll(DATABASE_GRANTS);
            differing.removeIf(permission -> grants.contains(permission) && DATABASE_GRANTS.contains(permission));
            found.violated(Reason.DATABASE_GRANT_NOT_BASELINE, differing.size(), "database-level GRANT is " + grants + ", must be exactly " + new TreeSet<>(DATABASE_GRANTS));
        }
        if (!grantable.isEmpty()) {
            found.violated(Reason.GRANT_WITH_GRANT_OPTION, grantable.size(), "database-level GRANT WITH GRANT OPTION " + grantable);
        }
    }

    private static void onlyPublic(Findings found, List<String> roles) {
        Set<String> have = new TreeSet<>(roles.stream().map(String::trim).toList());
        if (have.isEmpty()) {
            found.unknown(Reason.EMPTY_ANSWER, 1, "sys.user_token came back without public, cannot be determined");
        } else if (!have.equals(Set.of("public"))) {
            Set<String> beyondPublic = new TreeSet<>(have);
            beyondPublic.remove("public");
            found.violated(Reason.DATABASE_ROLE_MEMBERSHIP, beyondPublic.size(), "the login is in the database roles " + have + ", only public is allowed");
        }
    }

    /**
     * fn_my_permissions(NULL, 'SERVER') does not list permissions on a login, an endpoint or an availability group (IMPERSONATE ON
     * LOGIN::sa), so the login's own rows are read as well and must be exactly the one row the real logins hold (RULING-ADR37-11).
     */
    private static void server(Findings found, ReadOnlyCatalog catalog) {
        found.ask(catalog::serverPermissions).ifPresent(permissions -> sameAs(found, "server-level effective permissions", permissions, SERVER_PERMISSIONS));
        found.ask(() -> catalog.fixedServerRoleMemberships(FIXED_SERVER_ROLES)).ifPresent(memberships -> fixedServerRoles(found, memberships));
        found.ask(catalog::serverPermissionRows).ifPresent(rows -> {
            if (rows.isEmpty()) {
                found.unknown(Reason.EMPTY_ANSWER, 1, "sys.server_permissions came back empty, cannot be determined");
                return;
            }
            Set<String> have = new TreeSet<>();
            rows.forEach(row -> have.add(row.securableClass() + " " + row.classDescription().trim() + " " + row.state().trim() + " "
                    + upper(row.permission())));
            Set<String> more = new TreeSet<>(have);
            more.remove(LOGIN_SERVER_ROW);
            if (!more.isEmpty() || !have.contains(LOGIN_SERVER_ROW)) {
                found.violated(Reason.SERVER_PERMISSION_ROW_NOT_BASELINE, more.size() + (have.contains(LOGIN_SERVER_ROW) ? 0 : 1),
                        "the login's rows in sys.server_permissions must be exactly [" + LOGIN_SERVER_ROW + "]: more " + more
                        + (have.contains(LOGIN_SERVER_ROW) ? "" : ", lacks " + LOGIN_SERVER_ROW));
            }
        });
    }

    private static void fixedServerRoles(Findings found, Map<String, Integer> memberships) {
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
            found.violated(Reason.FIXED_SERVER_ROLE_MEMBERSHIP, member.size(), "the login is a member of the fixed server roles " + member);
        }
        if (!unknown.isEmpty()) {
            found.unknown(Reason.FIXED_SERVER_ROLE_UNANSWERED, unknown.size(), "membership of the fixed server roles " + unknown + " cannot be determined");
        }
    }

    private static void ownsNothing(Findings found, Ownership ownership) {
        if (ownership.databaseOwner()) {
            found.violated(Reason.DATABASE_OWNER, 1, "the login is dbo");
        }
        if (ownership.schemas() > 0) {
            found.violated(Reason.OWNS_SCHEMA, count(ownership.schemas()), "owns " + ownership.schemas() + " schema(s)");
        }
        if (ownership.objects() > 0) {
            found.violated(Reason.OWNS_OBJECT, count(ownership.objects()), "owns " + ownership.objects() + " object(s)");
        }
        if (ownership.roles() > 0) {
            found.violated(Reason.OWNS_ROLE, count(ownership.roles()), "owns " + ownership.roles() + " role(s)");
        }
        if (ownership.principalGrants() > 0) {
            found.violated(Reason.DATABASE_PRINCIPAL_GRANT, count(ownership.principalGrants()), "holds " + ownership.principalGrants() + " grant(s) on database principals such as IMPERSONATE");
        }
    }

    /** RDS opens the system databases to every login through guest; one where the login has a user of its own is entered with that user's rights. */
    private static void reachable(Findings found, ReadOnlyCatalog catalog) {
        Optional<String> own = found.ask(catalog::currentDatabase);
        Optional<List<ReachableDatabase>> reachable = found.ask(catalog::reachableDatabases);
        if (own.isEmpty() || reachable.isEmpty()) {
            return;
        }
        Set<String> beyond = new TreeSet<>();
        List<ReachableDatabase> system = new ArrayList<>();
        boolean ownReachable = false;
        for (ReachableDatabase database : reachable.get()) {
            if (database.name().equals(own.get())) {
                ownReachable = true;
            } else if (!database.system()) {
                beyond.add(database.name());
            } else {
                system.add(database);
            }
        }
        if (!ownReachable) {
            found.unknown(Reason.OWN_DATABASE_NOT_LISTED, 1, "HAS_DBACCESS does not list the login's own database, cannot be determined");
        }
        if (!beyond.isEmpty()) {
            found.violated(Reason.OTHER_DATABASE_REACHABLE, beyond.size(), "the login can also enter the databases " + beyond,
                    beyond.stream().map(DatabaseLocator::new).toList());
        }
        SystemDatabaseUsers.check(found, catalog, system);
    }

    private static int count(long value) {
        return (int) Math.min(Integer.MAX_VALUE, value);
    }

    static String upper(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static Set<String> normalised(List<String> values) {
        Set<String> set = new TreeSet<>();
        values.forEach(v -> set.add(upper(v)));
        return set;
    }
}
