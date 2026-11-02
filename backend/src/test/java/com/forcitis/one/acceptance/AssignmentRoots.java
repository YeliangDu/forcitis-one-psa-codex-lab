package com.forcitis.one.acceptance;

import com.forcitis.one.kernel.Uuids;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * #711 (DD revision 2 on #711, sections 4.1, 4.2 and 7 M7): every assignment version belongs to an assignment root in employee_assignments
 * and carries its assignment_id and assignment_kind, so a seed that writes assignment_versions directly first finds or creates the
 * employee's one PRIMARY root. Until the #711 migration exists the seeds are written as before (the table is looked up, as the #880 seeds
 * look up change_channel_kind), so that existing tests keep their results before and after #711.
 */
public final class AssignmentRoots {

    public static final String TABLE = "employee_assignments";
    public static final String PRIMARY = "PRIMARY";
    public static final String CONCURRENT = "CONCURRENT";

    private AssignmentRoots() {
    }

    public static boolean exist(Connection workforce) throws SQLException {
        try (PreparedStatement found = workforce.prepareStatement(
                "select 1 from information_schema.tables where table_schema = 'organization_workforce' and table_name = ?")) {
            found.setString(1, TABLE);
            try (ResultSet rows = found.executeQuery()) {
                return rows.next();
            }
        }
    }

    /** The employee's PRIMARY root, created when there is none; null while the table does not exist yet. */
    public static UUID primaryOf(Connection workforce, Object employeeId) throws SQLException {
        if (!exist(workforce)) {
            return null;
        }
        try (PreparedStatement found = workforce.prepareStatement("select assignment_id from " + TABLE + " where employee_id = ? and assignment_kind = 'PRIMARY'")) {
            found.setObject(1, employeeId);
            try (ResultSet rows = found.executeQuery()) {
                if (rows.next()) {
                    return rows.getObject(1, UUID.class);
                }
            }
        }
        return create(workforce, employeeId, PRIMARY);
    }

    /** A new root of the kind (a CONCURRENT one, or a PRIMARY one for a red case); the table must exist. */
    public static UUID create(Connection workforce, Object employeeId, String kind) throws SQLException {
        UUID id = Uuids.timeOrdered(AcceptanceSupport.NOW);
        OffsetDateTime at = OffsetDateTime.ofInstant(AcceptanceSupport.NOW, ZoneOffset.UTC);
        try (PreparedStatement insert = workforce.prepareStatement(
                "insert into " + TABLE + " (assignment_id, employee_id, assignment_kind, created_at, updated_at) values (?, ?, ?, ?, ?)")) {
            insert.setObject(1, id);
            insert.setObject(2, employeeId);
            insert.setString(3, kind);
            insert.setObject(4, at);
            insert.setObject(5, at);
            insert.executeUpdate();
        }
        return id;
    }

    /**
     * The insert into assignment_versions with the root's two columns added first when there is a root (they are bound as the first
     * parameter and the literal PRIMARY); unchanged when root is null.
     */
    public static String withRoot(String insert, UUID root) {
        if (root == null) {
            return insert;
        }
        String columns = "insert into assignment_versions (";
        int values = insert.indexOf("values (");
        if (!insert.contains(columns) || values < 0) {
            throw new AssertionError("not an insert into assignment_versions with a values list: " + insert);
        }
        return insert.substring(0, values).replace(columns, columns + "assignment_id, assignment_kind, ") + "values (?, 'PRIMARY', " + insert.substring(values + "values (".length());
    }

    /** The parameters of {@link #withRoot}: the root first when there is one. */
    public static Object[] arguments(UUID root, Object... arguments) {
        if (root == null) {
            return arguments;
        }
        Object[] all = new Object[arguments.length + 1];
        all[0] = root;
        System.arraycopy(arguments, 0, all, 1, arguments.length);
        return all;
    }
}
