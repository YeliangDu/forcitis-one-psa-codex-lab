package com.forcitis.one.platform.integration.infrastructure.connection;

import com.forcitis.one.platform.integration.infrastructure.connection.EffectiveGrants.DatabaseLocator;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.ReachableDatabase;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.Reason;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/**
 * The login's own users in the system databases under item (f) of {@link ReadOnlyBaseline}, as taken into the baseline (user 2026-10-15
 * 「纳入基线」, narrowed by one-coordinator, checked on RDS by one-ops): RDS gives every login a user in msdb, in a subset of the three SQL Agent
 * roles, and one in rdscore without a role and with CONNECT only. Any other own user, role or rdscore grant is a violation, and so is an Agent
 * job the login owns. The msdb user's direct grants are not judged: one-ops found 34 on the real logins (CONNECT, object-level SELECT, a DENY
 * and an EXECUTE), and what it inherits comes from the roles, which are bounded.
 */
final class SystemDatabaseUsers {

    static final String MSDB = "msdb";
    static final int MSDB_ID = 4;
    static final Set<String> MSDB_AGENT_ROLES = Set.of("SQLAgentOperatorRole", "SQLAgentReaderRole", "SQLAgentUserRole");
    static final String RDS_DATABASE_PERMISSION = "DATABASE G CONNECT";

    /** One row per role of the login's own user; a user without a role gives one row with inRole false, a role the login cannot name a null role. */
    record Membership(boolean inRole, @Nullable String role) {

        @Override
        public String toString() {
            return "Membership[inRole=" + inRole + "]";
        }
    }

    /** A row of sys.database_permissions granted to or denied the login's own user. */
    record OwnUserPermission(String classDescription, String state, String permission) {
    }

    /** What the read-only verification command lists under (f); role is null for an own user without a role. */
    record OwnUserRole(String database, @Nullable String role) {

        @Override
        public String toString() {
            return "OwnUserRole";
        }
    }

    private SystemDatabaseUsers() {
    }

    static void check(Findings found, ReadOnlyCatalog catalog, List<ReachableDatabase> systemDatabases) {
        Set<String> beyond = new TreeSet<>();
        for (ReachableDatabase database : systemDatabases) {
            found.ask(() -> catalog.ownUserMemberships(database.name())).filter(rows -> !rows.isEmpty()).ifPresent(rows -> {
                listed(found, database.name(), rows);
                if (!allowed(found, catalog, database, rows)) {
                    beyond.add(database.name());
                }
            });
        }
        if (!beyond.isEmpty()) {
            found.violated(Reason.SYSTEM_DATABASE_OWN_USER, beyond.size(), "the login has a user of its own in the system databases " + beyond
                    + " beyond the msdb and rdscore users the platform gives every login, only guest is allowed there otherwise",
                    beyond.stream().map(DatabaseLocator::new).toList());
        }
        found.ask(catalog::ownedAgentJobs).filter(jobs -> jobs > 0).ifPresent(jobs -> found.violated(Reason.OWNS_AGENT_JOB,
                (int) Math.min(Integer.MAX_VALUE, jobs), "the login owns " + jobs + " SQL Agent job(s)"));
    }

    private static void listed(Findings found, String database, List<Membership> rows) {
        for (Membership row : rows) {
            if (!row.inRole()) {
                found.ownUsers.add(new OwnUserRole(database, null));
            } else if (row.role() != null) {
                found.ownUsers.add(new OwnUserRole(database, row.role()));
            }
        }
    }

    private static boolean allowed(Findings found, ReadOnlyCatalog catalog, ReachableDatabase database, List<Membership> rows) {
        boolean msdb = database.id() == MSDB_ID && database.name().equals(MSDB);
        boolean rdscore = database.id() > MSDB_ID && database.name().equals(ReadOnlyBaseline.RDS_DATABASE);
        if (!msdb && !rdscore) {
            return false;
        }
        List<Membership> roles = rows.stream().filter(Membership::inRole).toList();
        if (rdscore) {
            return roles.isEmpty() && found.ask(() -> catalog.ownUserPermissions(database.name()))
                    .map(permissions -> permissions.stream().allMatch(p -> RDS_DATABASE_PERMISSION.equals(
                            p.classDescription().trim() + " " + p.state().trim() + " " + ReadOnlyBaseline.upper(p.permission()))))
                    .orElse(true);
        }
        if (roles.stream().anyMatch(row -> row.role() != null && !MSDB_AGENT_ROLES.contains(row.role()))) {
            return false;
        }
        long unnamed = roles.stream().filter(row -> row.role() == null).count();
        if (unnamed > 0) {
            found.unknown(Reason.NO_ANSWER, (int) unnamed, "a role of the login's own user in " + database.name() + " cannot be named, cannot be determined");
        }
        return true;
    }
}
