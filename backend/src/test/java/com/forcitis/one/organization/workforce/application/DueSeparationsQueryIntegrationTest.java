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

/** #931: the due-date job takes only separations whose hold was released; any other pending command is none of its business (card #933). */
class DueSeparationsQueryIntegrationTest extends WorkforceIntegrationSupport {
    static final String USER = "'019a0000-0000-7000-8000-0000000000aa'";
    static final LocalDate DAY = LocalDate.of(2026, 11, 3);

    @Autowired
    PersonnelCommandRepository commands;

    @BeforeEach
    void clean() {
        VersionTables.truncate(jdbc, "truncate table organization_workforce.state_transitions");
    }

    private UUID pendingSeparation(int n, LocalDate effective, String holdStatus) {
        String employee = "'019a0000-0000-7000-8000-00000000010" + n + "'";
        String command = "'019a0000-0000-7000-8000-00000000020" + n + "'";
        jdbc.sql("insert into organization_workforce.employees (id, employee_code, display_name, employment_status) values (" + employee + ", 'D-" + n + "', 'name', 'EMPLOYED')").update();
        jdbc.sql("insert into organization_workforce.personnel_commands (personnel_command_id, employee_id, command_kind, effective_date, status, created_at, created_by, updated_at)"
                + " values (" + command + ", " + employee + ", 'SEPARATE', date '" + effective + "', 'PENDING', now(), " + USER + ", now())").update();
        if (holdStatus != null) {
            jdbc.sql("insert into organization_workforce.separation_holds (separation_hold_id, personnel_command_id, employee_id, status, released_at, released_by, release_reason,"
                    + " release_channel_kind, created_at, updated_at) values ('019a0000-0000-7000-8000-00000000030" + n + "', " + command + ", " + employee + ", '" + holdStatus
                    + "', now(), " + USER + ", 'synthetic', 'INTERFACE', now(), now())").update();
        }
        return UUID.fromString("019a0000-0000-7000-8000-00000000020" + n);
    }

    @Test
    @DisplayName("#931 only a pending separation with a released hold and a day that has come is due")
    void onlyReleasedSeparationsAreDue() {
        UUID released = pendingSeparation(1, DAY, "RELEASED");
        pendingSeparation(2, DAY, null);
        pendingSeparation(3, DAY.plusDays(1), "RELEASED");

        assertThat(commands.releasedSeparationsDue(DAY)).containsExactly(new PersonnelCommandId(released));
        assertThat(commands.releasedSeparationsDue(DAY.plusDays(1))).hasSize(2);
    }
}
