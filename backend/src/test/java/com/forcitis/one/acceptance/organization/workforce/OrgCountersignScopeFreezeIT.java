package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.OrgCountersignNames.*;
import static com.forcitis.one.acceptance.organization.workforce.OrgStructureCommandNames.SET_ATTRIBUTES;
import static com.forcitis.one.acceptance.organization.workforce.OrgStructureCommandNames.TARGET_NAME;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.PENDING;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.REGULAR;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #910 acceptance part (#909 F4a), note section 5 rule 3 (user decision 2026-10-30 「推荐」 on later-dated commands, applied to org commands;
 * review rv903a M1): while an org command awaits countersign, its scope (the target unit and every unit below it on the effective date, for
 * MERGE also the receiving unit, and the employees in its basis) refuses later-dated org commands and personnel commands; earlier-dated
 * ones are allowed; the freeze ends when the command is countersigned or withdrawn.
 */
class OrgCountersignScopeFreezeIT extends OrgCountersignSupport {

    static final TestDatabase postgres = TestPostgres.database(OrgCountersignScopeFreezeIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private static final LocalDate DAY = TODAY.plusDays(10);

    private UUID unitX;
    private UUID child;
    private UUID basisPerson;
    private UUID frozen;

    private void awaitingOnX() throws Exception {
        unitX = seedUnit(topE1);
        child = seedUnit(unitX);
        basisPerson = inheritingIn(child);
        frozen = assignLegalEntity(unitX, DAY, e2);
        assertThat(statusOf(frozen)).isEqualTo(AWAITING);
    }

    @Test
    @DisplayName("#910 note §5 第 3 条（组织命令）：待会签期间，生效日更晚、目标在范围内（目标单元或其下级）的组织命令被拒；调整上级时新上级在范围内也被拒；不留命令行")
    void laterDatedOrgCommandsInTheScopeAreRefused() throws Exception {
        awaitingOnX();
        UUID outsider = seedUnit(topE1);
        long commands = orgCommandCount();

        assertRefused(() -> unitCommand(SET_ATTRIBUTES, child, DAY.plusDays(1), TARGET_NAME, "Later rename below"));
        assertRefused(() -> unitCommand(SET_ATTRIBUTES, unitX, DAY.plusDays(1), TARGET_NAME, "Later rename of the target"));
        assertRefused(() -> reparent(outsider, DAY.plusDays(2), child));
        assertRefused(() -> reparent(child, DAY.plusDays(2), topE3));

        assertThat(orgCommandCount()).isEqualTo(commands);
    }

    @Test
    @DisplayName("#910 note §5 第 3 条（人事命令）：待会签期间，依据里员工的更晚生效人事命令被拒；把范围外的人更晚调入范围内单元也被拒；不留命令行")
    void laterDatedPersonnelCommandsInTheScopeAreRefused() throws Exception {
        awaitingOnX();
        UUID outsider = inheritingIn(topE1);
        actInSpecialist();
        long basisCommands = commandCount(basisPerson);
        long outsiderCommands = commandCount(outsider);

        assertRefused(() -> transfer(basisPerson, DAY.plusDays(3), topE1));
        assertRefused(() -> transfer(outsider, DAY.plusDays(3), child));

        assertThat(commandCount(basisPerson)).isEqualTo(basisCommands);
        assertThat(commandCount(outsider)).isEqualTo(outsiderCommands);
    }

    @Test
    @DisplayName("#910 note §5 第 3 条「更早生效的命令允许」：待会签期间，生效日早于它的组织命令与人事命令照常录入；范围外的更晚命令也照常录入")
    void earlierDatedAndOutOfScopeCommandsAreAllowed() throws Exception {
        awaitingOnX();
        UUID outsideUnit = seedUnit(topE1);
        UUID outsidePerson = employedIn(outsideUnit, REGULAR, true);

        assertThat(unitCommand(SET_ATTRIBUTES, child, DAY.minusDays(1), TARGET_NAME, "Earlier rename")).isNotNull();
        assertThat(unitCommand(SET_ATTRIBUTES, outsideUnit, DAY.plusDays(1), TARGET_NAME, "Later rename outside")).isNotNull();
        actInSpecialist();
        assertThat(transfer(outsidePerson, DAY.plusDays(2), topE1)).isNotNull();
    }

    @Test
    @DisplayName("#910 note §5 第 3 条（合并）：待会签的合并把承接单元也冻结：更晚生效、目标为承接单元的组织命令被拒；会签完成后冻结解除，同一命令可以录入")
    void aMergeFreezesTheReceivingUnitUntilItIsCountersigned() throws Exception {
        UUID merged = seedUnit(topE1);
        UUID receiving = seedUnit(topE2);
        inheritingIn(merged);
        UUID waiting = merge(merged, receiving, DAY);
        assertThat(statusOf(waiting)).isEqualTo(AWAITING);

        assertRefused(() -> unitCommand(SET_ATTRIBUTES, receiving, DAY.plusDays(1), TARGET_NAME, "Later rename of the receiver"));

        approve(headE1, waiting, KIND_CHANGE, e1);
        approve(headE2, waiting, KIND_CHANGE, e2);
        assertThat(statusOf(waiting)).isEqualTo(PENDING);
        assertThat(unitCommand(SET_ATTRIBUTES, receiving, DAY.plusDays(1), TARGET_NAME, "Later rename of the receiver")).isNotNull();
    }

    @Test
    @DisplayName("#910 note §5 第 3 条：起草人撤回待会签命令后冻结解除，之前被拒的更晚人事命令可以录入")
    void withdrawingEndsTheFreeze() throws Exception {
        awaitingOnX();
        actInSpecialist();
        assertRefused(() -> transfer(basisPerson, DAY.plusDays(3), topE1));
        actIn(orgAdmin);

        orgCommands().withdraw(frozen, "synthetic: not now");

        actInSpecialist();
        assertThat(transfer(basisPerson, DAY.plusDays(3), topE1)).isNotNull();
    }
}
