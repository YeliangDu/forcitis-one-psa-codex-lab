package com.forcitis.one.platform.identity.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.organization.workforce.api.EmployeeLabels;
import com.forcitis.one.platform.audit.api.AuditPartitions;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.dao.DataAccessResourceFailureException;

@DisplayName("OperationsCommands: when the organisation snapshot cannot be read the command fails, runs no action and writes no audit entry")
class OperationsCommandsSnapshotUnavailableTest {
    static final String OPERATOR_ID = "01900000-0000-7000-8000-00000000a001";
    static final String WITNESS_ID = "01900000-0000-7000-8000-00000000a002";
    static final String EMPLOYEE_ID = "01900000-0000-7000-8000-00000000a003";

    @Test
    @DisplayName("a DataAccessException from the employee existence check surfaces, so the process ends with a non-zero code; nothing is granted")
    void unavailableSnapshotDoesNotAllowTheCommand() {
        GrantRoleByOperations grant = Mockito.mock(GrantRoleByOperations.class);
        RegisterEmergencyAdmin register = Mockito.mock(RegisterEmergencyAdmin.class);
        ResetEmergencyAdminCredentials reset = Mockito.mock(ResetEmergencyAdminCredentials.class);
        DisableEmergencyAdmin disable = Mockito.mock(DisableEmergencyAdmin.class);
        EnableEmergencyAdmin enable = Mockito.mock(EnableEmergencyAdmin.class);
        RevokeRoleByOperations revoke = Mockito.mock(RevokeRoleByOperations.class);
        EmployeeLabels employees = Mockito.mock(EmployeeLabels.class);
        Mockito.when(employees.labelsOf(Mockito.anySet())).thenThrow(new DataAccessResourceFailureException("snapshot down"));
        AuditPartitions partitions = month -> true;
        OperationsCommands commands = new OperationsCommands(register, reset, disable, enable, grant, revoke, () -> false, employees, partitions,
                Clock.fixed(Instant.parse("2026-10-17T00:00:00Z"), ZoneOffset.UTC));

        assertThatThrownBy(() -> commands.run(new DefaultApplicationArguments("--operation=grant-role", "--employee-id=" + EMPLOYEE_ID,
                "--role-code=business_admin", "--operator=" + OPERATOR_ID, "--witness=" + WITNESS_ID),
                new PrintStream(new ByteArrayOutputStream()), new PrintStream(new ByteArrayOutputStream()))).isInstanceOf(DataAccessResourceFailureException.class);

        Mockito.verifyNoInteractions(grant, register, reset, disable, enable, revoke);
    }
}
