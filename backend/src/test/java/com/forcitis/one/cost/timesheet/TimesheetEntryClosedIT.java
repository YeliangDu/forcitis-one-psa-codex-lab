package com.forcitis.one.cost.timesheet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.forcitis.one.platform.approval.ApprovalIntegrationSupport;
import com.forcitis.one.platform.identity.api.Permission;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class TimesheetEntryClosedIT extends ApprovalIntegrationSupport {

    @Test
    @DisplayName("D-1a-107 Phase 1a exposes no timesheet entry: submit, withdraw, cancel and read do not exist over HTTP, whatever permission the caller holds")
    void noTimesheetEndpointIsExposed() throws Exception {
        var holder = user("1").authorities(() -> Permission.Codes.TIMESHEET_SUBMIT, () -> Permission.Codes.TIMESHEET_APPROVE);
        String body = "{\"workPackageId\":1,\"workDate\":\"2026-10-13\",\"hours\":8,\"projectManagerId\":2,\"reason\":\"x\"}";
        List<Integer> statuses = List.of(
                mvc.perform(post("/api/v1/cost/timesheets/1:submit").with(csrf()).with(holder).contentType(MediaType.APPLICATION_JSON).content(body))
                        .andReturn().getResponse().getStatus(),
                mvc.perform(post("/api/v1/cost/timesheets/1:withdraw").with(csrf()).with(holder).contentType(MediaType.APPLICATION_JSON).content(body))
                        .andReturn().getResponse().getStatus(),
                mvc.perform(post("/api/v1/cost/timesheets/1:cancel").with(csrf()).with(holder).contentType(MediaType.APPLICATION_JSON).content(body))
                        .andReturn().getResponse().getStatus(),
                mvc.perform(get("/api/v1/cost/timesheets/1").with(holder)).andReturn().getResponse().getStatus());
        assertThat(statuses).allSatisfy(status -> assertThat(status).isIn(404, 405));
    }
}
