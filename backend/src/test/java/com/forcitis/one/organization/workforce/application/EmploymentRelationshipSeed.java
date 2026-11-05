package com.forcitis.one.organization.workforce.application;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Synthetic employment relationships for tests that insert version rows directly: every employee or assignment version row now belongs to one relationship (ADR-0086). */
public final class EmploymentRelationshipSeed {
    /** A synthetic active legal entity used as the employer of every seeded relationship. */
    public static final UUID EMPLOYER = UUID.fromString("019a0000-0000-7000-8000-00000000e0e1");

    private EmploymentRelationshipSeed() {
    }

    /** The employee's relationship with the synthetic employer; created (DECLARED) on first use, the same id afterwards. */
    public static UUID relationship(JdbcClient jdbc, UUID employee) {
        jdbc.sql("insert into organization_workforce.legal_entities (id, code, name, status) values (:id, 'ZREMP', 'Synthetic employer', 'ACTIVE') on conflict (id) do nothing")
                .param("id", EMPLOYER).update();
        return relationship(jdbc, employee, EMPLOYER);
    }

    /** The employee's relationship with an employer that already exists as a legal entity. */
    public static UUID relationship(JdbcClient jdbc, UUID employee, UUID employer) {
        jdbc.sql("""
                insert into organization_workforce.employment_relationships (employment_relationship_id, employee_id, employer_legal_entity_id, employer_source_kind, created_at, updated_at)
                values (uuidv7(), :employee, :employer, 'DECLARED', now(), now()) on conflict (employee_id, employer_legal_entity_id) do nothing""")
                .param("employee", employee).param("employer", employer).update();
        return jdbc.sql("select employment_relationship_id from organization_workforce.employment_relationships where employee_id = :employee and employer_legal_entity_id = :employer")
                .param("employee", employee).param("employer", employer).query(UUID.class).single();
    }

    /** For tests that write SQL text: seeds the relationship of an employee given as a quoted UUID literal. */
    public static void seed(JdbcClient jdbc, String... employeeLiterals) {
        for (String literal : employeeLiterals) {
            relationship(jdbc, UUID.fromString(literal.replace("'", "")));
        }
    }

    /** A SQL scalar expression for the relationship of an employee (quoted UUID literal or SQL expression); the relationship must have been seeded. */
    public static String ref(String employeeLiteral) {
        return "(select employment_relationship_id from organization_workforce.employment_relationships where employee_id = " + employeeLiteral + ")";
    }
}
