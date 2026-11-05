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
 * #1371 (ADR-0086, docs/architecture/proposals/employment-relationship.md section 4.1): every version of a person hangs on one of the
 * person's employment relationships, so a seed that writes employee or assignment versions directly first finds or creates the
 * relationship. Like {@link AssignmentRoots}, the adapters leave the statement unchanged while the relationship tables do not exist, so
 * that existing tests keep their results before and after the schema change. Only the set-up of a test goes through here, never an assertion.
 */
public final class EmploymentRelationshipSeeds {

    public static final String VERSIONS = "employment_relationship_versions";
    /** The employer of a seeded person whose test names none: one shared synthetic legal entity. */
    private static final String FIXTURE_ENTITY_CODE = "SEEDER";

    private EmploymentRelationshipSeeds() {
    }

    private static final java.util.Map<String, Boolean> RENAMED = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * The statement as the connection's database spells it: once employment_relationships exists, employee_versions is
     * employment_relationship_versions and its key column employment_relationship_version_id (ruling 4 of #1371). Tests that read the
     * table by its old name keep working before and after the schema change; a statement without the old names is returned unchanged.
     */
    public static String translate(Connection connection, String sql) throws SQLException {
        if (!sql.contains("employee_version")) {
            return sql;
        }
        Boolean renamed = RENAMED.get(connection.getMetaData().getURL());
        if (renamed == null) {
            renamed = exist(connection);
            RENAMED.put(connection.getMetaData().getURL(), renamed);
        }
        return renamed ? sql.replaceAll("\\bemployee_versions\\b", VERSIONS).replaceAll("\\bemployee_version_id\\b", "employment_relationship_version_id") : sql;
    }

    public static boolean exist(Connection workforce) throws SQLException {
        try (PreparedStatement found = workforce.prepareStatement(
                "select 1 from information_schema.tables where table_schema = 'organization_workforce' and table_name = 'employment_relationships'")) {
            try (ResultSet rows = found.executeQuery()) {
                return rows.next();
            }
        }
    }

    /**
     * The person's relationship with the employer (the fixture legal entity when null), created when there is none; null while the table
     * does not exist yet. The root row of the person must exist.
     */
    public static UUID of(Connection workforce, Object employeeId, Object employerLegalEntityId) throws SQLException {
        if (!exist(workforce)) {
            return null;
        }
        Object employer = employerLegalEntityId != null ? employerLegalEntityId : fixtureEntity(workforce);
        try (PreparedStatement found = workforce.prepareStatement(
                "select employment_relationship_id from employment_relationships where employee_id = ? and employer_legal_entity_id = ?")) {
            found.setObject(1, employeeId);
            found.setObject(2, employer);
            try (ResultSet rows = found.executeQuery()) {
                if (rows.next()) {
                    return rows.getObject(1, UUID.class);
                }
            }
        }
        UUID id = Uuids.timeOrdered(AcceptanceSupport.NOW);
        OffsetDateTime at = OffsetDateTime.ofInstant(AcceptanceSupport.NOW, ZoneOffset.UTC);
        try (PreparedStatement insert = workforce.prepareStatement(
                "insert into employment_relationships (employment_relationship_id, employee_id, employer_legal_entity_id, employer_source_kind, created_at, updated_at)"
                        + " values (?, ?, ?, 'DECLARED', ?, ?)")) {
            insert.setObject(1, id);
            insert.setObject(2, employeeId);
            insert.setObject(3, employer);
            insert.setObject(4, at);
            insert.setObject(5, at);
            insert.executeUpdate();
        }
        return id;
    }

    /** The person's relationship when it already has exactly one, else created with the fixture employer. */
    public static UUID of(Connection workforce, Object employeeId) throws SQLException {
        if (!exist(workforce)) {
            return null;
        }
        try (PreparedStatement found = workforce.prepareStatement("select employment_relationship_id from employment_relationships where employee_id = ? limit 1")) {
            found.setObject(1, employeeId);
            try (ResultSet rows = found.executeQuery()) {
                if (rows.next()) {
                    return rows.getObject(1, UUID.class);
                }
            }
        }
        return of(workforce, employeeId, null);
    }

    private static Object fixtureEntity(Connection workforce) throws SQLException {
        try (PreparedStatement found = workforce.prepareStatement("select id from legal_entities where code = ?")) {
            found.setString(1, FIXTURE_ENTITY_CODE);
            try (ResultSet rows = found.executeQuery()) {
                if (rows.next()) {
                    return rows.getObject(1);
                }
            }
        }
        UUID id = Uuids.timeOrdered(AcceptanceSupport.NOW);
        try (PreparedStatement insert = workforce.prepareStatement(
                "insert into legal_entities (id, code, name, status) values (?, ?, 'Synthetic seed employer', 'ACTIVE')")) {
            insert.setObject(1, id);
            insert.setString(2, FIXTURE_ENTITY_CODE);
            insert.executeUpdate();
        }
        return id;
    }

    /**
     * The insert into employee_versions as the relationship table: renamed table and key column, the relationship id bound as the first
     * parameter; unchanged when relationship is null.
     */
    public static String employeeVersion(String insert, UUID relationship) {
        if (relationship == null) {
            return insert;
        }
        int values = insert.indexOf("values (");
        if (!insert.contains("insert into employee_versions (employee_version_id,") || values < 0) {
            throw new AssertionError("not an insert into employee_versions with a values list: " + insert);
        }
        return insert.substring(0, values).replace("insert into employee_versions (employee_version_id,",
                "insert into " + VERSIONS + " (employment_relationship_id, employment_relationship_version_id,") + "values (?, " + insert.substring(values + "values (".length());
    }

    /** The insert into assignment_versions with the relationship column added first (after {@link AssignmentRoots#withRoot} if both apply). */
    public static String assignmentVersion(String insert, UUID relationship) {
        if (relationship == null) {
            return insert;
        }
        int values = insert.indexOf("values (");
        if (!insert.contains("insert into assignment_versions (") || values < 0) {
            throw new AssertionError("not an insert into assignment_versions with a values list: " + insert);
        }
        return insert.substring(0, values).replace("insert into assignment_versions (", "insert into assignment_versions (employment_relationship_id, ")
                + "values (?, " + insert.substring(values + "values (".length());
    }

    /** As {@link #assignmentVersion} for an insert that selects its values: the relationship id is the first selected column. */
    public static String assignmentVersionSelect(String insert, UUID relationship) {
        if (relationship == null) {
            return insert;
        }
        return insert.replace("insert into assignment_versions (assignment_version_id,", "insert into assignment_versions (employment_relationship_id, assignment_version_id,")
                .replace("select ?, ", "select ?, ?, ");
    }

    /** The parameters of {@link #employeeVersion} and {@link #assignmentVersion}: the relationship first when there is one. */
    public static Object[] arguments(UUID relationship, Object... arguments) {
        if (relationship == null) {
            return arguments;
        }
        Object[] all = new Object[arguments.length + 1];
        all[0] = relationship;
        System.arraycopy(arguments, 0, all, 1, arguments.length);
        return all;
    }
}
