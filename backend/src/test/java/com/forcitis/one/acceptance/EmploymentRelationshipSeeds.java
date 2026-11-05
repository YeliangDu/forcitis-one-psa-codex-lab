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
 * person's employment relationships, so a seed that writes employment relationship versions or assignment versions directly first
 * takes the person's relationship from here and binds it in its own insert. Only the set-up of a test goes through here, never an assertion.
 */
public final class EmploymentRelationshipSeeds {

    /** The employer of a seeded person whose test names none: one shared synthetic legal entity. */
    private static final String FIXTURE_ENTITY_CODE = "SEEDER";

    private EmploymentRelationshipSeeds() {
    }

    /**
     * The person's relationship with the employer (the fixture legal entity when null), created when there is none. The root row of the
     * person must exist.
     */
    public static UUID relationshipOf(Connection workforce, Object employeeId, Object employerLegalEntityId) throws SQLException {
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
    public static UUID relationshipOf(Connection workforce, Object employeeId) throws SQLException {
        try (PreparedStatement found = workforce.prepareStatement("select employment_relationship_id from employment_relationships where employee_id = ? limit 1")) {
            found.setObject(1, employeeId);
            try (ResultSet rows = found.executeQuery()) {
                if (rows.next()) {
                    return rows.getObject(1, UUID.class);
                }
            }
        }
        return relationshipOf(workforce, employeeId, null);
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
}
