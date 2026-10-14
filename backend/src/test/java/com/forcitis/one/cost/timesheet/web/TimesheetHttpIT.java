package com.forcitis.one.cost.timesheet.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.forcitis.one.platform.approval.ApprovalIntegrationSupport;
import com.forcitis.one.platform.approval.application.DecideTasks;
import com.forcitis.one.platform.approval.application.DecisionOutcome;
import com.forcitis.one.platform.approval.application.TaskDecision;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.identity.api.Permission;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** The timesheet's HTTP surface: the answers are Response records and typed error codes, and the caller is always the session user. */
class TimesheetHttpIT extends ApprovalIntegrationSupport {

    static final AtomicLong IDS = new AtomicLong(70_000);
    static final long WORK_PACKAGE = 7_002;

    @Autowired
    DecideTasks decideTasks;

    long employee;
    long manager;

    @BeforeEach
    void people() {
        jdbc.update("insert into delivery_execution.work_packages (id, planned_hours, actual_hours, status) values (?, 400, 10, 'IN_PROGRESS')"
                + " on conflict (id) do nothing", WORK_PACKAGE);
        employee = newId();
        manager = newId();
        organisation.person(employee, 1, null).person(manager, 1, null);
    }

    static RequestPostProcessor submitter(long id) {
        return user(String.valueOf(id)).authorities(() -> Permission.Codes.TIMESHEET_SUBMIT);
    }

    String url(long id, String action) {
        return "/api/v1/cost/timesheets/" + id + action;
    }

    void submit(long id) throws Exception {
        mvc.perform(post(url(id, ":submit")).with(csrf()).with(submitter(employee)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"workPackageId\":" + WORK_PACKAGE + ",\"hours\":8,\"projectManagerId\":" + manager + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("SUBMITTED"));
    }

    @Test
    @DisplayName("RULE-269 reading a submitted timesheet shows status, round, instance and whether it can be withdrawn")
    void getShowsStatusAndWithdrawability() throws Exception {
        long id = IDS.incrementAndGet();
        submit(id);

        mvc.perform(get(url(id, "")).with(submitter(employee))).andExpect(status().isOk()).andExpect(jsonPath("$.timesheetId").value(String.valueOf(id)))
                .andExpect(jsonPath("$.status").value("SUBMITTED")).andExpect(jsonPath("$.round").value(1))
                .andExpect(jsonPath("$.approvalInstanceId").isNotEmpty()).andExpect(jsonPath("$.canWithdraw").value(true));
    }

    @Test
    @DisplayName("RULE-269 withdraw over HTTP: 204, then the timesheet comes back RETURNED; a blank reason is 422 with a typed code")
    void withdrawOverHttp() throws Exception {
        long id = IDS.incrementAndGet();
        submit(id);

        mvc.perform(post(url(id, ":withdraw")).with(csrf()).with(submitter(employee)).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\" \"}"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.errorCode").value("COST_TIMESHEET_REASON_REQUIRED"));
        mvc.perform(post(url(id, ":withdraw")).with(csrf()).with(submitter(employee)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"wrong hours\"}")).andExpect(status().isNoContent());

        eventually(() -> {
            try {
                mvc.perform(get(url(id, "")).with(submitter(employee))).andExpect(jsonPath("$.status").value("RETURNED"))
                        .andExpect(jsonPath("$.canWithdraw").value(false));
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
    }

    @Test
    @DisplayName("RULE-268/279 cancel over HTTP returns the timesheet to a draft; cancelling an approved timesheet is 409")
    void cancelOverHttp() throws Exception {
        long cancelled = IDS.incrementAndGet();
        submit(cancelled);
        mvc.perform(post(url(cancelled, ":cancel")).with(csrf()).with(submitter(employee)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"duplicate\"}")).andExpect(status().isNoContent());
        eventually(() -> {
            try {
                mvc.perform(get(url(cancelled, "")).with(submitter(employee))).andExpect(jsonPath("$.status").value("DRAFT"));
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });

        long approved = IDS.incrementAndGet();
        submit(approved);
        var task = taskService.createTaskQuery().processInstanceBusinessKey("TIMESHEET:" + approved).singleResult();
        decideTasks.execute(new UserId(manager), List.of(new TaskDecision(new com.forcitis.one.platform.approval.api.ApprovalTaskId(task.getId()),
                DecisionOutcome.APPROVE, null)));
        mvc.perform(post(url(approved, ":cancel")).with(csrf()).with(submitter(employee)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"too late\"}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("COST_TIMESHEET_TRANSITION_ILLEGAL"));
    }

    @Test
    @DisplayName("I-5 another person's timesheet is 403 for read, withdraw and cancel; an unknown one is 404 with a typed code")
    void foreignAndUnknownTimesheets() throws Exception {
        long id = IDS.incrementAndGet();
        submit(id);
        long stranger = newId();
        organisation.person(stranger, 1, null);

        mvc.perform(get(url(id, "")).with(submitter(stranger))).andExpect(status().isForbidden());
        mvc.perform(post(url(id, ":withdraw")).with(csrf()).with(submitter(stranger)).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(url(id, ":cancel")).with(csrf()).with(submitter(stranger)).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get(url(987_654_321L, "")).with(submitter(employee))).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("COST_TIMESHEET_NOT_FOUND"));
    }

    @Test
    @DisplayName("every new timesheet route needs the submit permission")
    void permissionIsRequired() throws Exception {
        RequestPostProcessor approverOnly = user(String.valueOf(employee)).authorities(() -> Permission.Codes.TIMESHEET_APPROVE);
        mvc.perform(get(url(1, "")).with(approverOnly)).andExpect(status().isForbidden());
        mvc.perform(post(url(1, ":withdraw")).with(csrf()).with(approverOnly).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(url(1, ":cancel")).with(csrf()).with(approverOnly).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}"))
                .andExpect(status().isForbidden());
    }
}
