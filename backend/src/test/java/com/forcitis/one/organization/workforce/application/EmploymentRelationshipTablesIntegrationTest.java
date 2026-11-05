package com.forcitis.one.organization.workforce.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** #1372 (ADR-0086, appendix A): employment_relationships is append-only; the one-time confirmation of a migrated row is the only change it sees. */
class EmploymentRelationshipTablesIntegrationTest extends WorkforceIntegrationSupport {
    static final String TABLE = "organization_workforce.employment_relationships";

    @Autowired
    DataSource dataSource;

    private UUID employee;
    private UUID declared;
    private UUID migrated;

    @BeforeEach
    void aDeclaredAndAMigratedRelationship() {
        employee = PersonnelSeedSupport.employed(jdbc, clock, "ER-1").employee();
        declared = EmploymentRelationshipSeed.relationship(jdbc, employee);
        UUID otherEmployer = UUID.fromString("019a0000-0000-7000-8000-00000000e0e2");
        jdbc.sql("insert into organization_workforce.legal_entities (id, code, name, status) values (:id, :code, 'Synthetic employer two', 'ACTIVE') on conflict (id) do nothing")
                .param("id", otherEmployer).param("code", "ZRB").update();
        migrated = UUID.randomUUID();
        jdbc.sql("""
                insert into %s (employment_relationship_id, employee_id, employer_legal_entity_id, employer_source_kind, created_at, updated_at)
                values (:id, :employee, :employer, 'MIGRATION_ATTRIBUTION', now(), now())""".formatted(TABLE))
                .param("id", migrated).param("employee", employee).param("employer", otherEmployer).update();
    }

    private int update(UUID id, String set) {
        return jdbc.sql("update " + TABLE + " set " + set + " where employment_relationship_id = :id").param("id", id).update();
    }

    @Test
    @DisplayName("#1372 the one-time confirmation of a migrated row changes employer_confirmed_at and updated_at together and nothing else")
    void theConfirmationOfAMigratedRowIsAllowedOnce() {
        assertThat(update(migrated, "employer_confirmed_at = now(), updated_at = now()")).isEqualTo(1);
        assertThatThrownBy(() -> update(migrated, "employer_confirmed_at = now() + interval '1 day', updated_at = now()")).hasMessageContaining("append-only");
    }

    @Test
    @DisplayName("#1372 a new updated_at alone, or any other column, is refused for a migrated row")
    void anythingButTheConfirmationIsRefused() {
        assertThatThrownBy(() -> update(migrated, "updated_at = now() + interval '1 second'")).hasMessageContaining("append-only");
        assertThatThrownBy(() -> update(migrated, "employer_confirmed_at = now(), employer_source_kind = 'DECLARED', updated_at = now()")).hasMessageContaining("append-only");
        assertThatThrownBy(() -> update(migrated, "employer_confirmed_at = now(), employer_legal_entity_id = :employer".replace(":employer", "'" + EmploymentRelationshipSeed.EMPLOYER + "'")))
                .hasMessageContaining("append-only");
    }

    @Test
    @DisplayName("#1372 a declared row cannot be confirmed or changed at all")
    void aDeclaredRowIsNeverChanged() {
        assertThatThrownBy(() -> update(declared, "employer_confirmed_at = now(), updated_at = now()")).hasMessageContaining("append-only");
        assertThatThrownBy(() -> update(declared, "updated_at = now()")).hasMessageContaining("append-only");
    }

    @Test
    @DisplayName("#1372 only a declared-or-migrated source kind exists, and only a migrated row can carry a confirmation")
    void theSourceKindGuardsTheConfirmation() {
        assertThatThrownBy(() -> jdbc.sql("""
                insert into %s (employment_relationship_id, employee_id, employer_legal_entity_id, employer_source_kind, employer_confirmed_at, created_at, updated_at)
                values (gen_random_uuid(), :employee, :employer, 'DECLARED', now(), now(), now())""".formatted(TABLE))
                .param("employee", employee).param("employer", UUID.randomUUID()).update()).isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("#1372 a row is never truncated, and a role that does not own the table cannot delete one")
    void deleteByANonOwnerAndTruncateAreRefused() throws SQLException {
        assertThatThrownBy(() -> jdbc.sql("truncate table " + TABLE + " cascade").update()).hasMessageContaining("append-only");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("do $$ begin if not exists (select 1 from pg_roles where rolname = 'relationship_reader') then create role relationship_reader; end if; end $$");
            statement.execute("grant usage on schema organization_workforce to relationship_reader");
            statement.execute("grant select, delete on " + TABLE + " to relationship_reader");
            statement.execute("set role relationship_reader");
            try {
                assertThatThrownBy(() -> statement.execute("delete from " + TABLE)).hasMessageContaining("append-only");
            } finally {
                statement.execute("reset role");
            }
        }
        assertThat(jdbc.sql("select count(*) from " + TABLE + " where employee_id = :e").param("e", employee).query(Integer.class).single()).isEqualTo(2);
    }

    @Test
    @DisplayName("#1372 appendix A: the three open-command-per-day indexes of personnel_commands are unique and partial on the open statuses (application rules refuse first; these are the last line)")
    void theOpenCommandIndexesAreUniqueAndPartial() {
        for (String index : new String[] {"uk_personnel_commands_open_per_relationship_day", "uk_personnel_commands_open_person_day", "uk_personnel_commands_open_per_assignment_day"}) {
            assertThat(jdbc.sql("""
                    select i.indisunique and pg_get_expr(i.indpred, i.indrelid) like '%PENDING%AWAITING_ATTRIBUTION_COUNTERSIGN%' from pg_index i
                      join pg_class c on c.oid = i.indexrelid join pg_namespace n on n.oid = c.relnamespace
                     where n.nspname = 'organization_workforce' and c.relname = :index""").param("index", index).query(Boolean.class).single())
                    .as(index).isTrue();
        }
    }
}
