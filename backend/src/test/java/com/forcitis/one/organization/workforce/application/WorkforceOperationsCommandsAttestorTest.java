package com.forcitis.one.organization.workforce.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.organization.workforce.api.EmployeeLabel;
import com.forcitis.one.organization.workforce.api.EmployeeLabels;
import com.forcitis.one.platform.audit.api.AuditPartitions;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.DefaultApplicationArguments;

@DisplayName("WorkforceOperationsCommands: the operator and the witness must be employees with a row; otherwise the command is refused and runs no action")
class WorkforceOperationsCommandsAttestorTest {
    static final String OPERATOR_ID = "01900000-0000-7000-8000-00000000a001";
    static final String WITNESS_ID = "01900000-0000-7000-8000-00000000a002";

    private final MigrateInitialVersions migrate = Mockito.mock(MigrateInitialVersions.class);
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    private int run(Set<String> knownEmployees) {
        EmployeeLabels employees = ids -> {
            Map<EmployeeId, EmployeeLabel> known = new java.util.HashMap<>();
            ids.stream().filter(id -> knownEmployees.contains(id.value().toString())).forEach(id -> known.put(id, new EmployeeLabel("Synthetic", "SYN")));
            return known;
        };
        AuditPartitions partitions = month -> true;
        WorkforceOperationsCommands commands = new WorkforceOperationsCommands(Mockito.mock(ReleaseSeparationHoldByOperations.class), migrate, employees, partitions,
                Clock.fixed(Instant.parse("2026-11-04T00:00:00Z"), ZoneOffset.UTC));
        return commands.run(new DefaultApplicationArguments("--operation=migrate-initial-versions", "--operator=" + OPERATOR_ID, "--witness=" + WITNESS_ID),
                new PrintStream(new ByteArrayOutputStream()), new PrintStream(err));
    }

    @Test
    @DisplayName("a witness without an employees row is refused with exit code 1 and the migration does not run")
    void anUnknownWitnessIsRefused() {
        assertThat(run(Set.of(OPERATOR_ID))).isEqualTo(1);
        assertThat(err.toString()).startsWith("refused: ");
        Mockito.verifyNoInteractions(migrate);
    }

    @Test
    @DisplayName("an operator without an employees row is refused with exit code 1 and the migration does not run")
    void anUnknownOperatorIsRefused() {
        assertThat(run(Set.of(WITNESS_ID))).isEqualTo(1);
        Mockito.verifyNoInteractions(migrate);
    }

    @Test
    @DisplayName("both attestors known: the migration runs and the exit code is 0")
    void bothKnownRuns() {
        assertThat(run(Set.of(OPERATOR_ID, WITNESS_ID))).isZero();
        Mockito.verify(migrate).execute(Mockito.any(), Mockito.eq(false));
    }
}
