package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.*;
import static com.forcitis.one.acceptance.organization.workforce.SeparationHoldOperationNames.REFUSED;
import static com.forcitis.one.acceptance.organization.workforce.SeparationHoldOperationNames.WRONG_USAGE;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #960 (one-design DD on #960, section 2): the migration is refused before writing anything when the operator or the witness is missing or
 * the same person, or while a sync run is still running (the snapshot may still change under it). A class of its own: the running sync
 * run it leaves would refuse every later migration of the same database.
 */
class InitialVersionMigrationRefusalIT extends InitialVersionMigrationSupport {

    static final TestDatabase postgres = TestPostgres.database(InitialVersionMigrationRefusalIT.class, TestPostgres.MIGRATED)
            .withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Test
    @DisplayName("#960 数据设计第 2 节（与运维命令同形）：缺见证人以 2 拒绝、见证人与操作人同一人被拒，都不写任何版本行、不写审计")
    void theWitnessIsRequiredAndMustBeSomeoneElse() throws Exception {
        UUID person = snapshotPerson(false, null);
        UUID operator = employed(REGULAR);
        long rows = versionRowCount();
        int audited = auditCount(AUDIT_MIGRATED);

        Result noWitness = migrate(operator, null);
        Result sameWitness = migrate(operator, operator);

        assertThat(noWitness.exitCode()).as(noWitness.text()).isEqualTo(WRONG_USAGE);
        assertThat(sameWitness.exitCode()).as(sameWitness.text()).isNotZero();
        assertThat(versionRowCount()).isEqualTo(rows);
        assertThat(currentVersions(EMPLOYEE_VERSIONS, person)).isEmpty();
        assertThat(auditCount(AUDIT_MIGRATED)).isEqualTo(audited);
    }

    @Test
    @DisplayName("#960 数据设计第 2 节（运行时机）：有进行中的同步时以 1 拒绝，不写任何版本行、不写审计")
    void aRunningSyncRefusesTheMigration() throws Exception {
        UUID person = snapshotPerson(false, null);
        syncRun("RUNNING", clock.instant().minusSeconds(60), null);
        UUID operator = employed(REGULAR);
        UUID witness = employed(REGULAR);
        long rows = versionRowCount();
        int audited = auditCount(AUDIT_MIGRATED);

        Result result = migrate(operator, witness);

        assertThat(result.exitCode()).as(result.text()).isEqualTo(REFUSED);
        assertThat(versionRowCount()).isEqualTo(rows);
        assertThat(currentVersions(EMPLOYEE_VERSIONS, person)).isEmpty();
        assertThat(auditCount(AUDIT_MIGRATED)).isEqualTo(audited);
    }
}
