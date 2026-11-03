package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.REGULAR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.Uuids;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #895 acceptance part, one-design ruling on #908 (comment 6590861391) item 1, a new done-when line on #895: "未会签不写版本" (ADR-0073) is
 * held fail-closed by the database. The transition trigger of personnel_commands refuses AWAITING_ATTRIBUTION_COUNTERSIGN to PENDING or
 * EFFECTIVE unless the command has at least one CHANGE row and every CHANGE row is APPROVED; WITHDRAWN stays allowed (rejection, lapse
 * and drafter withdrawal). Rows are written directly in the schema, as a writer that bypassed the application would.
 */
class CountersignExitRuleIT extends PersonnelCommandSupport {

    static final TestDatabase postgres = TestPostgres.database(CountersignExitRuleIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private static final String AWAITING = "AWAITING_ATTRIBUTION_COUNTERSIGN";

    private OffsetDateTime at;
    private UUID e2;
    private Staff decider;

    @BeforeEach
    void aSecondEntity() throws Exception {
        at = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
        Staff admin = staff("org_data_admin");
        e2 = createLegalEntity(admin.session(), newLegalEntityCode("XR"), "Synthetic exit rule entity");
        decider = admin;
    }

    /** A CHANGE_LEGAL_ENTITY of a new employee from the world's entity to E2, awaiting countersign, drafted by the 人事专员. */
    private UUID awaiting() throws Exception {
        UUID command = Uuids.timeOrdered(NOW);
        try (Connection workforce = schemaConnection("organization_workforce")) {
            update(workforce, """
                    insert into personnel_commands (personnel_command_id, employee_id, command_kind, effective_date, status, target_legal_entity_id,
                      countersign_previous_legal_entity_id, countersign_new_legal_entity_id, created_at, created_by, updated_at)
                    values (?, ?, 'CHANGE_LEGAL_ENTITY', ?, ?, ?, ?, ?, ?, ?, ?)""",
                    command, stored(employed(REGULAR)), TODAY.plusDays(3), AWAITING, e2, legalEntity, e2, at, hr.userId(), at);
        }
        return command;
    }

    /** A CHANGE row of the command for the entity; decided as given (null leaves it open). */
    private void changeRow(UUID command, UUID entity, String decision) throws SQLException {
        UUID id = Uuids.timeOrdered(NOW);
        try (Connection workforce = schemaConnection("organization_workforce")) {
            Object employee = query(workforce, "select employee_id from personnel_commands where personnel_command_id = ?", command).get(0).get("employee_id");
            update(workforce, """
                    insert into attribution_countersigns (attribution_countersign_id, personnel_command_id, employee_id, drafted_by, request_kind,
                      request_sequence_number, legal_entity_id, requested_at, requested_by, due_on_date, created_at, updated_at)
                    values (?, ?, ?, ?, 'CHANGE', 1, ?, ?, ?, ?, ?, ?)""",
                    id, command, employee, hr.userId(), entity, at, hr.userId(), TODAY.plusDays(3), at, at);
            if (decision != null) {
                update(workforce, "update attribution_countersigns set decision_kind = ?, decided_at = ?, decided_by = ?, decision_reason = ?, decision_audit_entry_id = ?,"
                        + " updated_at = ? where attribution_countersign_id = ?", decision, at, "LAPSED".equals(decision) ? null : decider.userId(),
                        "REJECTED".equals(decision) ? "synthetic reason" : null, Uuids.timeOrdered(NOW), at, id);
            }
        }
    }

    /** Moves the command to the status directly; null when the database accepted it, else the refusal. */
    private Throwable moveTo(UUID command, String status) {
        return catchThrowable(() -> {
            try (Connection workforce = schemaConnection("organization_workforce")) {
                if (status.equals("WITHDRAWN")) {
                    update(workforce, "update personnel_commands set status = 'WITHDRAWN', withdrawn_at = ?, withdrawn_by = ?, withdrawal_reason = 'synthetic', updated_at = ?"
                            + " where personnel_command_id = ?", at, hr.userId(), at, command);
                } else {
                    update(workforce, "update personnel_commands set status = ?, applied_at = ?, updated_at = ? where personnel_command_id = ?",
                            status, status.equals("EFFECTIVE") ? at : null, at, command);
                }
            }
        });
    }

    private void assertExitRefused(UUID command, String status) throws Exception {
        assertThat(moveTo(command, status)).as("AWAITING -> " + status + " refused by the transition trigger").isInstanceOf(SQLException.class);
        assertThat(command(command).get("status")).isEqualTo(AWAITING);
    }

    @Test
    @DisplayName("#895 one-design 裁定（#908 comment 6590861391 第 1 项，fail-closed）：待法人归属会签的人事命令，有一行 CHANGE 未决、被拒或已失效、或一行 CHANGE 都没有时，数据库拒绝把它改成待生效或已生效，状态不变")
    void anAwaitingCommandCannotLeaveWithoutEveryChangeApproved() throws Exception {
        for (String other : new String[] {null, "REJECTED", "LAPSED"}) {
            UUID command = awaiting();
            changeRow(command, legalEntity, "APPROVED");
            changeRow(command, e2, other);
            assertExitRefused(command, "PENDING");
            assertExitRefused(command, "EFFECTIVE");
        }
        UUID none = awaiting();
        assertExitRefused(none, "PENDING");
        assertExitRefused(none, "EFFECTIVE");
    }

    @Test
    @DisplayName("#895 one-design 裁定（#908 comment 6590861391 第 1 项）通过的孪生：所有 CHANGE 行都批准时可以转待生效；有未决 CHANGE 行时仍可以转已撤回")
    void theTwinsLeaveAwaiting() throws Exception {
        UUID approved = awaiting();
        changeRow(approved, legalEntity, "APPROVED");
        changeRow(approved, e2, "APPROVED");
        assertThat(moveTo(approved, "PENDING")).isNull();

        UUID withdrawn = awaiting();
        changeRow(withdrawn, legalEntity, null);
        assertThat(moveTo(withdrawn, "WITHDRAWN")).isNull();
    }
}
