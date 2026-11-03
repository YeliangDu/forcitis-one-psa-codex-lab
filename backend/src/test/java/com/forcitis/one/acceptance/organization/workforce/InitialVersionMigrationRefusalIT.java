package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.*;
import static com.forcitis.one.acceptance.organization.workforce.SeparationHoldOperationNames.REFUSED;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #960 (one-design DD on #960, section 2): the migration is refused before writing anything when the witness is missing or is the operator,
 * as the other workforce operations commands refuse it (exit 1). No sync run is left running here (and after #934 there is no sync at
 * all), so nothing else can explain a refusal.
 */
class InitialVersionMigrationRefusalIT extends InitialVersionMigrationSupport {

    static final TestDatabase postgres = TestPostgres.database(InitialVersionMigrationRefusalIT.class, TestPostgres.MIGRATED)
            .withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Test
    @DisplayName("#960 数据设计第 2 节（与运维命令同形）：缺见证人、见证人与操作人同一人都以 1 拒绝，都不写任何版本行、不写审计")
    void theWitnessIsRequiredAndMustBeSomeoneElse() throws Exception {
        if (oldSyncTablesExist()) {
            assertThat(rows("select count(*) as n from sync_runs where status = 'RUNNING'").get(0).get("n")).as("no running sync").isEqualTo(0L);
        }
        UUID person = snapshotPerson(false, null);
        UUID operator = employed(REGULAR);
        long rows = versionRowCount();
        int audited = auditCount(AUDIT_MIGRATED);

        Result noWitness = migrate(operator, null);
        Result sameWitness = migrate(operator, operator);

        assertThat(noWitness.exitCode()).as(noWitness.text()).isEqualTo(REFUSED);
        assertThat(sameWitness.exitCode()).as(sameWitness.text()).isEqualTo(REFUSED);
        assertThat(versionRowCount()).isEqualTo(rows);
        assertThat(currentVersions(EMPLOYEE_VERSIONS, person)).isEmpty();
        assertThat(auditCount(AUDIT_MIGRATED)).isEqualTo(audited);
    }

    @Test
    @DisplayName("#960 运维命令形状：操作人或见证人不是员工时拒绝，不写任何版本行")
    void aWitnessWhoIsNoEmployeeIsRefused() throws Exception {
        UUID person = snapshotPerson(false, null);
        UUID operator = employed(REGULAR);
        long rows = versionRowCount();
        Result result = migrate(operator, UUID.fromString("01900000-0000-7000-8000-0000000fffff"));
        assertThat(result.exitCode()).as(result.text()).isEqualTo(REFUSED);
        assertThat(versionRowCount()).isEqualTo(rows);
        assertThat(currentVersions(EMPLOYEE_VERSIONS, person)).isEmpty();
    }
}
