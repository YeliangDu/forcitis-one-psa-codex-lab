package com.forcitis.one.platform.integration.infrastructure.connection;

import com.forcitis.one.platform.integration.infrastructure.connection.EffectiveGrants.DatabaseLocator;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.ReachableDatabase;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.Reason;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/**
 * The login's own users in the system databases under item (f) of {@link ReadOnlyBaseline}, as taken into the baseline (user 2026-10-15
 * 「纳入基线」, narrowed by one-coordinator, checked on RDS by one-ops): RDS gives every login a user in msdb, in a subset of the three SQL Agent
 * roles, and one in rdscore without a role and with CONNECT only. Any other own user, role or rdscore grant is a violation, and so is an Agent
 * job the login owns. What the login holds in msdb, through its own user, roles, public or guest, is judged by {@link MsdbPrivileges} whenever msdb
 * is reachable. The Agent jobs are asked whenever (f) gets that far; a login that reaches msdb through guest alone may not read sysjobs_view (229),
 * so (f) cannot be determined for it (one-coordinator ruling B, CLAUDE.md rule 6: no silent skip).
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

    private enum Shape { ALLOWED, BEYOND, UNDETERMINED }

    private SystemDatabaseUsers() {
    }

    static void check(Findings found, ReadOnlyCatalog catalog, List<ReachableDatabase> systemDatabases) {
        Set<String> beyond = new TreeSet<>();
        for (ReachableDatabase database : systemDatabases) {
            Optional<List<Membership>> rows = found.ask(() -> catalog.ownUserMemberships(database.name()));
            boolean ownUser = rows.isPresent() && !rows.get().isEmpty();
            if (ownUser) {
                listed(found, database.name(), rows.get());
                if (shape(found, catalog, database, rows.get()) == Shape.BEYOND) {
                    beyond.add(database.name());
                    continue;
                }
            }
            if (msdb(database)) {
                MsdbPrivileges.check(found, catalog, ownUser);
            }
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

    private static boolean msdb(ReachableDatabase database) {
        return database.id() == MSDB_ID && database.name().equals(MSDB);
    }

    private static Shape shape(Findings found, ReadOnlyCatalog catalog, ReachableDatabase database, List<Membership> rows) {
        boolean rdscore = database.id() > MSDB_ID && database.name().equals(ReadOnlyBaseline.RDS_DATABASE);
        if (!msdb(database) && !rdscore) {
            return Shape.BEYOND;
        }
        List<Membership> roles = rows.stream().filter(Membership::inRole).toList();
        if (rdscore) {
            if (!roles.isEmpty()) {
                return Shape.BEYOND;
            }
            Optional<List<OwnUserPermission>> permissions = found.ask(() -> catalog.ownUserPermissions(database.name()));
            if (permissions.isEmpty()) {
                return Shape.UNDETERMINED;
            }
            if (permissions.get().isEmpty()) {
                found.unknown(Reason.EMPTY_ANSWER, 1, "the permissions of the login's own user in " + database.name() + " came back empty, cannot be determined");
                return Shape.UNDETERMINED;
            }
            return permissions.get().stream().allMatch(p -> RDS_DATABASE_PERMISSION.equals(
                    p.classDescription().trim() + " " + p.state().trim() + " " + ReadOnlyBaseline.upper(p.permission()))) ? Shape.ALLOWED : Shape.BEYOND;
        }
        if (roles.stream().anyMatch(row -> row.role() != null && !MSDB_AGENT_ROLES.contains(row.role()))) {
            return Shape.BEYOND;
        }
        long unnamed = roles.stream().filter(row -> row.role() == null).count();
        if (unnamed > 0) {
            found.unknown(Reason.NO_ANSWER, (int) unnamed, "a role of the login's own user in " + database.name() + " cannot be named, cannot be determined");
            return Shape.UNDETERMINED;
        }
        return Shape.ALLOWED;
    }
}
