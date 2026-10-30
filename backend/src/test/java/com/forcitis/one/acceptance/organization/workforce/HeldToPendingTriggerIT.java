package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import java.sql.Connection;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #931 DD signed in the ruling on #732 (direct SQL on the workforce schema). Kept in a class of its own, with its own database, because the
 * rows it writes by hand (a due command left PENDING, transitions numbered by hand) must never be seen by the due-date job of another test.
 * The transitions are numbered as the real path does: 0 for null to HELD, 1 for HELD to PENDING. No test here runs the due-date job.
 */
class HeldToPendingTriggerIT extends PersonnelCommandSupport {

    static final TestDatabase postgres = TestPostgres.database(HeldToPendingTriggerIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private static final OffsetDateTime AT = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);

    private void insertTransition(Connection workforce, String aggregateType, UUID aggregate, long sequence, String from, String to) throws Exception {
        update(workforce, """
                insert into state_transitions (id, aggregate_type_code, aggregate_id, transition_sequence_number, from_status, to_status, occurred_on_date,
                  recorded_at, actor_type, actor_id, cause_kind, command_id, correlation_id, created_at, updated_at)
                values (gen_random_uuid(), ?, ?, ?, ?, ?, ?, ?, 'USER', ?, 'COMMAND', gen_random_uuid(), gen_random_uuid(), ?, ?)""",
                aggregateType, aggregate, sequence, from, to, TODAY, AT, hr.userId(), AT, AT);
    }

    @Test
    @DisplayName("#931 DD（ruling #732）：触发器 refuse_personnel_command_rewrite 与 ck_state_transitions_status_of_aggregate 放行 PersonnelCommand 的 HELD 到 PENDING（状态改写与 state_transitions 记录都被接受，序号 0 与 1），不新增列")
    void theDatabaseAcceptsHeldToPending() throws Exception {
        Held held = held();
        try (Connection workforce = schemaConnection("organization_workforce")) {
            update(workforce, """
                    update separation_holds set status = 'RELEASED', released_at = ?, released_by = ?, release_reason = ?, release_channel_kind = 'INTERFACE',
                      updated_at = ?, version = version + 1 where separation_hold_id = ?""", AT, hr.userId(), "synthetic: released by hand", AT, held.hold());
            update(workforce, "update personnel_commands set status = 'PENDING', updated_at = ?, version = version + 1 where personnel_command_id = ?", AT, held.command());
            insertTransition(workforce, AGGREGATE_PERSONNEL_COMMAND, held.command(), 0, null, HELD);
            insertTransition(workforce, AGGREGATE_PERSONNEL_COMMAND, held.command(), 1, HELD, PENDING);
        }

        assertThat(commandStatus(held.command())).isEqualTo(PENDING);
        assertThat(statusPath(AGGREGATE_PERSONNEL_COMMAND, held.command())).containsExactly(HELD, PENDING);
    }

    private String commandStatus(UUID command) throws Exception {
        return (String) command(command).get("status");
    }

    private java.util.List<String> statusPath(String aggregateType, UUID aggregateId) throws Exception {
        java.util.List<String> path = new java.util.ArrayList<>();
        for (java.util.Map<String, Object> step : transitions(aggregateType, aggregateId)) {
            path.add((String) step.get("to_status"));
        }
        return path;
    }

    @Test
    @DisplayName("#931 DD 红例（ruling #732）：只放宽 HELD 到 PENDING：EFFECTIVE 与 WITHDRAWN 的命令仍不能改回 PENDING，state_transitions 仍不接受 SeparationHold 的 HELD 到 PENDING")
    void onlyHeldToPendingIsNewlyAllowed() throws Exception {
        UUID effective = transfer(employed(REGULAR), TODAY, newUnit());
        assertThat(commandStatus(effective)).isEqualTo(EFFECTIVE);
        UUID withdrawn = transfer(employed(REGULAR), TODAY.plusDays(5), newUnit());
        commands().withdraw(withdrawn, "synthetic: entered by mistake");
        assertThat(commandStatus(withdrawn)).isEqualTo(WITHDRAWN);

        try (Connection workforce = schemaConnection("organization_workforce")) {
            assertThatThrownBy(() -> update(workforce, "update personnel_commands set status = 'PENDING' where personnel_command_id = ?", effective))
                    .hasMessageContaining("cannot move from EFFECTIVE to PENDING");
            assertThatThrownBy(() -> update(workforce, "update personnel_commands set status = 'PENDING' where personnel_command_id = ?", withdrawn))
                    .hasMessageContaining("cannot move from WITHDRAWN to PENDING");
            assertThatThrownBy(() -> insertTransition(workforce, AGGREGATE_SEPARATION_HOLD, UUID.randomUUID(), 1, HELD, PENDING))
                    .as("a SeparationHold never goes to PENDING; only the PersonnelCommand pair is new").hasMessageContaining("ck_state_transitions_status_of_aggregate");
        }
    }
}
