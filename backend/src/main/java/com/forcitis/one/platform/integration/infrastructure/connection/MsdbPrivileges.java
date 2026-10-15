package com.forcitis.one.platform.integration.infrastructure.connection;

import com.forcitis.one.platform.integration.infrastructure.connection.EffectiveGrants.DatabaseLocator;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.Reason;
import com.forcitis.one.platform.integration.infrastructure.connection.SystemDatabaseUsers.OwnUserPermission;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * What the login may hold in msdb under item (f) of {@link ReadOnlyBaseline} (opus review H-1 and H-1′, one-coordinator 2026-10-15 and 2026-10-16):
 * msdb is TRUSTWORTHY and owned by sa, so a db_owner-like capability there reaches sysadmin through EXECUTE AS. Asked whenever msdb is reachable,
 * through the login's own user or through guest alone. Three checks, each needed: every grant to a principal of the login's token in msdb (own
 * user, the SQL Agent roles, nested roles, public, or guest) must fit an allow-list by class, since a grant to a role or WITH GRANT OPTION, or a
 * user-level, schema-level or object-level one, leaves the database-level effective permissions false; the own user's rows are asked once more by
 * SID; and none of {@link #EFFECTIVE} nor IMPERSONATE on dbo may be effective, which also covers what the token scan cannot list. The platform
 * defaults one-ops read off the real logins on RDS, those SQL Server gives public and the SQL Agent roles pass.
 */
final class MsdbPrivileges {

    static final List<String> EFFECTIVE = List.of("CONTROL", "ALTER", "ALTER ANY ROLE", "ALTER ANY USER", "ALTER ANY SCHEMA", "CREATE PROCEDURE");
    /** HAS_PERMS_BY_NAME('dbo', 'USER', 'IMPERSONATE'): a user-level permission none of {@link #EFFECTIVE} shows. */
    static final String IMPERSONATE = "IMPERSONATE";
    static final List<String> ASKED = Stream.concat(EFFECTIVE.stream(), Stream.of(IMPERSONATE)).toList();
    /** What SQL Server 2016 and later grant public in every database. */
    static final Set<String> PUBLIC_DATABASE_DEFAULTS = Set.of("VIEW ANY COLUMN MASTER KEY DEFINITION", "VIEW ANY COLUMN ENCRYPTION KEY DEFINITION");

    /** A row of sys.database_permissions in msdb granted to or denied a principal of the login's token there; a null column is one the server left unanswered. */
    record TokenPermission(boolean toPublic, @Nullable String classDescription, @Nullable String state, @Nullable String permission) {

        @Override
        public String toString() {
            return "TokenPermission[toPublic=" + toPublic + "]";
        }
    }

    private MsdbPrivileges() {
    }

    static void check(Findings found, ReadOnlyCatalog catalog, boolean ownUser) {
        boolean beyond = false;
        if (ownUser) {
            Optional<List<OwnUserPermission>> direct = found.ask(() -> catalog.ownUserPermissions(SystemDatabaseUsers.MSDB));
            if (direct.isPresent()) {
                beyond = judged(found, direct.get().stream().map(r -> new TokenPermission(false, r.classDescription(), r.state(), r.permission())).toList(),
                        "the permissions of the login's own user in msdb");
            }
        }
        Optional<List<TokenPermission>> token = found.ask(catalog::msdbTokenPermissions);
        if (token.isPresent()) {
            beyond |= judged(found, token.get(), "the permissions of the login's token in msdb");
        }
        Optional<Map<String, Integer>> effective = found.ask(catalog::msdbEffectivePermissions);
        if (effective.isPresent()) {
            long unanswered = ASKED.stream().filter(p -> effective.get().get(p) == null).count();
            if (unanswered > 0) {
                found.unknown(Reason.NO_ANSWER, (int) unanswered, "effective permissions of the login's own user in msdb cannot be determined");
            }
            beyond |= ASKED.stream().map(p -> effective.get().get(p)).anyMatch(answer -> answer != null && answer != 0);
        }
        if (beyond) {
            found.violated(Reason.SYSTEM_DATABASE_OWN_USER_PRIVILEGE, 1,
                    "the login's own user in msdb holds permissions beyond those the platform gives every login",
                    List.of(new DatabaseLocator(SystemDatabaseUsers.MSDB)));
        }
    }

    private static boolean judged(Findings found, List<TokenPermission> rows, String what) {
        if (rows.isEmpty()) {
            found.unknown(Reason.EMPTY_ANSWER, 1, what + " came back empty, cannot be determined");
            return false;
        }
        long unanswered = rows.stream().filter(r -> r.classDescription() == null || r.state() == null || r.permission() == null).count();
        if (unanswered > 0) {
            found.unknown(Reason.NO_ANSWER, (int) unanswered, what + " cannot be determined");
        }
        return rows.stream().filter(r -> r.classDescription() != null && r.state() != null && r.permission() != null).anyMatch(r -> !allowed(r));
    }

    private static boolean allowed(TokenPermission row) {
        String state = row.state().trim();
        String permission = ReadOnlyBaseline.upper(row.permission());
        return switch (row.classDescription().trim()) {
            case "DATABASE" -> state.equals("G") && (permission.equals("CONNECT") || row.toPublic() && PUBLIC_DATABASE_DEFAULTS.contains(permission));
            case "OBJECT_OR_COLUMN" -> state.equals("D") || state.equals("G") && (permission.equals("SELECT") || permission.equals("EXECUTE"));
            default -> false;
        };
    }
}
