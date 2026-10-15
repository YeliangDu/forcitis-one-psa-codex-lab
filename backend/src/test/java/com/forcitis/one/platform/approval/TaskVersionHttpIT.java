package com.forcitis.one.platform.approval;

import com.forcitis.one.fixture.doubles.TestAccounts;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static com.forcitis.one.platform.approval.OrdinarySessions.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** The task actions are conditional on the version the caller read: no ETag is 428, an old one is 412, the current one goes through. */
class TaskVersionHttpIT extends ApprovalIntegrationSupport {

    static final String BASE = "/api/v1/platform/approval-tasks/";

    record Scene(ApprovalTaskId task, long manager, long colleague) {
    }

    Scene scene() {
        long unit = newId();
        long initiator = newId();
        long manager = newId();
        long colleague = newId();
        organisation.unit(unit, null, null).person(initiator, unit, null).person(manager, unit, null).person(colleague, unit, null);
        ApprovalInstanceId instance = start(newKey("version"), initiator, manager);
        return new Scene(openTaskId(instance), manager, colleague);
    }

    String etag(Scene s) throws Exception {
        return mvc.perform(get(BASE + s.task().value()).with(user(TestAccounts.account(s.manager()).value().toString()))).andExpect(status().isOk())
                .andExpect(header().exists("ETag")).andReturn().getResponse().getHeader("ETag");
    }

    @Test
    @DisplayName("D-1a-179 the detail carries the request's version as ETag, the same value that every change of the approval moves")
    void theDetailCarriesTheVersion() throws Exception {
        Scene s = scene();
        String before = etag(s);

        mvc.perform(post(BASE + s.task().value() + ":claim").with(csrf()).with(user(TestAccounts.account(s.manager()).value().toString())).header("If-Match", before))
                .andExpect(status().isNoContent());

        org.assertj.core.api.Assertions.assertThat(etag(s)).isNotEqualTo(before);
    }

    @Test
    @DisplayName("claim, release, decide, return, transfer and reassign without If-Match are 428 problem details with PLATFORM_WEB_VERSION_REQUIRED, and change nothing")
    void withoutIfMatchIsRefused() throws Exception {
        Scene s = scene();
        for (String action : List.of(":claim", ":release", ":decide", ":return", ":transfer", ":reassign")) {
            mvc.perform(post(BASE + s.task().value() + action).with(csrf()).with(user(TestAccounts.account(s.manager()).value().toString())).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"outcome\":\"APPROVE\",\"reason\":\"r\",\"assigneeId\":\"" + TestAccounts.employee(s.colleague()) + "\"}"))
                    .andExpect(status().isPreconditionRequired()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.errorCode").value("PLATFORM_WEB_VERSION_REQUIRED"));
        }
        org.assertj.core.api.Assertions.assertThat(openTask(taskInstance(s)).getAssignee()).isNull();
    }

    ApprovalInstanceId taskInstance(Scene s) {
        return new ApprovalInstanceId(taskService.createTaskQuery().taskId(s.task().value()).singleResult().getProcessInstanceId());
    }

    @Test
    @DisplayName("an If-Match of an older version is 412 for every action; the current one goes through")
    void aStaleVersionIsRefused() throws Exception {
        Scene s = scene();
        String stale = etag(s);
        mvc.perform(post(BASE + s.task().value() + ":claim").with(csrf()).with(user(TestAccounts.account(s.manager()).value().toString())).header("If-Match", stale))
                .andExpect(status().isNoContent());

        for (String action : List.of(":release", ":decide", ":return", ":transfer")) {
            mvc.perform(post(BASE + s.task().value() + action).with(csrf()).with(user(TestAccounts.account(s.manager()).value().toString())).header("If-Match", stale)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"outcome\":\"APPROVE\",\"reason\":\"r\",\"assigneeId\":\"" + TestAccounts.employee(s.colleague()) + "\"}"))
                    .andExpect(status().isPreconditionFailed()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.errorCode").value("PLATFORM_WEB_VERSION_MISMATCH"));
        }
        mvc.perform(post(BASE + s.task().value() + ":decide").with(csrf()).with(user(TestAccounts.account(s.manager()).value().toString())).header("If-Match", etag(s))
                .contentType(MediaType.APPLICATION_JSON).content("{\"outcome\":\"APPROVE\"}")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("an If-Match that is not one strong ETag (weak, unquoted, a list, not a number) is 400 and changes nothing")
    void aMalformedIfMatchIsRefused() throws Exception {
        Scene s = scene();
        for (String malformed : List.of("W/\"1\"", "1", "\"1\", \"2\"", "\"abc\"", "*")) {
            mvc.perform(post(BASE + s.task().value() + ":claim").with(csrf()).with(user(TestAccounts.account(s.manager()).value().toString())).header("If-Match", malformed))
                    .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.errorCode").value("PLATFORM_WEB_VERSION_MALFORMED"));
        }
        org.assertj.core.api.Assertions.assertThat(openTask(taskInstance(s)).getAssignee()).isNull();
    }

    @Test
    @DisplayName("every operation of the approval controllers declares application/problem+json refusals in the OpenAPI document")
    void everyOperationDeclaresItsRefusals() throws Exception {
        String api = mvc.perform(get("/v3/api-docs").with(user(TestAccounts.account(1).value().toString()))).andReturn().getResponse().getContentAsString();
        tools.jackson.databind.JsonNode paths = tools.jackson.databind.json.JsonMapper.builder().build().readTree(api).path("paths");
        int checked = 0;
        for (var path : paths.properties()) {
            if (!path.getKey().startsWith("/api/v1/platform/approval-task")) {
                continue;
            }
            for (var operation : path.getValue().properties()) {
                checked++;
                org.assertj.core.api.Assertions.assertThat(operation.getValue().path("responses").path("422").path("content").has("application/problem+json"))
                        .as(operation.getKey() + " " + path.getKey()).isTrue();
                if (operation.getKey().equals("post")) {
                    boolean required = false;
                    for (var parameter : operation.getValue().path("parameters")) {
                        required |= "If-Match".equals(parameter.path("name").asString()) && parameter.path("required").asBoolean();
                    }
                    org.assertj.core.api.Assertions.assertThat(required).as("If-Match is required on " + path.getKey()).isTrue();
                }
                org.assertj.core.api.Assertions.assertThat(operation.getValue().path("responses").path("404").path("content").has("application/problem+json")).isTrue();
            }
        }
        org.assertj.core.api.Assertions.assertThat(checked).isGreaterThan(8);
    }
}
