package com.forcitis.one.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.fixture.doubles.TestAccounts;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * #1329 (data design checklist D8): platform_identity.handled_personnel_commands only grows. The database itself refuses UPDATE, DELETE and
 * TRUNCATE (function refuse_append_only_rewrite, restrict_violation 23001), and still takes the two statements the application uses.
 */
class HandledPersonnelCommandsAppendOnlyIntegrationTest extends IdentityIntegrationTest {

    private void refusedByTheFunction(String sql, Object... params) {
        var statement = jdbc.sql(sql);
        for (int i = 0; i < params.length; i++) {
            statement = statement.param(params[i]);
        }
        var refused = statement;
        assertThatThrownBy(refused::update).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("platform_identity.handled_personnel_commands is append-only")
                .hasRootCauseInstanceOf(org.postgresql.util.PSQLException.class)
                .satisfies(e -> assertThat(((SQLException) e.getCause()).getSQLState()).isEqualTo("23001"));
    }

    private UUID handled() {
        createAccount(8801);
        UUID command = UUID.randomUUID();
        jdbc.sql("""
                insert into platform_identity.handled_personnel_commands (personnel_command_id, user_id, command_kind, outcome_kind, account_role_grant_id, handled_at)
                values (:id, :user, 'REINSTATE', 'NOT_EMPLOYED', null, now())
                """).param("id", command).param("user", TestAccounts.account(8801).value()).update();
        return command;
    }

    @Test
    @DisplayName("#1329 a handled command row is refused UPDATE, DELETE and TRUNCATE by the database, and stays")
    void rewriteIsRefused() {
        UUID command = handled();

        refusedByTheFunction("update platform_identity.handled_personnel_commands set outcome_kind = 'ALREADY_GRANTED' where personnel_command_id = ?", command);
        refusedByTheFunction("update platform_identity.handled_personnel_commands set updated_at = updated_at where personnel_command_id = ?", command);
        refusedByTheFunction("delete from platform_identity.handled_personnel_commands where personnel_command_id = ?", command);
        refusedByTheFunction("delete from platform_identity.handled_personnel_commands");
        refusedByTheFunction("truncate table platform_identity.handled_personnel_commands");

        assertThat(jdbc.sql("select count(*) from platform_identity.handled_personnel_commands where personnel_command_id = :id")
                .param("id", command).query(Long.class).single()).isEqualTo(1);
    }

    @Test
    @DisplayName("#1329 what the application does is unaffected: INSERT and the exists-read still work")
    void insertAndReadStillWork() {
        UUID command = handled();
        assertThat(jdbc.sql("select exists (select 1 from platform_identity.handled_personnel_commands where personnel_command_id = :id)")
                .param("id", command).query(Boolean.class).single()).isTrue();
    }
}
