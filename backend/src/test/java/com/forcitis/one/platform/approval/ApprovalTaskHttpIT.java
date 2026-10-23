package com.forcitis.one.platform.approval;

import com.forcitis.one.fixture.doubles.TestAccounts;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static com.forcitis.one.platform.approval.OrdinarySessions.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.identity.api.Permission;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Paths pass G41, permissions G43, bodies carry no identity G33. */
class ApprovalTaskHttpIT extends ApprovalIntegrationSupport {

    static RequestPostProcessor as(long id, String... permissions) {
        return user(TestAccounts.account(id).value().toString()).authorities(com.forcitis.one.platform.approval.OrdinarySessions.with(permissions));
    }

    static final String BASE = "/api/v1/platform/approval-tasks";

    @Test
    @DisplayName("inbox -> claim -> detail -> decide over HTTP: status codes, JSON shape and the next task's candidates")
    void claimAndDecideOverHttp() throws Exception {
        long initiator = newId();
        long manager = newId();
        long head = newId();
        long unit = newId();
        organisation.unit(unit, null, head).person(head, unit, null).person(initiator, unit, null).person(manager, unit, null);
        ApprovalInstanceId instance = startWithSupervisor(newKey("http"), initiator, manager);
        String task = openTaskId(instance, "projectManagerReview").value().toString();
        RequestPostProcessor approve = as(manager, Permission.Codes.PLATFORM_APPROVAL_TASK_APPROVE, Permission.Codes.PLATFORM_APPROVAL_TASK_READ);

        mvc.perform(get(BASE).with(approve)).andExpect(status().isOk()).andExpect(jsonPath("$.newCount").value(1))
                .andExpect(jsonPath("$.items[0].source").doesNotExist()).andExpect(jsonPath("$.items[0].taskId").value(task))
                .andExpect(jsonPath("$.items[0].claimed").value(false));
        mvc.perform(post(BASE + "/" + task + ":claim").with(csrf()).with(currentEtag()).with(approve)).andExpect(status().isNoContent());
        mvc.perform(get(BASE + "/" + task).with(approve)).andExpect(status().isOk()).andExpect(jsonPath("$.assignee").value(TestAccounts.account(manager).value().toString()))
                .andExpect(jsonPath("$.nodeKey").value("projectManagerReview")).andExpect(jsonPath("$.canAct").value(true));
        mvc.perform(post(BASE + "/" + task + ":claim").with(csrf()).with(currentEtag()).with(approve)).andExpect(status().isNoContent());
        mvc.perform(post(BASE + "/" + task + ":decide").with(csrf()).with(currentEtag()).with(approve).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"APPROVE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.instanceId").value(instance.value().toString()));
        mvc.perform(get(BASE).with(as(head, Permission.Codes.PLATFORM_APPROVAL_TASK_READ))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].nodeName").value("一级汇报人审批"));
    }

    @Test
    @DisplayName("RULE-272/275 business refusals are 4xx with a stable error code: a reject / return without reason, a closed task")
    void refusalsCarryErrorCodes() throws Exception {
        long initiator = newId();
        long manager = newId();
        organisation.person(initiator, 1, null).person(manager, 1, null);
        ApprovalInstanceId instance = start(newKey("http"), initiator, manager);
        String task = openTaskId(instance).value().toString();
        RequestPostProcessor approve = as(manager, Permission.Codes.PLATFORM_APPROVAL_TASK_APPROVE);

        mvc.perform(post(BASE + "/" + task + ":decide").with(csrf()).with(currentEtag()).with(approve).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"REJECT\",\"reason\":\"\"}"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.errorCode").value("KERNEL_REASON_REQUIRED"));
        mvc.perform(post(BASE + "/" + task + ":return").with(csrf()).with(currentEtag()).with(approve).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"  \"}"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.errorCode").value("KERNEL_REASON_REQUIRED"));
        mvc.perform(post(BASE + "/" + task + ":return").with(csrf()).with(currentEtag()).with(approve).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"incomplete\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RETURNED"));
        mvc.perform(post(BASE + "/" + task + ":decide").with(csrf()).with(currentEtag()).with(approve).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"APPROVE\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.errorCode").value("PLATFORM_APPROVAL_TASK_NOT_FOUND"));
    }

    @Test
    @DisplayName("anonymous callers get 401 and a write without CSRF is refused")
    void anonymousAndCsrf() throws Exception {
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        mvc.perform(post(BASE + "/x:claim").with(as(1, Permission.Codes.PLATFORM_APPROVAL_TASK_APPROVE))).andExpect(status().isForbidden());
    }
}
