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
 * #960 (one-design DD on #960, section 2): while a sync run is still running the migration is refused (exit 1) and writes nothing. A class
 * of its own: the running sync run it leaves would refuse every later migration of the same database.
 */
class InitialVersionMigrationRunningSyncIT extends InitialVersionMigrationSupport {

    static final TestDatabase postgres = TestPostgres.database(InitialVersionMigrationRunningSyncIT.class, TestPostgres.MIGRATED)
            .withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
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
