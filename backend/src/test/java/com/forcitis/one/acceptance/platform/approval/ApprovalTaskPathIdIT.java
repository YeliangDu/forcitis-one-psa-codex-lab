package com.forcitis.one.acceptance.platform.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.ApprovalP4Support;
import com.forcitis.one.platform.approval.api.ApprovalError;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * R-1b P4 G-A1 (acceptance item 10): the task id in a path is a lower-case canonical UUID. Anything else (the old text and number
 * forms, an upper-case UUID) is a malformed request, 400, in all seven task operations, and is not answered as "no such task" (404).
 * A well-formed id of no task still is 404, and a real task is still found. The write operations get a valid If-Match and body, so that
 * the path is the only thing wrong with them.
 */
class ApprovalTaskPathIdIT extends ApprovalP4Support {

    static final TestDatabase postgres = TestPostgres.database(ApprovalTaskPathIdIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private MockHttpServletResponse write(String taskId, String operation) throws Exception {
        String body = switch (operation) {
            case "decide" -> "{\"outcome\":\"APPROVE\",\"reason\":\"synthetic\"}";
            case "return" -> "{\"reason\":\"synthetic\"}";
            case "transfer", "reassign" -> "{\"assigneeId\":\"" + text(secondProjectManager) + "\",\"reason\":\"synthetic\"}";
            default -> null;
        };
        var request = post(TASKS + taskId + ":" + operation).header("If-Match", "\"1\"").with(csrf()).with(session(projectManagerAccount));
        if (body != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(request).andReturn().getResponse();
    }

    @Test
    @DisplayName("R-1b P4 G-A1：非 uuid 的任务 ID 路径参数返回 400，不当作「找不到任务」（读取与六个写操作；旧的文本、整数形态与大写 uuid 都算）")
    void aTaskIdThatIsNoCanonicalUuidIsAMalformedRequest() throws Exception {
        List<String> malformed = List.of("not-a-uuid", "42", "approval-task-1", UUID.randomUUID().toString().toUpperCase(java.util.Locale.ROOT),
                "0190-0000-7000-8000-000000000001");
        for (String taskId : malformed) {
            MockHttpServletResponse read = mvc.perform(get(TASKS + taskId).with(session(projectManagerAccount))).andReturn().getResponse();
            assertThat(read.getStatus()).as("GET %s: %s", taskId, read.getContentAsString()).isEqualTo(400);
            assertThat(read.getContentAsString()).doesNotContain(ApprovalError.PLATFORM_APPROVAL_TASK_NOT_FOUND.name());
            for (String operation : List.of("claim", "release", "decide", "return", "transfer", "reassign")) {
                MockHttpServletResponse written = write(taskId, operation);
                assertThat(written.getStatus()).as("%s of %s: %s", operation, taskId, written.getContentAsString()).isEqualTo(400);
                assertThat(written.getContentAsString()).doesNotContain(ApprovalError.PLATFORM_APPROVAL_TASK_NOT_FOUND.name());
            }
        }
    }

    @Test
    @DisplayName("R-1b P4 G-A1（对照）：规范 uuid 形态但没有这个任务仍答 404 PLATFORM_APPROVAL_TASK_NOT_FOUND；真实任务照常读到")
    void aWellFormedIdIsLookedUp() throws Exception {
        MockHttpServletResponse unknown = mvc.perform(get(TASKS + UUID.randomUUID()).with(session(projectManagerAccount))).andReturn().getResponse();

        assertThat(unknown.getStatus()).isEqualTo(404);
        assertThat((String) JsonPath.read(unknown.getContentAsString(), "$.errorCode")).isEqualTo("PLATFORM_APPROVAL_TASK_NOT_FOUND");

        UUID task = openTaskIds(startProjectHours(projectManager)).getFirst();
        MockHttpServletResponse found = detail(task, projectManagerAccount);
        assertThat(found.getStatus()).as(found.getContentAsString()).isEqualTo(200);
        assertThat((String) JsonPath.read(found.getContentAsString(), "$.taskId")).isEqualTo(task.toString());
        assertThat((String) JsonPath.read(found.getContentAsString(), "$.instanceId")).matches(CANONICAL_UUID);
        assertThat((String) JsonPath.read(found.getContentAsString(), "$.documentId")).matches(CANONICAL_UUID);
    }
}
