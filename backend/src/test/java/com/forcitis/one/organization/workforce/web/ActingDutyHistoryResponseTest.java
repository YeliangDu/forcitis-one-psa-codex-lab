package com.forcitis.one.organization.workforce.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestIds;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.organization.workforce.api.ActingDutyEndCause;
import com.forcitis.one.organization.workforce.api.ActingDutyId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PositionId;
import com.forcitis.one.organization.workforce.application.ActingDutyView;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("#1579 duty responses expose exactly the agreed fields, including nullable keys")
class ActingDutyHistoryResponseTest {
    private final JsonMapper json = JsonMapper.builder().findAndAddModules().build();

    @Test
    void serializesOnlyTheAgreedFieldsWithWireIdsAndDates() {
        var view = new ActingDutyView(new ActingDutyId(TestIds.uuid(1)), new EmployeeId(TestIds.uuid(2)), new OrgUnitId(TestIds.uuid(3)),
                new PositionId(TestIds.uuid(4)), new EmployeeId(TestIds.uuid(5)), "Visible person", LocalDate.of(2026, 11, 6),
                LocalDate.of(2026, 11, 7), ActingDutyView.State.ENDED, ActingDutyEndCause.ENDED_EARLY, "temporary coverage");
        var root = json.valueToTree(ActingDutyHistoryResponse.of(List.of(view)));
        assertThat(root.propertyNames()).containsExactly("items");
        var row = root.path("items").get(0);
        assertThat(row.propertyNames()).containsExactlyInAnyOrder("actingDutyId", "employeeId", "coveredOrgUnitId", "coveredPositionId",
                "coveredEmployeeId", "coveredEmployeeName", "firstDay", "lastDay", "state", "endCause", "reason");
        assertThat(row.path("actingDutyId").asString()).isEqualTo(TestIds.uuid(1).toString());
        assertThat(row.path("employeeId").asString()).isEqualTo(TestIds.uuid(2).toString());
        assertThat(row.path("coveredOrgUnitId").asString()).isEqualTo(TestIds.uuid(3).toString());
        assertThat(row.path("coveredPositionId").asString()).isEqualTo(TestIds.uuid(4).toString());
        assertThat(row.path("coveredEmployeeId").asString()).isEqualTo(TestIds.uuid(5).toString());
        assertThat(row.path("coveredEmployeeName").asString()).isEqualTo("Visible person");
        assertThat(row.path("firstDay").asString()).isEqualTo("2026-11-06");
        assertThat(row.path("lastDay").asString()).isEqualTo("2026-11-07");
        assertThat(row.path("state").asString()).isEqualTo("ENDED");
        assertThat(row.path("endCause").asString()).isEqualTo("ENDED_EARLY");
        assertThat(row.path("reason").asString()).isEqualTo("temporary coverage");
    }

    @Test
    void nullsRemainPresentForTheHeadDutyAndZeroLengthTerm() {
        var view = new ActingDutyView(new ActingDutyId(TestIds.uuid(1)), new EmployeeId(TestIds.uuid(2)), new OrgUnitId(TestIds.uuid(3)),
                null, null, null, LocalDate.of(2026, 11, 6), null, ActingDutyView.State.ENDED, null, "temporary coverage");
        var row = json.valueToTree(ActingDutyHistoryResponse.of(List.of(view))).path("items").get(0);
        for (String field : List.of("coveredPositionId", "coveredEmployeeId", "coveredEmployeeName", "lastDay", "endCause")) {
            assertThat(row.has(field)).as(field).isTrue();
            assertThat(row.get(field).isNull()).as(field).isTrue();
        }
    }
}
