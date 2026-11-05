package com.forcitis.one.organization.workforce.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.organization.workforce.domain.InitialVersionMigrationRepository;
import com.forcitis.one.organization.workforce.domain.InitialVersionPlan;
import com.forcitis.one.platform.audit.api.AuditLog;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@DisplayName("#1372 the initial-version run skips and counts people without an attribution; a dry run reports the same count and writes nothing")
class MigrateInitialVersionsTest {

    private final InitialVersionMigrationRepository repository = Mockito.mock(InitialVersionMigrationRepository.class);
    private final AuditLog audit = Mockito.mock(AuditLog.class);
    private final UUID attributed = UUID.fromString("019a0000-0000-7000-8000-0000000000a1");
    private final UUID unattributed = UUID.fromString("019a0000-0000-7000-8000-0000000000a2");
    private final MigrateInitialVersions migrate = new MigrateInitialVersions(repository, audit,
            Clock.fixed(Instant.parse("2026-11-05T02:00:00Z"), ZoneId.of("Asia/Shanghai")), new BusinessZone(ZoneId.of("Asia/Shanghai")));

    private void twoPeople() {
        Mockito.when(repository.lockEmployeesWithoutVersions()).thenReturn(List.of(
                new InitialVersionPlan.SnapshotPerson(attributed, true, false, null, null, false),
                new InitialVersionPlan.SnapshotPerson(unattributed, true, false, null, null, false)));
        Mockito.when(repository.hasEmployerOn(Mockito.eq(new EmployeeId(attributed)), Mockito.any())).thenReturn(true);
        Mockito.when(repository.hasEmployerOn(Mockito.eq(new EmployeeId(unattributed)), Mockito.any())).thenReturn(false);
    }

    @Test
    @DisplayName("a real run writes the attributed person, skips and counts the other, and does not throw")
    void realRunSkipsAndCounts() {
        twoPeople();
        String summary = migrate.execute(new WorkforceOperationAttestation(new EmployeeId(UUID.randomUUID()), new EmployeeId(UUID.randomUUID())), false);
        assertThat(summary).contains("employedMigrated=1").contains("skippedWithoutAttribution=1");
        Mockito.verify(repository).insertEmployeeVersion(Mockito.eq(new EmployeeId(attributed)), Mockito.any(), Mockito.any());
        Mockito.verify(repository, Mockito.never()).insertEmployeeVersion(Mockito.eq(new EmployeeId(unattributed)), Mockito.any(), Mockito.any());
        Mockito.verify(audit).record(Mockito.any());
    }

    @Test
    @DisplayName("a dry run reports the same counts and writes neither a version nor an audit entry")
    void dryRunWritesNothing() {
        twoPeople();
        String summary = migrate.execute(new WorkforceOperationAttestation(new EmployeeId(UUID.randomUUID()), new EmployeeId(UUID.randomUUID())), true);
        assertThat(summary).startsWith("dry run, nothing written").contains("employedMigrated=1").contains("skippedWithoutAttribution=1");
        Mockito.verify(repository, Mockito.never()).insertEmployeeVersion(Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.verifyNoInteractions(audit);
    }
}
