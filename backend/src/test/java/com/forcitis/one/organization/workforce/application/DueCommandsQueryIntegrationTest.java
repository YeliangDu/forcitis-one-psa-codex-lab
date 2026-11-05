package com.forcitis.one.organization.workforce.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.domain.PersonnelCommandRepository;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** #933: the due-date job takes the pending commands whose day has come: ordinary ones and separations with a released hold; a never-held separation waits for #967, and held, withdrawn and done ones are not pending. */
class DueCommandsQueryIntegrationTest extends WorkforceIntegrationSupport {
    static final String USER = "'019a0000-0000-7000-8000-0000000000aa'";
    static final LocalDate DAY = LocalDate.of(2026, 11, 3);

    @Autowired
    PersonnelCommandRepository commands;

    @BeforeEach
    void clean() {
        VersionTables.truncate(jdbc, "truncate table organization_workforce.state_transitions");
    }

    private UUID pendingCommand(int n, String kind, LocalDate effective, String status, String holdStatus) {
        String employee = "'019a0000-0000-7000-8000-00000000010" + n + "'";
        String command = "'019a0000-0000-7000-8000-00000000020" + n + "'";
        jdbc.sql("insert into organization_workforce.employees (id, employee_code, display_name, employment_status) values (" + employee + ", 'D-" + n + "', 'name', 'EMPLOYED')").update();
        EmploymentRelationshipSeed.seed(jdbc, employee);
        String withdrawn = status.equals("WITHDRAWN") ? "now(), " + USER + ", 'synthetic'" : "null, null, null";
        jdbc.sql("insert into organization_workforce.personnel_commands (personnel_command_id, employment_relationship_id, employee_id, command_kind, effective_date, status, created_at, created_by, updated_at,"
                + " applied_at, withdrawn_at, withdrawn_by, withdrawal_reason) values (" + command + ", " + EmploymentRelationshipSeed.ref(employee) + ", " + employee + ", '" + kind + "', date '" + effective + "', '" + status
                + "', now(), " + USER + ", now(), " + (status.equals("EFFECTIVE") ? "now()" : "null") + ", " + withdrawn + ")").update();
        if (holdStatus != null) {
            String released = holdStatus.equals("RELEASED") ? "now(), " + USER + ", 'synthetic', 'INTERFACE'" : "null, null, null, null";
            jdbc.sql("insert into organization_workforce.separation_holds (separation_hold_id, personnel_command_id, employee_id, status, released_at, released_by, release_reason,"
                    + " release_channel_kind, created_at, updated_at) values ('019a0000-0000-7000-8000-00000000030" + n + "', " + command + ", " + employee + ", '" + holdStatus
                    + "', " + released + ", now(), now())").update();
        }
        return UUID.fromString("019a0000-0000-7000-8000-00000000020" + n);
    }

    @Test
    @DisplayName("#933 every pending command whose day has come is due, a separation never held included (it is judged on its day, #967); held, withdrawn and effective ones, and later days, are not")
    void everyPendingCommandIsDue() {
        UUID released = pendingCommand(1, "SEPARATE", DAY, "PENDING", "RELEASED");
        UUID ordinary = pendingCommand(2, "REGULARIZE", DAY.minusDays(2), "PENDING", null);
        pendingCommand(3, "SEPARATE", DAY.plusDays(1), "PENDING", "RELEASED");
        pendingCommand(4, "SEPARATE", DAY, "HELD", "HELD");
        pendingCommand(5, "REGULARIZE", DAY, "WITHDRAWN", null);
        pendingCommand(6, "REGULARIZE", DAY, "EFFECTIVE", null);
        UUID unheld = pendingCommand(7, "SEPARATE", DAY, "PENDING", null);

        assertThat(commands.pendingDue(DAY)).containsExactly(new PersonnelCommandId(ordinary), new PersonnelCommandId(released), new PersonnelCommandId(unheld));
        assertThat(commands.pendingDue(DAY.plusDays(1))).hasSize(4);
    }
}
