package com.forcitis.one.platform.approval.infrastructure.replay;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExportedIdsTest {

    @Test
    @DisplayName("P3B-L5 a form value 0 is the export's 'nobody' and becomes blank, not the UUID of person or department 0")
    void zeroBecomesBlank() {
        assertThat(ExportedIds.employeeText("0")).isEmpty();
        assertThat(ExportedIds.orgUnitText(" 0 ")).isEmpty();

        assertThat(ExportedIds.employeeText("12")).isEqualTo(ExportedIds.employee(12).toString());
        assertThat(ExportedIds.orgUnitText("-7")).isEqualTo(ExportedIds.orgUnit(-7).toString());
        assertThat(ExportedIds.employeeText("not-a-number")).isEqualTo("not-a-number");
        assertThat(ExportedIds.person(ExportedIds.employee(12))).isEqualTo(12);
    }
}
