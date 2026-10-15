package com.forcitis.one.platform.integration.infrastructure.connection;

import com.forcitis.one.platform.integration.infrastructure.connection.EffectiveGrants.DatabaseLocator;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.Reason;
import com.forcitis.one.platform.integration.infrastructure.connection.SystemDatabaseUsers.OwnUserPermission;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What the login's own msdb user may hold under item (f) of {@link ReadOnlyBaseline} (opus review H-1, one-coordinator 2026-10-15): msdb is
 * TRUSTWORTHY and owned by sa, so a db_owner-like capability there reaches sysadmin through EXECUTE AS. Two checks, both needed: the user's own
 * rows must fit an allow-list by class, because IMPERSONATE on a user or a grant WITH GRANT OPTION leaves every effective permission below
 * false; and none of {@link #EFFECTIVE} may be effective, because nesting, grants to the SQL Agent roles and grants to public leave the user's
 * own rows unchanged. The platform defaults one-ops read off the real logins on RDS (CONNECT, object-level GRANT SELECT and EXECUTE, a DENY,
 * and what the SQL Agent roles bring) pass both.
 */
final class MsdbPrivileges {

    static final List<String> EFFECTIVE = List.of("CONTROL", "ALTER", "ALTER ANY ROLE", "ALTER ANY USER", "ALTER ANY SCHEMA", "CREATE PROCEDURE");

    private MsdbPrivileges() {
    }

    static void check(Findings found, ReadOnlyCatalog catalog) {
        boolean beyond = false;
        Optional<List<OwnUserPermission>> direct = found.ask(() -> catalog.ownUserPermissions(SystemDatabaseUsers.MSDB));
        if (direct.isPresent()) {
            if (direct.get().isEmpty()) {
                found.unknown(Reason.EMPTY_ANSWER, 1, "the permissions of the login's own user in msdb came back empty, cannot be determined");
            } else if (!direct.get().stream().allMatch(MsdbPrivileges::allowed)) {
                beyond = true;
            }
        }
        Optional<Map<String, Integer>> effective = found.ask(catalog::msdbEffectivePermissions);
        if (effective.isPresent()) {
            long unanswered = EFFECTIVE.stream().filter(p -> effective.get().get(p) == null).count();
            if (unanswered > 0) {
                found.unknown(Reason.NO_ANSWER, (int) unanswered, "effective permissions of the login's own user in msdb cannot be determined");
            }
            beyond |= EFFECTIVE.stream().map(p -> effective.get().get(p)).anyMatch(answer -> answer != null && answer != 0);
        }
        if (beyond) {
            found.violated(Reason.SYSTEM_DATABASE_OWN_USER_PRIVILEGE, 1,
                    "the login's own user in msdb holds permissions beyond those the platform gives every login",
                    List.of(new DatabaseLocator(SystemDatabaseUsers.MSDB)));
        }
    }

    static boolean allowed(OwnUserPermission row) {
        String state = row.state().trim();
        String permission = ReadOnlyBaseline.upper(row.permission());
        return switch (row.classDescription().trim()) {
            case "DATABASE" -> state.equals("G") && permission.equals("CONNECT");
            case "OBJECT_OR_COLUMN" -> state.equals("D") || state.equals("G") && (permission.equals("SELECT") || permission.equals("EXECUTE"));
            default -> false;
        };
    }
}
