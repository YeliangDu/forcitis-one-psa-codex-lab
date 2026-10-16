package com.forcitis.one.cost.timesheet.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.cost.timesheet.api.TimesheetId;
import com.forcitis.one.cost.timesheet.api.TimesheetStatus;
import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.TestIds;
import com.forcitis.one.fixture.doubles.TestOrgIds;
import com.forcitis.one.kernel.Hours;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.event.AggregateReference;
import com.forcitis.one.platform.numbering.api.DocumentNumber;
import com.forcitis.one.platform.numbering.api.NumberingRuleVersionId;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TimesheetRuleVersionTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 14);

    private static Timesheet draftedWith(NumberingRuleVersionId ruleVersionId) {
        return Timesheet.draft(new TimesheetId(TestIds.uuid(7)), new DocumentNumber("TS-ABC-202610-0007"),
                new AggregateReference("NumberingCounter", TestIds.uuid(11)), ruleVersionId, TestAccounts.employee(30),
                new LegalEntityId(TestIds.uuid(1)), TestOrgIds.orgUnit(5), TODAY.minusDays(1), Hours.of("8"), TestAccounts.employee(40), TODAY);
    }

    @Test
    @DisplayName("TS-UNIQ 新建的工时单带着发号所用的编号规则版本 ID；缺失即失败，不写空值")
    void aNewTimesheetNeedsTheRuleVersionOfItsNumber() {
        NumberingRuleVersionId version = new NumberingRuleVersionId(TestIds.uuid(12));

        assertThat(draftedWith(version).numberingRuleVersionId()).contains(version);
        assertThatThrownBy(() -> draftedWith(null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("TS-UNIQ 迁入的工时单没有编号规则版本 ID，仍可还原")
    void aMigratedTimesheetHasNoRuleVersion() {
        Timesheet migrated = Timesheet.restore(new TimesheetId(TestIds.uuid(7)), new DocumentNumber("OLD-1"), null, TestAccounts.employee(30),
                new LegalEntityId(TestIds.uuid(1)), TestOrgIds.orgUnit(5), TODAY, Hours.of("8"), TestAccounts.employee(40),
                TimesheetStatus.APPROVED, 1, TODAY, 3, 2);

        assertThat(migrated.numberingRuleVersionId()).isEmpty();
    }
}
