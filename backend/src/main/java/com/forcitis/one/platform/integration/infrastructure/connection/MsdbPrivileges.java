package com.forcitis.one.platform.integration.infrastructure.connection;

import com.forcitis.one.platform.integration.infrastructure.connection.EffectiveGrants.DatabaseLocator;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.Reason;
import com.forcitis.one.platform.integration.infrastructure.connection.SystemDatabaseUsers.OwnUserPermission;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * msdb is TRUSTWORTHY and owned by sa, so a db_owner-like capability there reaches sysadmin through EXECUTE AS (opus review H-1, H-A, M-A). Checked
 * whenever msdb is reachable, through the own user or guest alone: every grant to a token principal must fit an allow-list by class, none of
 * {@link #EFFECTIVE} nor IMPERSONATE on dbo may be effective, the token holds no role beyond {@link #TOKEN_ROLES} (a nested role such as db_ssisadmin
 * brings only object-level grants the other checks allow), and the token owns nothing but the schemas the platform gives its principals.
 */
final class MsdbPrivileges {

    static final List<String> EFFECTIVE = List.of("CONTROL", "ALTER", "ALTER ANY ROLE", "ALTER ANY USER", "ALTER ANY SCHEMA", "CREATE PROCEDURE");
    /** HAS_PERMS_BY_NAME('dbo', 'USER', 'IMPERSONATE'): a user-level permission none of {@link #EFFECTIVE} shows. */
    static final String IMPERSONATE = "IMPERSONATE";
    static final List<String> ASKED = Stream.concat(EFFECTIVE.stream(), Stream.of(IMPERSONATE)).toList();
    /** The roles a login's token may hold in msdb: public and the SQL Agent roles RDS adds; compared in process, never printed. */
    static final Set<String> TOKEN_ROLES = Set.of("public", "SQLAgentOperatorRole", "SQLAgentReaderRole", "SQLAgentUserRole");
    /** The principals that own a schema of their own name in msdb on every SQL Server: guest and the SQL Agent roles. */
    static final Set<String> PLATFORM_SCHEMA_OWNERS = Set.of("guest", "SQLAgentOperatorRole", "SQLAgentReaderRole", "SQLAgentUserRole");
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
        Optional<List<String>> roles = found.ask(catalog::msdbTokenRoles);
        if (roles.isPresent()) {
            beyond |= roles(found, roles.get());
        }
        Optional<Long> owned = found.ask(catalog::msdbOwnedByToken);
        if (owned.isPresent() && owned.get() > 0) {
            beyond = true;
        }
        Optional<Map<String, Integer>> effective = found.ask(catalog::msdbEffectivePermissions);
        if (effective.isPresent()) {
            long unanswered = ASKED.stream().filter(p -> effective.get().get(p) == null).count();
            if (unanswered > 0) {
                found.unknown(Reason.NO_ANSWER, (int) unanswered, "effective permissions of the login in msdb cannot be determined");
            }
            beyond |= ASKED.stream().map(p -> effective.get().get(p)).anyMatch(answer -> answer != null && answer != 0);
        }
        if (beyond) {
            found.violated(Reason.SYSTEM_DATABASE_OWN_USER_PRIVILEGE, 1,
                    "the login in msdb holds permissions beyond those the platform gives every login",
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

    private static boolean roles(Findings found, List<String> names) {
        if (names.isEmpty()) {
            found.unknown(Reason.EMPTY_ANSWER, 1, "the roles of the login's token in msdb came back empty, cannot be determined");
            return false;
        }
        long unnamed = names.stream().filter(Objects::isNull).count();
        if (unnamed > 0) {
            found.unknown(Reason.NO_ANSWER, (int) unnamed, "a role of the login's token in msdb cannot be named, cannot be determined");
        }
        return names.stream().filter(Objects::nonNull).anyMatch(name -> !TOKEN_ROLES.contains(name));
    }

    private static boolean allowed(TokenPermission row) {
        String state = row.state().trim();
        if (state.equals("D")) {
            return true;
        }
        String permission = ReadOnlyBaseline.upper(row.permission());
        return switch (row.classDescription().trim()) {
            case "DATABASE" -> state.equals("G") && (permission.equals("CONNECT") || row.toPublic() && PUBLIC_DATABASE_DEFAULTS.contains(permission));
            case "OBJECT_OR_COLUMN" -> state.equals("G") && (permission.equals("SELECT") || permission.equals("EXECUTE"));
            default -> false;
        };
    }
}
