package com.forcitis.one.organization.workforce.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestIds;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("#1579 HTTP personnel commands retain acting-duty inputs")
class EnterPersonnelCommandRequestTest {
    private final JsonMapper json = JsonMapper.builder().findAndAddModules()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    @Test
    void mapsAllSixActingFields() {
        var command = json.readValue("""
                {"commandKind":"ACTING_EXTEND",
                 "actingCoveredOrgUnitId":"%s", "actingCoveredPositionId":"%s",
                 "actingCoveredEmployeeId":"%s", "actingEndsBeforeDate":"2026-11-20",
                 "targetActingDutyId":"%s", "reason":"temporary coverage"}
                """.formatted(TestIds.uuid(1), TestIds.uuid(2), TestIds.uuid(3), TestIds.uuid(4)), EnterPersonnelCommandRequest.class).toCommand();

        assertThat(command.actingCoveredOrgUnitId()).isNotNull();
        assertThat(command.actingCoveredOrgUnitId().value()).isEqualTo(TestIds.uuid(1));
        assertThat(command.actingCoveredPositionId().value()).isEqualTo(TestIds.uuid(2));
        assertThat(command.actingCoveredEmployeeId().value()).isEqualTo(TestIds.uuid(3));
        assertThat(command.actingEndsBeforeDate()).isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(command.targetActingDutyId().value()).isEqualTo(TestIds.uuid(4));
        assertThat(command.reason()).isEqualTo("temporary coverage");
    }

    @Test
    void absentActingFieldsStayNull() {
        var command = json.readValue("{\"commandKind\":\"TRANSFER\"}", EnterPersonnelCommandRequest.class).toCommand();
        assertThat(command.actingCoveredOrgUnitId()).isNull();
        assertThat(command.actingCoveredPositionId()).isNull();
        assertThat(command.actingCoveredEmployeeId()).isNull();
        assertThat(command.actingEndsBeforeDate()).isNull();
        assertThat(command.targetActingDutyId()).isNull();
        assertThat(command.reason()).isNull();
    }
}
