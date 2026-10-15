package com.forcitis.one.platform.integration.infrastructure.connection;

import static com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.DATABASE_DENIES;
import static com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.upper;

import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.DatabaseLevelPermission;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.Reason;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.SecurablePermissionRow;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/**
 * Item (g) of {@link ReadOnlyBaseline} judged by effective permission (RULING-ADR37-10 narrowed by ADR-0037 item 4 (b), one-coordinator
 * 2026-10-15), and the locators of (f) and (g) that only the read-only verification command prints.
 */
final class EffectiveGrants {

    /** The ruling names object-level and schema-level grants only; a grant on a type, a role or any other class stays a violation. */
    static final Set<String> OVERRIDABLE_CLASSES = Set.of("OBJECT_OR_COLUMN", "SCHEMA");

    /** Where a violation or an overridden grant is; printed by the read-only verification command only, never in a refusal, an ALERT or a log. */
    sealed interface Locator permits DatabaseLocator, GrantLocator {
    }

    record DatabaseLocator(String database) implements Locator {
    }

    /**
     * state G or W; self for a grant to the login's own user, whose name is then not kept. objectId is the row's major_id: a securable the
     * login has no effective permission on is hidden from its catalog views, so its names are null, and the number is what C-14 looks it up by.
     */
    record GrantLocator(String classDescription, @Nullable String schema, @Nullable String object, long objectId, String permission, String state,
            boolean self, @Nullable String grantee, @Nullable String granteeType) implements Locator {
    }

    enum OverridingDeny {
        DATABASE_DENY,
        SCHEMA_DENY
    }

    /** A GRANT that the login's own DENY of the same permission overrides (ADR-0037 item 4 (b), RULING-ADR37-10 narrowed): no violation. */
    record OverriddenGrant(GrantLocator grant, OverridingDeny by) {
    }

    private EffectiveGrants() {
    }

    /**
     * A GRANT of one of the eleven database-level DENY permissions is overridden by the login's own DENY of the same permission on the
     * database or on the securable's schema; any other permission, CONTROL included, and any grant with grant option stays a violation.
     * When the login's DENY rows cannot be read or there is none at all, such a grant cannot be determined.
     */
    static void belowTheDatabase(Findings found, List<SecurablePermissionRow> rows, ReadOnlyCatalog catalog) {
        Set<String> beyond = new TreeSet<>();
        Set<GrantLocator> violations = new LinkedHashSet<>();
        List<SecurablePermissionRow> overridable = new ArrayList<>();
        for (SecurablePermissionRow row : rows) {
            String state = row.state().trim();
            String permission = upper(row.permission());
            if (state.equals("D") || state.equals("G") && permission.equals("SELECT")) {
                continue;
            }
            if (state.equals("G") && OVERRIDABLE_CLASSES.contains(row.classDescription().trim()) && DATABASE_DENIES.contains(permission)) {
                overridable.add(row);
            } else {
                beyond.add(beyondTheBaseline(row));
                violations.add(located(row));
            }
        }
        if (!overridable.isEmpty()) {
            found.ask(catalog::databaseLevelPermissions).ifPresent(databaseLevel -> overriddenOrNot(found, overridable, databaseLevel, rows, beyond, violations));
        }
        if (!beyond.isEmpty()) {
            found.violated(Reason.SECURABLE_GRANT_BEYOND_SELECT, violations.size(), "schema, object and other grants below the database level may only be "
                    + "DENY, GRANT SELECT or a GRANT the login's own DENY of the same permission overrides, found " + beyond, List.copyOf(violations));
        }
    }

    private static void overriddenOrNot(Findings found, List<SecurablePermissionRow> overridable, List<DatabaseLevelPermission> databaseLevel,
            List<SecurablePermissionRow> rows, Set<String> beyond, Set<GrantLocator> violations) {
        Set<String> databaseDenies = new TreeSet<>();
        databaseLevel.stream().filter(p -> p.state().trim().equals("D")).forEach(p -> databaseDenies.add(upper(p.permission())));
        Set<String> schemaDenies = new TreeSet<>();
        rows.stream().filter(r -> !r.toPublic() && r.state().trim().equals("D") && r.classDescription().trim().equals("SCHEMA"))
                .forEach(r -> schemaDenies.add(r.majorId() + " " + upper(r.permission())));
        if (databaseDenies.isEmpty() && schemaDenies.isEmpty()) {
            found.unknown(Reason.EMPTY_ANSWER, 1, "the login's database-level and schema-level DENY came back empty, whether they override "
                    + overridable.size() + " grant(s) below the database level cannot be determined");
            return;
        }
        for (SecurablePermissionRow row : overridable) {
            String permission = upper(row.permission());
            if (databaseDenies.contains(permission)) {
                found.overridden.add(new OverriddenGrant(located(row), OverridingDeny.DATABASE_DENY));
            } else if (row.schemaId() != null && schemaDenies.contains(row.schemaId() + " " + permission)) {
                found.overridden.add(new OverriddenGrant(located(row), OverridingDeny.SCHEMA_DENY));
            } else {
                beyond.add(beyondTheBaseline(row));
                violations.add(located(row));
            }
        }
    }

    private static String beyondTheBaseline(SecurablePermissionRow row) {
        return row.classDescription().trim() + " " + row.majorId() + " " + row.state().trim() + " " + upper(row.permission()) + (row.toPublic() ? " to public" : "");
    }

    private static GrantLocator located(SecurablePermissionRow row) {
        return new GrantLocator(row.classDescription().trim(), row.schema(), row.object(), row.majorId(), upper(row.permission()), row.state().trim(),
                !row.toPublic(), row.toPublic() ? row.grantee() : null, row.granteeType());
    }
}
